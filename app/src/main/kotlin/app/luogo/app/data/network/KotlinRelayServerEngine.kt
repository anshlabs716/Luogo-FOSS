package app.luogo.app.data.network

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Transport-agnostic core of the Luogo relay.
 *
 * Zero-knowledge by design: the server stores and forwards *ciphertext* it produced nothing
 * itself. It holds no group keys, so it cannot decrypt anyone's location even if compromised.
 * Authorisation is checked here (is the sender a member of the group, is the invite still
 * valid) while confidentiality is handled entirely client-side by CryptoEngine.
 *
 * Shared by the Android client (which embeds it for self-hosted single-process deployments)
 * and by the standalone `server/` process, so both enforce identical rules.
 */
class KotlinRelayServerEngine(private val maxLogPerGroup: Int = 500) {

    data class User(
        val id: String,
        val name: String,
        val color: Long,
        val publicKey: String,
        val createdAt: Long
    )

    data class Group(
        val id: String,
        val name: String,
        val createdAt: Long,
        val ownerId: String
    )

    data class Invite(
        val token: String,
        val groupId: String,
        val createdBy: String,
        val expiresAt: Long
    )

    data class StoredMessage(
        val seq: Long,
        val groupId: String,
        val senderId: String,
        val timestamp: Long,
        val ciphertext: String
    )

    data class StoredSighting(
        val reportId: String,
        val rotatingBleIdHex: String,
        val helperLatitude: Double,
        val helperLongitude: Double,
        val helperAccuracyMeters: Float,
        val rssiDbm: Int,
        val encryptedPayload: String,
        val authTag: String,
        val timestamp: Long
    )

    /** Handle returned by [connectWebSocket]; closing it detaches the listener. */
    fun interface WebSocketSession {
        fun close()
    }

    private val random = SecureRandom()
    private val sequence = AtomicLong(0L)

    private val users = ConcurrentHashMap<String, User>()
    private val tokenToUserId = ConcurrentHashMap<String, String>()
    private val groups = ConcurrentHashMap<String, Group>()
    private val members = ConcurrentHashMap<String, MutableSet<String>>()
    private val invites = ConcurrentHashMap<String, Invite>()
    private val inviteUseCount = ConcurrentHashMap<String, Int>()
    private val messageLog = ConcurrentHashMap<String, CopyOnWriteArrayList<StoredMessage>>()
    private val sightingStore = ConcurrentHashMap<String, CopyOnWriteArrayList<StoredSighting>>()
    private val liveSockets = ConcurrentHashMap<String, CopyOnWriteArrayList<(String) -> Unit>>()

    // ------------------------------------------------------------------ users

    /** Registers a user and returns the profile plus its bearer token. The token is shown once. */
    fun createUser(name: String, color: Long, publicKey: String): Pair<User, String> {
        val user = User(
            id = randomHex(16),
            name = name,
            color = color,
            publicKey = publicKey,
            createdAt = System.currentTimeMillis()
        )
        users[user.id] = user
        val token = randomToken(32)
        tokenToUserId[sha256(token)] = user.id
        return user to token
    }

    /**
     * Resolves a bearer token to a user. Accepts the raw token or an `Authorization` header
     * value. Returns null for anything unrecognised.
     */
    fun authenticateToken(rawHeader: String?): User? {
        val token = rawHeader?.trim()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
        if (token.isNullOrEmpty()) return null
        val userId = tokenToUserId[sha256(token)] ?: return null
        return users[userId]
    }

    fun userById(id: String): User? = users[id]

    // ----------------------------------------------------------------- groups

    fun createGroup(ownerId: String, name: String): Group {
        require(users.containsKey(ownerId)) { "unknown owner" }
        val group = Group(randomHex(16), name, System.currentTimeMillis(), ownerId)
        groups[group.id] = group
        members[group.id] = ConcurrentHashMap.newKeySet<String>().apply { add(ownerId) }
        messageLog[group.id] = CopyOnWriteArrayList()
        sightingStore[group.id] = CopyOnWriteArrayList()
        return group
    }

    fun groupsForUser(userId: String): List<Group> =
        members.entries.filter { it.value.contains(userId) }.mapNotNull { groups[it.key] }

    fun isMember(groupId: String, userId: String): Boolean =
        members[groupId]?.contains(userId) == true

    fun leaveGroup(groupId: String, userId: String): Boolean =
        members[groupId]?.remove(userId) ?: false

    fun removeMember(groupId: String, actorId: String, targetId: String): Boolean {
        val group = groups[groupId] ?: return false
        // Only the owner may remove someone else.
        if (group.ownerId != actorId) return false
        return members[groupId]?.remove(targetId) ?: false
    }

    // ---------------------------------------------------------------- invites

    /** Single-use invite token that expires. The group key travels inside the invite payload. */
    fun createInvite(groupId: String, createdBy: String, nowMs: Long = System.currentTimeMillis()): Invite? {
        if (!isMember(groupId, createdBy)) return null
        val invite = Invite(
            token = randomToken(24),
            groupId = groupId,
            createdBy = createdBy,
            expiresAt = nowMs + INVITE_TTL_MS
        )
        invites[invite.token] = invite
        return invite
    }

    /**
     * Redeems an invite exactly once.
     *
     * Replay is prevented by counting use, so a leaked invite link cannot be used to admit a
     * second stranger after the intended user joins.
     */
    fun joinGroupWithInvite(
        groupId: String,
        userId: String,
        inviteToken: String,
        nowMs: Long = System.currentTimeMillis()
    ): Group? {
        val invite = invites[inviteToken] ?: return null
        if (invite.groupId != groupId) return null
        if (nowMs > invite.expiresAt) {
            invites.remove(inviteToken)
            return null
        }
        if (!users.containsKey(userId)) return null
        val uses = inviteUseCount.merge(inviteToken, 1, Int::plus) ?: 1
        if (uses > 1) return null
        members[groupId]?.add(userId) ?: return null
        return groups[groupId]
    }

    // --------------------------------------------------------------- messages

    /**
     * Appends an opaque ciphertext. Only group members may post. The log is bounded so a
     * malicious client cannot exhaust server memory.
     */
    fun appendMessage(
        groupId: String,
        senderId: String,
        ciphertext: String,
        nowMs: Long = System.currentTimeMillis()
    ): StoredMessage? {
        if (!isMember(groupId, senderId)) return null
        if (ciphertext.isBlank() || ciphertext.length > MAX_CIPHERTEXT_CHARS) return null
        val log = messageLog[groupId] ?: return null
        val message = StoredMessage(
            seq = sequence.incrementAndGet(),
            groupId = groupId,
            senderId = senderId,
            timestamp = nowMs,
            ciphertext = ciphertext
        )
        log.add(message)
        while (log.size > maxLogPerGroup) log.removeAt(0)
        fanOut(groupId, """{"type":"message","seq":${message.seq},"groupId":"$groupId","senderId":"$senderId","ciphertext":"$ciphertext"}""")
        return message
    }

    /** Messages after a sequence number, so a reconnecting client can catch up. */
    fun getMessagesAfter(groupId: String, userId: String, afterSeq: Long): List<StoredMessage>? {
        if (!isMember(groupId, userId)) return null
        val log = messageLog[groupId] ?: return null
        return log.filter { it.seq > afterSeq }
    }

    // -------------------------------------------------------------- sightings

    /** Accepts a crowdsourced BLE sighting. Opaque payload; the relay cannot read it. */
    fun recordSighting(sighting: StoredSighting): Boolean {
        val store = sightingStore[sighting.rotatingBleIdHex.substring(0, 8)] ?: return false
        if (store.any { it.reportId == sighting.reportId }) return false
        store.add(sighting)
        while (store.size > maxLogPerGroup) store.removeAt(0)
        return true
    }

    /** Most recent authenticated sighting for an item, so the owner sees a position quickly. */
    fun latestSightingForItem(rotatingBleIdPrefix: String): StoredSighting? =
        sightingStore[rotatingBleIdPrefix]?.maxByOrNull { it.timestamp }

    // ------------------------------------------------------------- websocket

    fun connectWebSocket(userId: String, onPayload: (String) -> Unit): WebSocketSession {
        val set = liveSockets.getOrPut(userId) { CopyOnWriteArrayList<(String) -> Unit>() }
        val listener: (String) -> Unit = { payload: String -> onPayload(payload) }
        set.add(listener)
        return WebSocketSession { liveSockets[userId]?.remove(listener) }
    }

    private fun fanOut(groupId: String, payload: String) {
        for (memberId in members[groupId].orEmpty()) {
            liveSockets[memberId]?.forEach { listener -> runCatching { listener(payload) } }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun randomHex(bytes: Int): String =
        ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private fun randomToken(bytes: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

    /** Tokens are stored only as hashes, so a memory dump does not yield usable credentials. */
    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val INVITE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_CIPHERTEXT_CHARS = 32 * 1024
    }
}