# Luogo Relay (100% Kotlin)

A zero-knowledge end-to-end encrypted Kotlin relay server for **Luogo-FOSS**.
All location payloads and crowdsourced BLE sighting reports are encrypted end-to-end on the Android device (`ChaCha20-Poly1305` / `HKDF-SHA256` / `Ed25519`) — **the relay never sees message plaintext**.

The in-process engine is also integrated into the Android repository under `app.luogo.app.data.network.KotlinRelayServerEngine` and verified by automated JUnit tests.

## Build & Run

```bash
kotlinc RelayServer.kt -include-runtime -d luogo-relay.jar
java -jar luogo-relay.jar -addr :8080 -max-log 500
```

## Docker

```bash
docker build -t luogo-relay-kotlin .
docker run -p 8040:8040 luogo-relay-kotlin -addr :8040
```
