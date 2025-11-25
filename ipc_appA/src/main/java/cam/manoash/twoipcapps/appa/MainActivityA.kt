package cam.manoash.twoipcapps.appa

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
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
import cam.manoash.twoipcapps.appa.ipc.IEventCallbackA
import cam.manoash.twoipcapps.appa.ipc.IEventServiceA
import cam.manoash.twoipcapps.appb.ipc.IEventServiceB

/**
 * Main Activity for App A
 * Demonstrates two-way IPC communication with App B using AIDL
 */
class MainActivityA : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivityAppA"
        private const val REMOTE_PACKAGE = "cam.manoash.twoipcapps.appb"
        private const val REMOTE_SERVICE_CLASS = "$REMOTE_PACKAGE.EventServiceB"
        private const val REMOTE_SERVICE_ACTION = "cam.manoash.twoipcapps.appb.ipc.EventService"
    }

    // State management
    private val events = mutableStateListOf<String>()
    private val connectionStatus = mutableStateOf("Disconnected")
    private val localConnectionStatus = mutableStateOf("Local: Disconnected")

    // Services
    private var remoteService: IEventServiceB? = null
    private var localService: IEventServiceA? = null

    // AIDL callback for receiving events
    private val callback = object : IEventCallbackA.Stub() {
        override fun onEvent(sender: String?, message: String?) {
            Log.d(TAG, "onEvent received: sender=$sender, message=$message")
            runOnUiThread {
                addEvent("From: ${sender ?: "unknown"} - ${message ?: ""}")
            }
        }
    }

    // Remote service connection (App B)
    private val remoteConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Remote service connected: $name")
            remoteService = IEventServiceB.Stub.asInterface(service)
            updateConnectionStatus(true)
            addEvent("✓ Connected to App B")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Remote service disconnected: $name")
            remoteService = null
            updateConnectionStatus(false)
            addEvent("✗ Disconnected from App B")
        }
    }

    // Local service connection (own service)
    private val localConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Local service connected: $name")
            localService = IEventServiceA.Stub.asInterface(service)
            registerLocalCallback()
            updateLocalConnectionStatus(true)
            addEvent("✓ Local service ready")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Local service disconnected: $name")
            localService = null
            updateLocalConnectionStatus(false)
            addEvent("✗ Local service disconnected")
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
        val intent = Intent(this, EventServiceA::class.java)
        bindService(intent, localConnection, Context.BIND_AUTO_CREATE)
    }

    private fun registerLocalCallback() {
        try {
            localService?.registerCallback(callback)
            Log.d(TAG, "Callback registered with local service")
        } catch (ex: RemoteException) {
            Log.e(TAG, "Failed to register callback", ex)
            addEvent("ERROR: Callback registration failed")
        }
    }

    private fun setupUI() {
        setContent {
            AppTheme {
                MainScreen(
                    appName = "App A",
                    targetAppName = "App B",
                    connectionStatus = connectionStatus.value,
                    localConnectionStatus = localConnectionStatus.value,
                    events = events,
                    onSendClick = ::sendEventToRemote
                )
            }
        }
    }

    private fun sendEventToRemote() {
        val message = "Hello from App A"

        if (remoteService == null) {
            Log.e(TAG, "Cannot send: not connected to App B")
            addEvent("ERROR: Not connected to App B")
            return
        }

        try {
            Log.d(TAG, "Sending event: $message")
            remoteService?.sendEvent(packageName, message)
            addEvent("→ Sent: $message")
        } catch (ex: RemoteException) {
            Log.e(TAG, "Failed to send event", ex)
            addEvent("ERROR: Send failed - ${ex.message}")
        }
    }

    private fun addEvent(event: String) {
        events.add(0, event)
    }

    private fun updateConnectionStatus(connected: Boolean) {
        connectionStatus.value = if (connected) "Connected" else "Disconnected"
    }

    private fun updateLocalConnectionStatus(connected: Boolean) {
        localConnectionStatus.value = if (connected) "Local: Connected" else "Local: Disconnected"
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        unregisterLocalCallback()
        unbindServices()
    }

    private fun unregisterLocalCallback() {
        try {
            localService?.unregisterCallback(callback)
        } catch (ex: Exception) {
            Log.w(TAG, "Error unregistering callback", ex)
        }
    }

    private fun unbindServices() {
        try {
            unbindService(remoteConnection)
        } catch (ex: Exception) {
            Log.w(TAG, "Error unbinding remote service", ex)
        }

        try {
            unbindService(localConnection)
        } catch (ex: Exception) {
            Log.w(TAG, "Error unbinding local service", ex)
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}

@Composable
private fun MainScreen(
    appName: String,
    targetAppName: String,
    connectionStatus: String,
    localConnectionStatus: String,
    events: List<String>,
    onSendClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        AppHeader(
            appName = appName,
            connectionStatus = connectionStatus,
            localConnectionStatus = localConnectionStatus
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Send button
        SendButton(
            targetAppName = targetAppName,
            onClick = onSendClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(8.dp))

        // Events list
        EventsList(events = events)
    }
}

@Composable
private fun AppHeader(
    appName: String,
    connectionStatus: String,
    localConnectionStatus: String
) {
    Column {
        Text(
            text = appName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Status: $connectionStatus | $localConnectionStatus",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SendButton(
    targetAppName: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Send to $targetAppName")
    }
}

@Composable
private fun EventsList(events: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(events) { event ->
            EventItem(event = event)
        }
    }
}

@Composable
private fun EventItem(event: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = event,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}
