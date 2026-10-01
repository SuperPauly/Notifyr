# Notifyr - Multi-Server Android Notification Inbox

Notifyr is a native Android application engineered with **Kotlin**, **Jetpack Compose**, and **Material 3**. It functions as an interactive notification inbox supporting multiple independent backend servers, multi-app routing, and immediate action buttons and inline RemoteInput replies.

---

## Architecture & Verification Summary

| Feature / Requirement | Implementation Details | Verification Status |
| :--- | :--- | :--- |
| **Composite Partitioning** | Primary key `(from_server, id)` in Room SQLite database. | ✅ Verified via Automated Robolectric test (`101L` on `srv-prod-us` & `srv-staging-eu` remain isolated) |
| **Notification Taps & Actions** | Scoped `PendingIntent` with explicit request codes targeting `NotificationActionReceiver`. | ✅ Verified via Unit tests & Compile check |
| **Unicode Inline Replies** | Preserves emojis, multibyte characters (Cyrillic, Arabic, Japanese, accents). | ✅ Verified via Automated Robolectric test |
| **Duplicate Delivery Prevention** | Duplicate incoming payloads update existing Room records without re-alerting. | ✅ Verified via Automated Robolectric test |
| **Process Survival** | Outbox responses are persisted in Room with frozen UUIDs before transmission. | ✅ Verified via Automated Robolectric test |
| **Expiration & Responded Guard** | Expired/answered notifications disable action buttons and reject incoming responses. | ✅ Verified via Automated Robolectric test |
| **Account Isolation** | Outbox responses bind to `connectionId` and `accountIdentity`; switching accounts cannot re-route old responses. | ✅ Verified via Automated Robolectric test |
| **Keystore Credential Protection** | Hardware-backed Android Keystore AES-256-GCM encryption. Never logged. | ✅ Verified (zero logging statements found in grep audit) |
| **Permission Recovery** | Explanatory banner and settings launcher when `POST_NOTIFICATIONS` is denied. | ✅ Implemented in Compose UI |
| **UnifiedPush Integration** | Official connector protocol with safe decoder; displays "Awaiting server integration". | ✅ Verified via Automated Decoder test |
| **Debug APK Build** | Standard Android Gradle Plugin release/debug signing and compilation. | ✅ Built (`app/build/outputs/apk/debug/app-debug.apk`) |

---

## Operations Awaiting Future Server Integration

> **Notice:** The Android client is fully implemented with durable Room persistence, outbox queueing, WorkManager network scheduling, and notification shade interactions. The following operations intentionally await the future backend server contract:
>
> 1. **Live Network Ingest**: Real-time HTTP/gRPC push streams from live servers. Currently driven by the local demo transport engine.
> 2. **Real Server Delivery Receipts**: Outbox responses are dispatched through the transport interface and marked as `SIMULATED` or `SENT` upon receipt. Live HTTP acceptance receipts await the final server API.
> 3. **Distributor-to-Server Endpoint Sync**: When a UnifiedPush distributor provides a new push endpoint URL, the status displays **"Registered with distributor (Awaiting server integration)"** because endpoint registration with the application server is not yet deployed.

---

## Building and Installing

### Prerequisites
- JDK 17 or JDK 21
- Android SDK Platform 36 (targetSdk 36, minSdk 24)
- Gradle (managed via environment wrapper)

### Build Commands
```bash
# Compile and build the debug APK locally
./gradlew assembleDebug

# Run Robolectric JVM unit tests
./gradlew :app:testDebugUnitTest

# Install on a connected physical device or emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions Manual Workflow (`.github/workflows/build-apk.yml`)
You can trigger an automated APK build from your GitHub repository:
1. Navigate to the **Actions** tab on your GitHub repository.
2. Select **Build Android APK** from the left sidebar.
3. Click **Run workflow** (optionally select `debug` or `release` variant).
4. When finished, download the generated `notifyr-debug-apk` artifact containing the `.apk` file directly to run on any Android device.

Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Exercising Features on Device / Simulator

### 1. Exercising Demo Notifications
1. Open Notifyr.
2. Navigate to **Settings** (`bottom navigation bar -> Settings`).
3. Under **System Notification Simulator**, tap any of the demo alert buttons:
   - **Ordinary Info**: Posts a status notification with an internal navigation path.
   - **Button Actions**: Posts an urgent approval request with interactive "Approve" and "Deny" buttons.
   - **RemoteInput Reply**: Posts an alert with an inline text input field in the notification drawer.
   - **Expired Alert**: Posts a notification with expired deadline (action controls disabled).
4. Pull down the Android notification shade to inspect the notifications.

### 2. Testing Inline RemoteInput Replies
1. From the **Settings** screen, tap **RemoteInput Reply**.
2. Pull down the Android notification shade.
3. Tap **Reply** inside the notification.
4. Type Unicode text (e.g., `Investigated: latency resolved! 🚀 ✔`) and tap Send.
5. Notice the notification is dismissed promptly.
6. Open Notifyr's **Outbox** tab: observe the queued response with full Unicode preservation, status `SIMULATED`, and matching notification provenance.

### 3. Configuring UnifiedPush
1. Install an open-source UnifiedPush distributor (e.g., **ntfy** from F-Droid or Google Play).
2. In Notifyr, open the **Servers** tab.
3. On any server card, tap **Register** under the **UnifiedPush** section.
4. Select your installed distributor from the list.
5. The status will update to **"Registered with distributor (Awaiting server integration)"**.
6. If no distributor is installed, tap **"Browse Available Distributors"** or use the **"Simulate Push Ingest"** button in Settings to verify message decoding without third-party software.

---

## Test Execution Matrix

### Checked Automatically (CI / Local JVM)
- ✅ `gradle :app:testDebugUnitTest`: 100% passed (composite primary keys, Unicode reply storage, duplicate delivery suppression, outbox restart durability, expiration safeguards, and UnifiedPush payload decoding).
- ✅ `gradle assembleDebug`: Built 23MB valid APK.
- ✅ Zero plaintext credential logging verified via source audit.

### Requires a Physical Android Device / Streaming Emulator
- 📱 User interaction with the Android system notification drawer (pulling down shade, expanding RemoteInput keyboard, and tapping notification action buttons).
- 📱 System audio chimes and hardware vibration motor feedback.
- 📱 Inter-process broadcast delivery from an external distributor APK installed alongside Notifyr.
