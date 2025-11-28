package cam.manoash.twoipcapps.sharedmemoryappb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cam.manoash.twoipcapps.sharedmemoryappa.ISharedMemoryServiceA
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main Activity for SharedMemory App B
 * Demonstrates bidirectional IPC using Android Shared Memory (Ashmem)
 */
class MainActivityB : ComponentActivity() {

    companion object {
        private const val TAG = "SharedMemoryAppB"
        private const val REMOTE_PACKAGE = "cam.manoash.twoipcapps.sharedmemoryappa"
        private const val REMOTE_SERVICE_CLASS = "$REMOTE_PACKAGE.SharedMemoryServiceA"
        private const val REMOTE_SERVICE_ACTION = "cam.manoash.twoipcapps.sharedmemoryappa.SharedMemoryService"
    }

    // State management
    private val events = mutableStateListOf<String>()
    private val connectionStatus = mutableStateOf("Disconnected")

    // Services
    private var remoteService: ISharedMemoryServiceA? = null
    private var localService: SharedMemoryServiceB? = null
    private var localBinder: ISharedMemoryServiceB? = null

    // Shared memory helper for local service only
    private val localMemoryHelper = SharedMemoryHelper()

    // Polling control
    private val polling = AtomicBoolean(false)

    // Callback for data changes from local service
    private val localCallback = object : IMemoryCallbackB.Stub() {
        override fun onDataChanged() {
            Log.d(TAG, "Local data changed notification received")
            runOnUiThread {
                readFromLocalMemory()
            }
        }
    }

    // Callback for data changes from remote service (App A)
    private val remoteCallback = object : cam.manoash.twoipcapps.sharedmemoryappa.IMemoryCallbackA.Stub() {
        override fun onDataChanged() {
            Log.d(TAG, "Remote callback: data changed")
            runOnUiThread { readFromRemoteMemory() }
        }
    }

    // Remote service connection (App A)
    private val remoteConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Remote service connected: $name")
            remoteService = ISharedMemoryServiceA.Stub.asInterface(service)

            // Register remote callback so App A can notify us
            try {
                remoteService?.registerCallback(remoteCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register remote callback", e)
            }

            // Use AIDL read/write on remoteService; no need to open raw FD
            updateConnectionStatus(true)
            addEvent("✓ Connected to App A")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Remote service disconnected: $name")
            try {
                remoteService?.unregisterCallback(remoteCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister remote callback", e)
            }
            remoteService = null
            updateConnectionStatus(false)
            addEvent("✗ Disconnected from App A")
        }
    }

    // Local service connection
    private val localConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Local service connected")
            localBinder = ISharedMemoryServiceB.Stub.asInterface(service)
            localService = (service as SharedMemoryServiceB.LocalBinder).getService()

            // Register callback
            localService?.registerCallback(localCallback)

            // Setup local memory
            setupLocalMemory()
            addEvent("✓ Local service ready")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Local service disconnected")
            localService?.unregisterCallback(localCallback)
            localService = null
            localBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bindToServices()
        setupUI()
    }

    private fun bindToServices() {
        bindToRemoteService()
        bindToLocalService()
    }

    private fun bindToRemoteService() {
        val intent = Intent(REMOTE_SERVICE_ACTION).apply {
            component = ComponentName(REMOTE_PACKAGE, REMOTE_SERVICE_CLASS)
        }

        val bound = bindService(intent, remoteConnection, BIND_AUTO_CREATE)
        Log.d(TAG, "Binding to remote service: $bound")

        if (!bound) {
            Log.e(TAG, "Failed to bind to remote service!")
            addEvent("ERROR: Cannot bind to App A")
        }
    }

    private fun bindToLocalService() {
        val intent = Intent(this, SharedMemoryServiceB::class.java)
        bindService(intent, localConnection, BIND_AUTO_CREATE)
    }

    private fun setupLocalMemory() {
        try {
            // For same-process local service binding we will use the service API (readData/writeData)
            // Avoid opening the raw ParcelFileDescriptor which relies on hidden MemoryFile constructors.
            Log.d(TAG, "Local service ready for read/write via AIDL: localService=$localService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup local memory", e)
            addEvent("ERROR: Local memory setup failed")
        }
    }

    private fun readFromLocalMemory() {
        try {
            val data = localService?.readData() ?: localMemoryHelper.readFromMemory()
            if (data != null && data.isNotEmpty()) {
                addEvent("📥 Received from A: $data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read local memory", e)
        }
    }

    private fun readFromRemoteMemory() {
        try {
            // Prefer AIDL read
            val data = try {
                remoteService?.readData()
            } catch (e: Exception) {
                null
            }

            if (data != null && data.isNotEmpty()) {
                runOnUiThread { addEvent("📥 Received from A: $data") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read remote memory", e)
        }
    }

    private fun setupUI() {
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        appName = "Shared Memory App B",
                        targetAppName = "App A",
                        connectionStatus = connectionStatus.value,
                        events = events,
                        onSendClick = ::sendEventToRemote
                    )
                }
            }
        }
    }

    private fun sendEventToRemote() {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val message = "Hello from App B at $timestamp"

        if (remoteService == null) {
            Log.e(TAG, "Cannot send: not connected to App A")
            addEvent("ERROR: Not connected to App A")
            return
        }

        try {
            // Use AIDL write so remote service writes into its own shared memory and broadcasts
            try {
                remoteService?.writeData(message)
            } catch (e: Exception) {
                Log.e(TAG, "Remote write failed", e)
                addEvent("ERROR: Remote write failed - ${e.message}")
            }

            addEvent("→ Sent: $message")
            Log.d(TAG, "Sent event via shared memory: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send event", e)
            addEvent("ERROR: Send failed - ${e.message}")
        }
    }

    private fun addEvent(event: String) {
        events.add(0, event)
        if (events.size > 50) {
            events.removeAt(events.size - 1)
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        connectionStatus.value = if (connected) "Connected" else "Disconnected"
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        try {
            // stop polling thread
            polling.set(false)

            localService?.unregisterCallback(localCallback)
            try {
                remoteService?.unregisterCallback(remoteCallback)
            } catch (_: Exception) {
                // ignore
            }
            unbindService(remoteConnection)
            unbindService(localConnection)
            localMemoryHelper.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
    }
}

@Composable
fun MainScreen(
    appName: String,
    targetAppName: String,
    connectionStatus: String,
    events: List<String>,
    onSendClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = appName,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Status: $connectionStatus",
            style = MaterialTheme.typography.bodyMedium,
            color = if (connectionStatus.contains("Connected"))
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onSendClick,
            enabled = connectionStatus.contains("Connected"),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Send to $targetAppName (Shared Memory)")
        }

        Text(
            text = "Events:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(events) { event ->
                    Text(
                        text = event,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
