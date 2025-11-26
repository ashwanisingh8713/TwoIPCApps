package cam.manoash.twoipcapps.sharedmemoryappa

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import android.util.Log

/**
 * Service that manages shared memory for App A
 * Provides shared memory access to remote processes
 */
class SharedMemoryServiceA : Service() {

    companion object {
        private const val TAG = "SharedMemoryServiceA"
    }

    private val sharedMemoryHelper = SharedMemoryHelper()
    private val callbacks = RemoteCallbackList<IMemoryCallbackA>()

    inner class LocalBinder : Binder() {
        fun getService(): SharedMemoryServiceA = this@SharedMemoryServiceA
    }

    private val localBinder = LocalBinder()

    private val remoteBinder = object : ISharedMemoryServiceA.Stub() {
        override fun getSharedMemory(): ParcelFileDescriptor? {
            Log.d(TAG, "getSharedMemory() called")
            return sharedMemoryHelper.createSharedMemory()
        }

        override fun notifyDataChanged() {
            Log.d(TAG, "notifyDataChanged() called")
            broadcastDataChanged()
        }

        override fun registerCallback(cb: IMemoryCallbackA?) {
            if (cb != null) {
                callbacks.register(cb)
                Log.d(TAG, "Remote callback registered")
            }
        }

        override fun unregisterCallback(cb: IMemoryCallbackA?) {
            if (cb != null) {
                callbacks.unregister(cb)
                Log.d(TAG, "Remote callback unregistered")
            }
        }

        override fun readData(): String? {
            return sharedMemoryHelper.readFromMemory()
        }

        override fun writeData(data: String?) {
            if (data == null) return
            if (sharedMemoryHelper.writeToMemory(data)) {
                broadcastDataChanged()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Initialize shared memory
        sharedMemoryHelper.createSharedMemory()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound: ${intent?.action}")

        // Return local binder for same-process, remote binder for cross-process
        return if (intent?.action == null) {
            localBinder
        } else {
            remoteBinder
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        callbacks.kill()
        sharedMemoryHelper.close()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val action = intent?.action
            if (action == "cam.manoash.twoipcapps.ACTION_SEND_TEST") {
                val msg = intent.getStringExtra("message") ?: "Test from ServiceA"
                writeData(msg)
                Log.d(TAG, "onStartCommand: wrote test message via intent: $msg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand failed", e)
        }
        return START_NOT_STICKY
    }

    /**
     * Write data to shared memory
     */
    @Suppress("unused")
    fun writeData(data: String) {
        if (sharedMemoryHelper.writeToMemory(data)) {
            broadcastDataChanged()
        }
    }

    /**
     * Read data from shared memory
     */
    fun readData(): String? {
        return sharedMemoryHelper.readFromMemory()
    }

    /**
     * Register callback for data change notifications
     */
    fun registerCallback(callback: IMemoryCallbackA) {
        callbacks.register(callback)
        Log.d(TAG, "Callback registered")
    }

    /**
     * Unregister callback
     */
    fun unregisterCallback(callback: IMemoryCallbackA) {
        callbacks.unregister(callback)
        Log.d(TAG, "Callback unregistered")
    }

    /**
     * Broadcast data change to all registered callbacks
     */
    private fun broadcastDataChanged() {
        val count = callbacks.beginBroadcast()
        Log.d(TAG, "Broadcasting data change to $count callbacks")

        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i)?.onDataChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify callback $i", e)
            }
        }

        callbacks.finishBroadcast()
    }
}
