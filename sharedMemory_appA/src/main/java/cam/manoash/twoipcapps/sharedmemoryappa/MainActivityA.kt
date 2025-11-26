package cam.manoash.twoipcapps.sharedmemoryappa

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
import cam.manoash.twoipcapps.sharedmemoryappb.ISharedMemoryServiceB
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main Activity for SharedMemory App A
 * Demonstrates bidirectional IPC using Android Shared Memory (Ashmem)
 */
class MainActivityA : ComponentActivity() {

    companion object {
        private const val TAG = "SharedMemoryAppA"
        private const val REMOTE_PACKAGE = "cam.manoash.twoipcapps.sharedmemoryappb"
        private const val REMOTE_SERVICE_CLASS = "$REMOTE_PACKAGE.SharedMemoryServiceB"
        private const val REMOTE_SERVICE_ACTION = "cam.manoash.twoipcapps.sharedmemoryappb.SharedMemoryService"
    }

    // State management
    private val events = mutableStateListOf<String>()
    private val connectionStatus = mutableStateOf("Disconnected")

    // Services
    private var remoteService: ISharedMemoryServiceB? = null
    private var localService: SharedMemoryServiceA? = null
    private var localBinder: ISharedMemoryServiceA? = null

    // Shared memory helper for local service
    private val localMemoryHelper = SharedMemoryHelper()

    // Callback for data changes from local service
    private val localCallback = object : IMemoryCallbackA.Stub() {
        override fun onDataChanged() {
            Log.d(TAG, "Local data changed notification received")
            runOnUiThread {
                readFromLocalMemory()
            }
        }
    }

    // Callback for data changes from remote service (App B)
    private val remoteCallback = object : cam.manoash.twoipcapps.sharedmemoryappb.IMemoryCallbackB.Stub() {
        override fun onDataChanged() {
            Log.d(TAG, "Remote callback: data changed")
            runOnUiThread { readFromRemoteMemory() }
        }
    }

    // Remote service connection (App B)
    private val remoteConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Remote service connected: $name")
            remoteService = ISharedMemoryServiceB.Stub.asInterface(service)

            // Try to register remote callback so App B can notify us
            try {
                remoteService?.registerCallback(remoteCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register remote callback", e)
            }

            // Get shared memory from remote service
            // Remote read/write will use AIDL calls; no need to open raw FD here
            updateConnectionStatus(true)
            addEvent("✓ Connected to App B")
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
            addEvent("✗ Disconnected from App B")
        }
    }

    // Local service connection
    private val localConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Local service connected")
            localBinder = ISharedMemoryServiceA.Stub.asInterface(service)
            localService = (service as SharedMemoryServiceA.LocalBinder).getService()

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

        val bound = bindService(intent, remoteConnection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "Binding to remote service: $bound")

        if (!bound) {
            Log.e(TAG, "Failed to bind to remote service!")
            addEvent("ERROR: Cannot bind to App B")
        }
    }

    private fun bindToLocalService() {
        val intent = Intent(this, SharedMemoryServiceA::class.java)
        bindService(intent, localConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupLocalMemory() {
        try {
            // For same-process local service binding we will use the service API (readData/writeData)
            // Avoid opening the raw ParcelFileDescriptor, which uses private MemoryFile constructors.
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
                addEvent("📥 Received from B: $data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read local memory", e)
        }
    }

    private fun readFromRemoteMemory() {
        try {
            val data = try {
                remoteService?.readData()
            } catch (e: Exception) {
                null
            }

            if (data != null && data.isNotEmpty()) {
                runOnUiThread { addEvent("📥 Received from B: $data") }
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
                        appName = "Shared Memory App A",
                        targetAppName = "App B",
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
        val message = "Hello from App A at $timestamp"

        if (remoteService == null) {
            Log.e(TAG, "Cannot send: not connected to App B")
            addEvent("ERROR: Not connected to App B")
            return
        }

        try {
            // Use AIDL writeData so the remote service writes into its own shared memory and broadcasts
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
            localService?.unregisterCallback(localCallback)
            try { remoteService?.unregisterCallback(remoteCallback) } catch (_: Exception) {}
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
