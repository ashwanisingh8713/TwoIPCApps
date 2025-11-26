package cam.manoash.twoipcapps.sharedmemoryappa

import android.os.MemoryFile
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileDescriptor
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Helper class for managing shared memory operations
 * Uses MemoryFile (ashmem) and reflection to obtain/restore file descriptors across processes
 */
class SharedMemoryHelper {
    companion object {
        private const val TAG = "SharedMemoryHelper"
        private const val MEMORY_SIZE = 4096 // 4KB shared memory
    }

    private var memoryFile: MemoryFile? = null

    /**
     * Create or return the existing shared memory region and duplicate its FD for IPC.
     */
    fun createSharedMemory(): ParcelFileDescriptor? {
        try {
            if (memoryFile == null) {
                memoryFile = MemoryFile("two_ipc_shared_a", MEMORY_SIZE)
            }

            // try MemoryFile.getFileDescriptor() then fallback to mFD
            val fd: FileDescriptor? = try {
                val m: Method = MemoryFile::class.java.getMethod("getFileDescriptor")
                m.isAccessible = true
                m.invoke(memoryFile) as FileDescriptor
            } catch (e: Exception) {
                try {
                    val f = MemoryFile::class.java.getDeclaredField("mFD")
                    f.isAccessible = true
                    f.get(memoryFile) as FileDescriptor
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to get fd from MemoryFile", ex)
                    null
                }
            }

            return fd?.let { ParcelFileDescriptor.dup(it) }
        } catch (e: Exception) {
            Log.e(TAG, "createSharedMemory failed", e)
            return null
        }
    }

    /**
     * Write data to shared memory
     */
    fun writeToMemory(data: String): Boolean {
        try {
            val mem = memoryFile ?: run {
                Log.e(TAG, "Memory file not initialized")
                return false
            }
            val bytes = data.toByteArray(StandardCharsets.UTF_8)
            val buf = ByteBuffer.allocate(4 + bytes.size)
            buf.putInt(bytes.size)
            buf.put(bytes)
            mem.writeBytes(buf.array(), 0, 0, buf.position())
            return true
        } catch (e: Exception) {
            Log.e(TAG, "writeToMemory failed", e)
            return false
        }
    }

    /**
     * Read data from shared memory
     */
    fun readFromMemory(): String? {
        try {
            val mem = memoryFile ?: run {
                Log.e(TAG, "Memory file not initialized")
                return null
            }
            // Read the whole memory region into a temp buffer to avoid signature/order differences
            val temp = ByteArray(MEMORY_SIZE)
            mem.readBytes(temp, 0, 0, MEMORY_SIZE)
            val len = ByteBuffer.wrap(temp, 0, 4).int
            if (len <= 0 || len > MEMORY_SIZE - 4) {
                Log.w(TAG, "Invalid data length: $len")
                return null
            }
            val data = temp.copyOfRange(4, 4 + len)
            return String(data, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "readFromMemory failed", e)
            return null
        }
    }

    /**
     * Close shared memory
     */
    fun close() {
        try {
            memoryFile?.close()
            memoryFile = null
        } catch (e: Exception) {
            Log.e(TAG, "close failed", e)
        }
    }
}
