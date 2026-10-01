# Luogo-FOSS

**Privacy-first, native Android location sharing, device finding, and Android Auto.**

Luogo-FOSS is an open-source Android application for real-time location sharing, saved places, location history, and privacy-preserving crowdsourced BLE item finding.

**Platform scope:** Android phone/tablet + Android Auto.  
**No Wear OS app. No iOS app. No desktop app.**

Licensed under the **European Union Public Licence v. 1.2 (EUPL-1.2)**.  
Original upstream: https://github.com/lukehmcc/luogo

---

## Architecture

### Android application

The Android application is being migrated from the original Flutter implementation to a **native Kotlin Android application**.

- Kotlin-first Android codebase
- Gradle Kotlin DSL
- Jetpack Compose + Material 3
- AndroidX / Jetpack
- Kotlin coroutines and Flow
- Room for local persistence
- Native Android location, sensor, BLE, background-service, notification, widget, Quick Settings, and Android Auto APIs
- Native Android networking and cryptography
- No Flutter or Dart dependency in the final Android application
- No Google Play Services requirement for core functionality
- No mandatory Google account
- No advertisements or unnecessary analytics

The migration is incremental: existing functionality is replaced subsystem-by-subsystem, with builds and tests run throughout the process.

### Backend

The existing **Go backend remains Go**. The Kotlin migration applies to the Android application and does not require rewriting the server.

---

## Core Systems

### Real-time location

- GNSS, including dual-frequency positioning where supported
- Network/Wi-Fi/cellular positioning
- Wi-Fi RTT where supported
- BLE/UWB ranging where supported by the device
- Accelerometer, gyroscope, magnetometer, barometer, and other available sensors
- Sensor fusion and dead reckoning
- Motion/activity awareness
- Heading, speed, altitude, and accuracy
- Significant-location changes and geofencing
- Background location with battery-aware behavior
- Stale-fix and impossible-jump detection
- Mock-location detection
- Offline location caching
- Server-side interpolation/anomaly handling where appropriate
- Target of approximately 2-second meaningful location updates while moving, subject to Android/device/network limitations

### Live people sharing

- Real-time shared locations
- Groups and invitations
- Custom names, avatars, and colors
- Online/offline and last-seen state
- Current location, accuracy, speed, heading, and battery information
- Temporary location sharing
- Pause/resume sharing
- Granular Android permissions

### Saved places and history

- Home, School, Work, and custom places
- Configurable geofence radii
- Arrival/departure detection
- Daily, weekly, and monthly history
- Route playback
- Distance and time-at-place statistics
- GPX export
- Local history retention and deletion controls

### Privacy-preserving item finding

Luogo-FOSS supports finding personal items such as phones, earbuds, watches, laptops, keys, bags, bikes, and custom items.

Every item has a human-readable friendly name throughout the UI, such as:

- `Ansh's Pixel Buds 3`
- `Ansh's Commuter Bike`
- `Everyday Keyring`

Raw BLE identifiers remain internal.

The finding system is designed around:

- Rotating encrypted BLE identifiers
- Ephemeral identity keys
- HKDF-SHA256 key derivation
- Encrypted BLE sightings
- Offline report queues
- Replay protection
- Authenticated item enrollment
- Abuse/unknown-tracker protection
- Crowdsourced helper-device reports
- Owner-side location reporting

### Cryptography

The Android security layer separates device authentication, group encryption, and item-finding identities.

Planned/implemented primitives include:

- Ed25519 signatures
- HKDF-SHA256
- ChaCha20-Poly1305
- AES-256-GCM
- Android Keystore where appropriate
- Secure random key generation
- Authenticated encryption and replay protection

Cryptographic primitives are not treated as a substitute for secure key storage, nonce management, authentication, or protocol-level protections.

### Maps and navigation

- MapLibre/OSM-compatible map architecture
- Standard, dark, terrain/elevation, and satellite map modes
- Pinch zoom, rotation, tilt, compass, scale, follow mode, and accuracy indicators
- Offline map downloads and management
- Configurable map providers with required attribution
- Routing abstraction
- Walking, cycling, and driving routing
- Offline fallback behavior where practical

### Android integration

- Android Auto
- Quick Settings tile
- Home-screen widget
- Share sheet integration
- Deep links
- Location and finding notifications
- Background services where required by Android
- Android-only implementation; no Wear OS module

---

## Privacy

Luogo-FOSS is designed to minimize dependence on proprietary Google services.

- No mandatory Google account
- No mandatory Google Play Services for core functionality
- No ads
- No unnecessary analytics
- End-to-end encryption for applicable shared data
- Granular permissions
- Local data controls
- Data export and deletion
- Self-hostable backend
- Tor/Orbot support
- SOCKS5/custom proxy support

Capabilities that depend on specific device hardware or Android versions are detected rather than falsely advertised as universally available.

---

## Project Structure

The intended final architecture is:

```
Luogo-FOSS/
├── Android application
│   ├── Kotlin
│   ├── Gradle Kotlin DSL
│   ├── Jetpack Compose / Material 3
│   ├── AndroidX
│   └── Room
│
└── server/
    └── Go backend
```

The original Flutter/Dart implementation is being replaced rather than used as the Android runtime.

---

## Development

The migration follows a staged approach:

1. Native Kotlin Android foundation
2. Location engine
3. Live people sharing
4. Saved places and history
5. BLE item finding
6. Finding backend integration
7. Maps, satellite imagery, offline maps, and routing
8. Android Auto and Android integrations
9. Security hardening, diagnostics, documentation, testing, and release preparation

Changes should remain small and coherent. The project should be compiled and tested after each major subsystem migration.

Do not remove Git history, licensing information, or required upstream attribution.

---

## Status

Luogo-FOSS is actively undergoing the **Flutter/Dart → native Kotlin Android migration**.

The target is a complete native Android application with the Go backend retained separately.

This README describes the intended native architecture and feature set; individual capabilities should not be considered complete until implemented, built, and tested in the Android application.

---

## License

Luogo-FOSS is licensed under the **European Union Public Licence v. 1.2 (EUPL-1.2)**.

See [LICENSE](LICENSE) for the complete license text.

Original upstream project: https://github.com/lukehmcc/luogo
