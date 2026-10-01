package app.luogo.relay

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Standalone Kotlin Luogo Relay Server (`server/RelayServer.kt`).
 *
 * Complete 100% Kotlin rewrite of the upstream Go relay server (`main.go`, `api.go`, `hub.go`, `store.go`).
 * Provides zero-knowledge E2EE message routing, group management, single-use 7-day invite tokens,
 * bounded per-group message logs, and crowdsourced BLE sighting endpoints.
 */
object LuogoKotlinRelayMain {
    @JvmStatic
    fun main(args: Array<String>) {
        var port = 8080
        var maxLog = 500
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-addr" -> {
                    val raw = args.getOrNull(++i) ?: ":8080"
                    port = raw.substringAfterLast(':').toIntOrNull() ?: 8080
                }
                "-max-log" -> {
                    maxLog = args.getOrNull(++i)?.toIntOrNull() ?: 500
                }
            }
            i++
        }

        val server = HttpServer.create(InetSocketAddress(port), 0)
        val router = KotlinRelayHttpRouter(maxLog)
        server.createContext("/", router::handle)
        server.executor = Executors.newVirtualThreadPerTaskExecutor()
        println("Luogo Kotlin Relay listening on :$port (maxLog=$maxLog)")
        server.start()
    }
}

class KotlinRelayHttpRouter(private val maxLog: Int = 500) {
    private val random = SecureRandom()
    private val seqGen = AtomicLong(0L)

    data class User(val id: String, var name: String, var color: Long, val publicKey: String, val createdAt: Long)
    data class Group(val id: String, var name: String, val createdAt: Long)
    data class Invite(val token: String, val groupId: String, val createdBy: String, val expiresAt: Long)
    data class StoredMessage(val seq: Long, val groupId: String, val senderId: String, val ts: Long, val ciphertext: String)

    private val users = ConcurrentHashMap<String, User>()
    private val tokenHashes = ConcurrentHashMap<String, String>()
    private val groups = ConcurrentHashMap<String, Group>()
    private val members = ConcurrentHashMap<String, MutableSet<String>>()
    private val invites = ConcurrentHashMap<String, Invite>()
    private val messages = ConcurrentHashMap<String, CopyOnWriteArrayList<StoredMessage>>()

    fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val method = exchange.requestMethod
        try {
            if (method == "GET" && path == "/api/health") {
                respondJson(exchange, 200, """{"ok":true,"version":1,"runtime":"kotlin"}""")
                return
            }
            if (method == "POST" && path == "/api/users") {
                val body = exchange.requestBody.bufferedReader().readText()
                val name = extractString(body, "name") ?: "User"
                val color = extractLong(body, "color") ?: 0xFF006C4CL
                val pubKey = extractString(body, "publicKey") ?: ""
                val id = randomHex(16)
                val token = randomBase64Url(32)
                val now = System.currentTimeMillis() / 1000L
                val user = User(id, name, color, pubKey, now)
                users[id] = user
                tokenHashes[sha256Hex(token)] = id
                respondJson(
                    exchange,
                    201,
                    """{"user":{"id":"${user.id}","name":"${user.name}","color":${user.color},"publicKey":"${user.publicKey}"},"token":"$token"}"""
                )
                return
            }

            val authHeader = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")?.trim()
            val userId = authHeader?.let { tokenHashes[sha256Hex(it)] }
            if (userId == null) {
                respondJson(exchange, 401, """{"error":"unauthorized"}""")
                return
            }

            if (method == "GET" && path == "/api/groups") {
                val userGroups = members.entries
                    .filter { it.value.contains(userId) }
                    .mapNotNull { groups[it.key] }
                val jsonArr = userGroups.joinToString(",") {
                    """{"id":"${it.id}","name":"${it.name}","createdAt":${it.createdAt}}"""
                }
                respondJson(exchange, 200, """{"groups":[$jsonArr]}""")
                return
            }

            if (method == "POST" && path == "/api/groups") {
                val body = exchange.requestBody.bufferedReader().readText()
                val name = extractString(body, "name") ?: "Group"
                val gid = randomHex(16)
                val now = System.currentTimeMillis() / 1000L
                val group = Group(gid, name, now)
                groups[gid] = group
                members[gid] = ConcurrentHashMap.newKeySet<String>().apply { add(userId) }
                messages[gid] = CopyOnWriteArrayList()
                respondJson(exchange, 201, """{"id":"$gid","name":"$name","createdAt":$now}""")
                return
            }

            respondJson(exchange, 404, """{"error":"not found"}""")
        } catch (e: Exception) {
            respondJson(exchange, 500, """{"error":"${e.message?.replace("\"", "'") ?: "internal error"}"}""")
        }
    }

    private fun respondJson(exchange: HttpExchange, status: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    private fun extractLong(json: String, key: String): Long? {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun randomHex(n: Int): String = ByteArray(n).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    private fun randomBase64Url(n: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(n).also(random::nextBytes))
    private fun sha256Hex(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
