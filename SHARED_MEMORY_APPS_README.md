# Shared Memory IPC Apps

This directory contains two Android applications (sharedMemory_appA and sharedMemory_appB) that demonstrate inter-process communication using Android's Shared Memory (Ashmem).

## Architecture

### Components

Each app contains:
1. **SharedMemoryHelper.kt** - Helper class for creating and managing shared memory regions
2. **SharedMemoryService** - Service that provides shared memory access to other processes via AIDL
3. **MainActivity** - UI that allows sending/receiving events through shared memory
4. **AIDL Interfaces**:
   - `ISharedMemoryService` - Provides shared memory file descriptor and notifications
   - `IMemoryCallback` - Receives data change notifications

### How It Works

1. **Shared Memory Creation**:
   - Each app creates its own shared memory region using `MemoryFile` (Android's Ashmem wrapper)
   - The memory file descriptor is exposed via AIDL to other processes

2. **Cross-Process Communication**:
   - App A binds to App B's service and gets its shared memory file descriptor
   - App B binds to App A's service and gets its shared memory file descriptor
   - Each app can write to the remote app's shared memory

3. **Data Format**:
   - Data is stored as: `[4 bytes length][data bytes]`
   - Length is written first as an integer, followed by the UTF-8 encoded string

4. **Notifications**:
   - After writing to remote memory, the writer calls `notifyDataChanged()` on the remote service
   - The remote service broadcasts to all registered callbacks
   - The reader then reads the updated data from shared memory

## Building and Running

### Build the Apps

```bash
cd /Users/ashwani/AndroidStudioProjects/TwoIpcApps

# Build both apps
./gradlew :sharedMemory_appA:assembleDebug :sharedMemory_appB:assembleDebug
```

### Install on Device/Emulator

```bash
# Install App A
adb install sharedMemory_appA/build/outputs/apk/debug/sharedMemory_appA-debug.apk

# Install App B
adb install sharedMemory_appB/build/outputs/apk/debug/sharedMemory_appB-debug.apk
```

### Testing the Communication

1. Launch both apps on the same device
2. Wait for the apps to connect (status will show "Connected")
3. Click "Send to App B" in App A - the message will appear in App B's event log
4. Click "Send to App A" in App B - the message will appear in App A's event log

## Key Features

- **Bidirectional Communication**: Both apps can send and receive messages
- **Shared Memory**: Uses Android's Ashmem for efficient cross-process data sharing
- **AIDL Integration**: File descriptors are passed via AIDL interfaces
- **Real-time Updates**: Polling mechanism checks for changes every 500ms
- **Event Log**: All sent/received messages are displayed in a scrollable list

## Technical Notes

### Shared Memory (Ashmem)

- Android's Anonymous Shared Memory provides efficient IPC
- Memory regions are kernel-backed and can be shared across processes
- File descriptors can be passed via Binder/AIDL using `ParcelFileDescriptor`

### Reflection Usage

The code uses reflection to access Android's internal `MemoryFile` APIs:
```kotlin
val fdField = MemoryFile::class.java.getDeclaredField("mFD")
fdField.isAccessible = true
val fd = fdField.get(memoryFile) as FileDescriptor
```

This is necessary because `MemoryFile` doesn't expose public APIs for getting file descriptors.

### Permissions

Both apps declare `<queries>` in their manifests to allow package visibility:
```xml
<queries>
    <package android:name="cam.manoash.twoipcapps.sharedmemoryappX" />
</queries>
```

## Troubleshooting

### Apps Won't Connect

- Ensure both apps are installed
- Check logcat for binding errors: `adb logcat | grep SharedMemory`
- Verify the service actions match in both manifests

### No Messages Received

- Check that polling thread is running
- Verify shared memory was successfully opened
- Check logcat for read/write errors

### Build Errors

- Ensure Android SDK 36 is installed
- Check that AIDL files are in the correct packages
- Run `./gradlew clean` and rebuild

## Comparison with AIDL-only IPC

| Feature | Shared Memory | AIDL Only |
|---------|--------------|-----------|
| Data Transfer | Direct memory access | Binder transactions |
| Large Data | Efficient | Limited by Binder buffer |
| Setup Complexity | Higher (FD passing) | Lower |
| Performance | Faster for bulk data | Faster for small messages |
| Type Safety | Manual serialization | Automatic |

## License

Part of the TwoIpcApps demonstration project.

