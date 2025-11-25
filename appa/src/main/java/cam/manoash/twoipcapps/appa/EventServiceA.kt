package cam.manoash.twoipcapps.appa

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import cam.manoash.twoipcapps.appa.ipc.IEventCallbackA
import cam.manoash.twoipcapps.appa.ipc.IEventServiceA

/**
 * AIDL-based Event Service for App A
 *
 * This service handles IPC communication, allowing:
 * - Registration/unregistration of event callbacks
 * - Broadcasting events to all registered callbacks
 * - Cross-process communication with other apps
 */
class EventServiceA : Service() {

    companion object {
        private const val TAG = "EventServiceAppA"
    }

    /**
     * Thread-safe list of registered callbacks
     * RemoteCallbackList automatically handles:
     * - Thread safety
     * - Death recipient registration
     * - Cleanup of dead callbacks
     */
    private val callbacks = RemoteCallbackList<IEventCallbackA>()

    /**
     * AIDL implementation
     */
    private val binder = object : IEventServiceA.Stub() {

        override fun registerCallback(cb: IEventCallbackA?) {
            if (cb == null) {
                Log.w(TAG, "Attempted to register null callback")
                return
            }

            val success = callbacks.register(cb)
            if (success) {
                Log.d(TAG, "Callback registered: $cb (Total: ${callbacks.registeredCallbackCount})")
            } else {
                Log.w(TAG, "Failed to register callback: $cb")
            }
        }

        override fun unregisterCallback(cb: IEventCallbackA?) {
            if (cb == null) {
                Log.w(TAG, "Attempted to unregister null callback")
                return
            }

            val success = callbacks.unregister(cb)
            if (success) {
                Log.d(TAG, "Callback unregistered: $cb (Remaining: ${callbacks.registeredCallbackCount})")
            } else {
                Log.w(TAG, "Failed to unregister callback: $cb")
            }
        }

        override fun sendEvent(sender: String?, message: String?) {
            if (sender == null || message == null) {
                Log.w(TAG, "Received event with null sender or message")
                return
            }

            Log.d(TAG, "Broadcasting event: from=$sender, message=$message")
            broadcastEvent(sender, message)
        }
    }

    /**
     * Broadcasts an event to all registered callbacks
     */
    private fun broadcastEvent(sender: String, message: String) {
        val callbackCount = callbacks.beginBroadcast()
        Log.d(TAG, "Broadcasting to $callbackCount callback(s)")

        try {
            for (i in 0 until callbackCount) {
                try {
                    val callback = callbacks.getBroadcastItem(i)
                    callback.onEvent(sender, message)
                } catch (ex: RemoteException) {
                    Log.e(TAG, "Failed to deliver event to callback $i", ex)
                    // RemoteCallbackList will automatically remove dead callbacks
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }

        Log.d(TAG, "Broadcast complete")
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound: intent=$intent")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service unbound: intent=$intent")
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroying (Registered callbacks: ${callbacks.registeredCallbackCount})")

        // Clean up all callbacks
        callbacks.kill()

        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}
