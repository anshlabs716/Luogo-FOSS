# Luogo-FOSS

<div align="center">

### Privacy-first location sharing & device finding for Android

End-to-end encrypted sharing · Crowdsourced BLE finding · Self-hostable backend

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/) [![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/) [![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)

[![Android Auto](https://img.shields.io/badge/Android%20Auto-supported-3DDC84?logo=androidauto&logoColor=white)](https://developer.android.com/training/cars) [![License: EUPL--1.2](https://img.shields.io/badge/license-EUPL--1.2-blue)](LICENSE)

**No mandatory Google account · No ads**

</div>

---

## What is Luogo-FOSS?

Luogo-FOSS is a privacy-first Android application for:

- 📍 **Real-time location sharing**
- 👥 **Live people & group sharing**
- 📌 **Saved places & geofencing**
-    **Location history & trip playback**
-    **Crowdsourced BLE item finding**
- 🗺️ **Maps, satellite imagery & offline maps**
-    **Android Auto**
- 🔐 **End-to-end encrypted communication**
-    **Self-hosted backend support**

**Supported Platforms:** Android phones/tablets + Android Auto only.



## Architecture

### Luogo-FOSS structure

Luogo-FOSS is a fork of the original Luogo, migrated to a **native Kotlin Android application**, instead of the original implementation in Flutter/Dart and Swift.

| Layer | Technology |
| --- | --- |
| Language | **Kotlin** |
| Build | **Gradle Kotlin DSL** |
| UI | **Jetpack Compose + Material 3** |
| Architecture | Clean architecture + ViewModel |
| Async | Kotlin Coroutines + Flow |
| Database | Room |
| Location | Native Android location APIs |
| BLE | Native Android Bluetooth/BLE APIs |
| Background | Native Android services/workers |
| Maps | MapLibre / OSM-compatible providers |
| Android Auto | Native Android Auto APIs |
| Crypto | Android Keystore + established cryptographic libraries |
| Backend | **Kotlin** |

### 🚫 Not part of the final Android app

- Flutter / Dart
- Swift / iOS
- Wear OS
- React Native / Electron
- Rust / NDK / C++ unless a genuinely unavoidable native dependency requires it

The goal is a **real native Android application**, not a Flutter application with a Kotlin wrapper.

## Real-Time Location

Designed around multi-source location and sensor fusion:

- GNSS, including dual-frequency positioning where supported
- Network, Wi-Fi, and cellular positioning
- Wi-Fi RTT where supported
- BLE/UWB ranging where supported
- Accelerometer, gyroscope, magnetometer, and barometer
- Sensor fusion and inertial dead reckoning
- Motion/activity awareness
- Heading, speed, altitude, and accuracy
- Significant-location changes
- Background location
- Geofencing
- Stale-fix and impossible-jump detection
- Mock-location detection
- Offline location caching
- Battery-aware behavior
- Target of approximately **2-second meaningful updates while moving**, subject to Android, hardware, and network limitations

## 👥 People & Location Sharing

- Real-time shared locations
- Groups and invitations
- Custom names, avatars, and colors
- Status (Online, Offline, Last Seen)
- Location accuracy, speed, heading, and battery information
- Temporary sharing
- Pause/resume sharing
- Granular Android permissions

## Places & History

### Saved places

- Home, School, Work, and custom places
- Configurable geofence radius
- Arrival/departure detection
- Hysteresis to reduce notification noise

### Location history

- Daily / weekly / monthly views
- Route visualization and playback
- Trip detection
- Distance traveled
- Time spent at saved places
- GPX 1.1 export
- Local retention and deletion controls

## Privacy-Preserving Item Finding

Supports personal items such as **phones, earbuds, watches, laptops, keys, bags, bikes, and custom items**.

Every item has a human-readable name throughout the UI, for example:

> `Ansh's Pixel Buds 3`

> `Ansh's Commuter Bike`

> `Everyday Keyring`

Raw BLE identifiers remain internal.

### Finding protocol

- Rotating BLE identifiers
- Ephemeral Identity Key (EIK)
- HKDF-SHA256 derivation
- Encrypted sightings
- Offline report queue
- Replay protection
- Authenticated item enrollment
- Crowdsourced helper-device reports
- Owner-side location reporting
- Unknown-tracker / abuse protection

## 🔐 Cryptography & Privacy

The security architecture separates device authentication, group encryption, and item-finding identities.

Cryptographic primitives include:

- Ed25519
- HKDF-SHA256
- ChaCha20-Poly1305
- AES-256-GCM
- Secure random key generation
- Android Keystore where appropriate

Security also depends on correct nonce management, key storage, authentication, replay protection, and protocol design—not merely the choice of algorithms.

### Privacy goals

- ❌ No mandatory Google account
- ❌ No mandatory Google Play Services for core functionality
- ❌ No advertisements
- ❌ No unnecessary analytics
- ✅ Granular permissions
- ✅ Encryption
- ✅ Export/delete controls
- ✅ Self-hostable backend
- ✅ Tor/Orbot support
- ✅ SOCKS5/custom proxy support

Hardware- or Android-version-specific capabilities are detected rather than falsely presented as universally available.

## 🗺️ Maps & Navigation

- MapLibre / OSM-compatible architecture
- Standard, dark, terrain/elevation, and satellite modes
- Pinch zoom, rotation, tilt, compass, scale, and follow mode
- Accuracy indicators
- Offline map regions
- Configurable map providers with required attribution
- Routing abstraction
- Walking / cycling / driving routing
- Offline fallback where practical

## 🚗 Android Integration

- Android Auto
- Quick Settings tile
- Home-screen widget
- Share sheet
- Deep links
- Location and finding notifications
- Background services where required by Android

**Android-only. No Wear OS module.**

## Backend

The backend is also written in Kotlin

~~~text
┌──────────────────────────────┐
│       Luogo-FOSS Android     │
│                              │
│  Kotlin · Compose · AndroidX │
│  Room · BLE · Location       │
│  Maps · Android Auto         │
└──────────────┬───────────────┘
               │ encrypted API
               ▼
┌──────────────────────────────┐
│        Kotlin Backend        │
│                              │
│   Relay · Sharing · Finding  │
│   Authentication · Sync      │
└──────────────────────────────┘
~~~

## 🛠️ Migration Status

Luogo-FOSS is undergoing a **Flutter/Dart → native Kotlin Android migration**.

1. 🏗️ Native Kotlin Android foundation
2. 📍 Location engine
3. 👥 Live people sharing
4. 📌 Places & history
5. 🎧 BLE item finding
6. 🔐 Finding backend integration
7. 🗺️ Maps, satellite, offline maps & routing
8. 🚗 Android Auto & Android integrations
9. 🛡️ Security hardening, diagnostics, testing & release preparation

The migration is incremental. Existing functionality is replaced subsystem-by-subsystem rather than blindly performing a giant rewrite.

Each major stage should be compiled, tested, and validated before moving on.

## 📦 Project Structure

The intended final structure is:

~~~text
Luogo-FOSS/
├── Android application/
│   ├── Kotlin
│   ├── Gradle Kotlin DSL
│   ├── Jetpack Compose
│   ├── AndroidX
│   └── Room
│
└── server/
    └── Kotlin Backend
~~~

The original Flutter/Dart implementation is being replaced and will not be the Android runtime.

## 📜 License

Licensed under the **European Union Public Licence v. 1.2 (EUPL-1.2)**.

See [LICENSE](LICENSE).

Original upstream project: https://github.com/lukehmcc/luogo

All required upstream attribution and licensing information is preserved.

<div align="center">

### Private by design. Built for Android. Built to be yours.

**Luogo-FOSS**

</div>
