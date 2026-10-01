# Luogo-FOSS

*A Privacy-First, End-to-End Encrypted Real-Time Location Sharing & Crowdsourced BLE Item Finding Network for Android & Android Auto*

Licensed under the **European Union Public Licence v. 1.2 (EUPL-1.2)**.
Original upstream: [https://github.com/lukehmcc/luogo](https://github.com/lukehmcc/luogo)

---

## Core Systems

1. **Real-Time Multi-Source Location Sharing (~2-Second Moving Update Target)**
   - **Location Fusion Engine (`LocationFusionEngine`)**: Combines GNSS (standard & dual-frequency L1+L5), Wi-Fi, Wi-Fi RTT (802.11mc), Cellular, BLE Ranging, UWB Ranging, Barometric Altitude, and Inertial Dead Reckoning (Accelerometer + Gyroscope + Magnetometer + Step Detector).
   - **Motion Awareness**: Dynamically classifies `Stationary`, `Walking`, `Running`, `Cycling`, and `Driving` states. Targets ~2-second updates while moving and reduces sensor/network duty cycle when stationary.
   - **Location Quality & Integrity**: Tracks horizontal/vertical accuracy, age, confidence, and source summary (`"8 m accuracy · GNSS (L1+L5) + Wi-Fi · Updated 1.2 s ago"`). Detects stale fixes, impossible jumps, and mock locations without fabricating precision.
   - **60fps Fluid Map Interpolation (`SmoothMarkerInterpolator`)**: Separates raw sensor frequency, fused location frequency (~2s), network sync frequency, and map render frequency.

2. **Privacy-Preserving Crowdsourced BLE Item Finding Network (`BleFindingProtocol`)**
   - **Friendly Human-Readable Names**: Every registered personal item (e.g., `"Ansh's Pixel Buds 3"`, `"Ansh's Commuter Bike"`, `"Everyday Keyring"`) is identified across the entire UI by its friendly name while raw cryptographic BLE identifiers remain strictly internal.
   - **Rotating BLE Identifiers**: Derived every 15 minutes from a 256-bit Ephemeral Identity Key (EIK) seed via `HKDF-SHA256`.
   - **End-to-End Encrypted Sightings & Offline Report Queue**: Helper phones encrypt BLE sightings locally and queue them in Room (`pending_sighting_reports`) when offline, automatically uploading with replay protection and HMAC integrity verification when connectivity returns.
   - **Abuse Protection & Unknown-Tracker Detection**: Continuously evaluates unowned BLE beacons and alerts the user if an unrecognized tracker is observed moving with them across multiple distinct locations.

3. **Maps, Satellite Imagery, Offline Maps & Routing (`MapAndRoutingProvider`)**
   - Supports **Standard Vector/OSM**, **Dark Night Mode**, **Terrain & Elevation**, and **Satellite Imagery** (with configurable licensed tile URLs and full attribution).
   - **Offline Map Region Manager**: Download, pause/resume, monitor storage, and delete offline map regions.
   - **Multi-Modal Routing**: Walking, Cycling, and Driving routing via OSRM with transparent offline geodesic fallback when disconnected.

4. **Saved Places, Geofencing & Location History (`GeofenceAndHistoryEngine`)**
   - **Saved Places**: Configure `Home`, `Work`, `School`, and custom places with geofence radius and hysteresis-protected arrival/departure notifications.
   - **Location History**: Route visualization, interactive route playback scrubber, automatic trip detection, time spent at saved places, GPX 1.1 export, and configurable local retention cleanup.

5. **Android & Android Auto Integration**
   - **Android Auto (`LuogoCarAppService`)**: Driving-safe `PlaceListMapTemplate` displaying Shared People, Saved Places, and Registered Items with distance and one-tap navigation. (Strictly phone/tablet + Android Auto; no Wear OS).
   - **Quick Settings Tile (`LuogoQuickSettingsTileService`)** & **Home-Screen Widget (`LuogoWidgetProvider`)**.
   - **Self-Hosting, Tor/Orbot & SOCKS5 Proxy (`RelayClient`)**: Configurable relay server URL, live connection diagnostics, Tor/Orbot routing (`127.0.0.1:9050`), and custom SOCKS5 proxy support.
