# TwoIpcApps - Android IPC Communication Demo

A production-ready Android project demonstrating **bidirectional Inter-Process Communication (IPC)** between two applications using **AIDL (Android Interface Definition Language)**.

## 🎯 Overview

This project contains two Android applications (`ipc_appA` and `ipc_appB`) that communicate with each other using AIDL-based IPC:

- **App A** can send events to **App B**
- **App B** can send events to **App A**
- Both apps display all sent and received events in real-time
- Full two-way communication with proper service binding

## ✨ Features

- ✅ **Two-way IPC Communication** - Bidirectional message passing
- ✅ **AIDL Implementation** - Clean, professional AIDL architecture
- ✅ **Service Binding** - Proper remote and local service binding
- ✅ **Event Tracking** - All events displayed in UI with timestamps
- ✅ **Material3 UI** - Modern Jetpack Compose UI with Cards
- ✅ **Connection Status** - Real-time connection status indicators
- ✅ **Thread-Safe** - RemoteCallbackList for safe cross-process callbacks
- ✅ **Error Handling** - Comprehensive error handling and logging
- ✅ **Production Ready** - Professional code quality with documentation

## 📱 Applications

### App A (ipc_appA)
- **Package:** `cam.manoash.twoipcapps.appa`
- **Main Activity:** `MainActivityA`
- **Service:** `EventServiceA`
- **AIDL Interfaces:** `IEventServiceA`, `IEventCallbackA`

### App B (ipc_appB)
- **Package:** `cam.manoash.twoipcapps.appb`
- **Main Activity:** `MainActivityB`
- **Service:** `EventServiceB`
- **AIDL Interfaces:** `IEventServiceB`, `IEventCallbackB`

## 🏗️ Architecture

### IPC Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Device / Android OS                   │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  App A (ipc_appA)              App B (ipc_appB)         │
│  ┌──────────────┐              ┌──────────────┐         │
│  │MainActivityA │              │MainActivityB │         │
│  │   callback   │              │   callback   │         │
│  │      ↓       │              │      ↓       │         │
│  │   register   │              │   register   │         │
│  │      ↓       │              │      ↓       │         │
│  │EventServiceA │←─────────────│RemoteService │         │
│  │   (local)    │  sendEvent   │(IEventServiceA)│       │
│  │              │              │              │         │
│  │RemoteService │─────────────→│EventServiceB │         │
│  │(IEventServiceB) sendEvent   │   (local)    │         │
│  └──────────────┘              └──────────────┘         │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

### Key Components

1. **AIDL Interfaces**
   - `IEventServiceA/B` - Service interface for event operations
   - `IEventCallbackA/B` - Callback interface for receiving events

2. **Services**
   - `EventServiceA/B` - Exported services implementing AIDL interfaces
   - Handle callback registration and event broadcasting

3. **Activities**
   - `MainActivityA/B` - UI for sending/receiving events
   - Bind to local service (for receiving) and remote service (for sending)

## 🚀 Quick Start

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK API 24 (Android 7.0) or higher
- Physical device or emulator running Android 7.0+
- ADB (Android Debug Bridge) installed

### Build & Install

```bash
# Navigate to project directory
cd /Users/ashwani/AndroidStudioProjects/TwoIpcApps

# Build both applications
./gradlew :ipc_appA:assembleDebug :ipc_appB:assembleDebug

# Run automated test script
./test_ipc.sh
```

### Manual Installation

```bash
# Build APKs
./gradlew clean :ipc_appA:assembleDebug :ipc_appB:assembleDebug

# Install App A
adb install -r ipc_appA/build/outputs/apk/debug/ipc_appA-debug.apk

# Install App B
adb install -r ipc_appB/build/outputs/apk/debug/ipc_appB-debug.apk

# Launch App A
adb shell am start -n cam.manoash.twoipcapps.appa/.MainActivityA

# Launch App B
adb shell am start -n cam.manoash.twoipcapps.appb/.MainActivityB
```

## 🧪 Testing

### Automated Testing

Use the provided test script for automated installation and log collection:

```bash
./test_ipc.sh
```

The script will:
1. Uninstall old versions
2. Install fresh APKs
3. Launch both apps
4. Collect binding status logs
5. Wait for manual testing
6. Display comprehensive event logs

### Manual Testing

1. **Install both apps** (see installation steps above)
2. **Launch both apps**
3. **Verify connection status** - Both apps should show "Connected | Local: Connected"
4. **Test App A → App B:**
   - Open App A
   - Tap "Send to App B"
   - Verify message appears in App B's event list
5. **Test App B → App A:**
   - Open App B
   - Tap "Send to App A"
   - Verify message appears in App A's event list
6. **Test multiple messages** - Send messages in any order from either app

### Expected Results

✅ Both apps show "Connected | Local: Connected" status  
✅ App A → App B: Messages delivered successfully  
✅ App B → App A: Messages delivered successfully  
✅ Multiple messages work in any order  
✅ Event list shows all sent and received messages

## 📂 Project Structure

```
TwoIpcApps/
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts           # Module includes
├── test_ipc.sh                   # Automated test script
├── README.md                     # This file
├── .gitignore                    # Root gitignore
│
├── ipc_appA/                     # App A Module
│   ├── build.gradle.kts
│   ├── .gitignore
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── aidl/
│       │   ├── cam/manoash/twoipcapps/appa/ipc/
│       │   │   ├── IEventCallbackA.aidl      # Own callback
│       │   │   └── IEventServiceA.aidl       # Own service
│       │   └── cam/manoash/twoipcapps/appb/ipc/
│       │       ├── IEventCallbackB.aidl      # Remote callback
│       │       └── IEventServiceB.aidl       # Remote service
│       ├── java/cam/manoash/twoipcapps/appa/
│       │   ├── MainActivityA.kt              # Main UI
│       │   └── EventServiceA.kt              # AIDL Service
│       └── res/                              # Resources
│
└── ipc_appB/                     # App B Module
    ├── build.gradle.kts
    ├── .gitignore
    └── src/main/
        ├── AndroidManifest.xml
        ├── aidl/
        │   ├── cam/manoash/twoipcapps/appb/ipc/
        │   │   ├── IEventCallbackB.aidl      # Own callback
        │   │   └── IEventServiceB.aidl       # Own service
        │   └── cam/manoash/twoipcapps/appa/ipc/
        │       ├── IEventCallbackA.aidl      # Remote callback
        │       └── IEventServiceA.aidl       # Remote service
        ├── java/cam/manoash/twoipcapps/appb/
        │   ├── MainActivityB.kt              # Main UI
        │   └── EventServiceB.kt              # AIDL Service
        └── res/                              # Resources
```

## 🔧 Configuration

### AIDL Configuration

Each app includes its own AIDL interfaces **and** the other app's interfaces (for binding):

**App A includes:**
- Own: `appa.ipc.IEventServiceA`, `appa.ipc.IEventCallbackA`
- Remote: `appb.ipc.IEventServiceB`, `appb.ipc.IEventCallbackB`

**App B includes:**
- Own: `appb.ipc.IEventServiceB`, `appb.ipc.IEventCallbackB`
- Remote: `appa.ipc.IEventServiceA`, `appa.ipc.IEventCallbackA`

### Manifest Configuration

Both apps require:
- Exported services with unique action names
- `<queries>` declaration for package visibility (Android 11+)

```xml
<queries>
    <package android:name="cam.manoash.twoipcapps.appb" />  <!-- In App A -->
    <package android:name="cam.manoash.twoipcapps.appa" />  <!-- In App B -->
</queries>
```

## 📝 Implementation Details

### Service Binding Pattern

Each app binds to **two services**:

1. **Local Service** - Own service for receiving events
   - Registers callback to receive events
   - Handles incoming IPC calls

2. **Remote Service** - Other app's service for sending events
   - Used only for sending events
   - No callback registration

### Event Flow

**App A sends to App B:**
```
1. App A: Click "Send to App B"
2. App A: remoteService.sendEvent("appa", "Hello from App A")
3. App B's EventService: Receives sendEvent()
4. App B's EventService: Broadcasts to registered callbacks
5. App B's MainActivity: callback.onEvent() receives message
6. App B's UI: Displays message in event list
```

### Thread Safety

- Uses `RemoteCallbackList<T>` for thread-safe callback management
- Automatic dead process cleanup
- Proper callback registration/unregistration

## 🛠️ Development

### Build Commands

```bash
# Clean build
./gradlew clean

# Build both apps
./gradlew :ipc_appA:assembleDebug :ipc_appB:assembleDebug

# Build specific app
./gradlew :ipc_appA:assembleDebug

# Build release
./gradlew :ipc_appA:assembleRelease :ipc_appB:assembleRelease

# Install directly to device
./gradlew :ipc_appA:installDebug :ipc_appB:installDebug

# Run tests
./gradlew test
```

### Debugging

```bash
# View real-time logs
adb logcat | grep -E "EventServiceApp|MainActivityApp"

# View specific app logs
adb logcat | grep "MainActivityAppA"
adb logcat | grep "MainActivityAppB"

# Check if apps are installed
adb shell pm list packages | grep twoipc

# Check if services are running
adb shell dumpsys activity services | grep EventService
```

## 📚 Documentation

Additional documentation files in the project:

- `DEBUGGING_GUIDE.md` - Comprehensive debugging and troubleshooting
- `GITIGNORE_GUIDE.md` - Git configuration explanation
- `MODULE_RENAMING_SUMMARY.md` - Module renaming documentation
- `COMPLETE_REFACTORING_SUMMARY.md` - Code refactoring details
- `TEST_SCRIPT_REFERENCE.md` - Test script command reference
- `FIX_NOT_CONNECTED_ISSUE.md` - Connection troubleshooting

## 🐛 Troubleshooting

### "Not connected to App X"

**Cause:** One or both apps not installed

**Solution:**
```bash
# Check if both apps are installed
adb shell pm list packages | grep twoipc

# Reinstall if missing
./gradlew :ipc_appA:assembleDebug :ipc_appB:assembleDebug
adb install -r ipc_appA/build/outputs/apk/debug/ipc_appA-debug.apk
adb install -r ipc_appB/build/outputs/apk/debug/ipc_appB-debug.apk
```

### "Service binding failed"

**Cause:** Service not found or not exported

**Solution:**
- Verify both apps are installed and running
- Check AndroidManifest.xml has `android:exported="true"`
- Verify `<queries>` declarations exist
- Restart both apps

### "APK not found"

**Cause:** APKs not built or wrong path

**Solution:**
```bash
# Rebuild
./gradlew clean :ipc_appA:assembleDebug :ipc_appB:assembleDebug

# Verify APKs exist
find . -name "*-debug.apk"
```

## 🔐 Security Considerations

- Services are exported (required for IPC)
- No authentication implemented (demo purposes)
- For production:
  - Add custom permissions
  - Implement signature-level permissions
  - Validate sender package names
  - Add encryption for sensitive data

## 📄 License

This project is for educational and demonstration purposes.

## 👥 Authors

Created by Ashwani

## 🙏 Acknowledgments

- Android AIDL documentation
- Jetpack Compose
- Material3 Design

## 📞 Support

For issues or questions:
1. Check the documentation files in the project
2. Review logcat output for error messages
3. Verify all installation steps were followed

## 🔄 Version History

- **v1.0.0** - Initial release with two-way IPC communication
- Module names: `ipc_appA`, `ipc_appB`
- AIDL interfaces with suffix-based naming
- Professional code refactoring
- Comprehensive documentation

---

**Status: Production Ready** ✅

Two-way IPC communication working perfectly with professional code quality!

