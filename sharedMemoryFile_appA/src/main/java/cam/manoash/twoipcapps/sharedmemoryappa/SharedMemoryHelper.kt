package cam.manoash.twoipcapps.sharedmemoryappa

import android.os.Build
import android.os.MemoryFile
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileDescriptor
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    // Reflection-backed SharedMemory holder (when available at runtime)
    private var sharedMemoryObj: Any? = null
    private var sharedMemoryClass: Class<*>? = null

    private var memoryFile: MemoryFile? = null

    private fun ensureSharedMemoryClass(): Class<*>? {
        if (sharedMemoryClass != null) return sharedMemoryClass
        return try {
            val cls = Class.forName("android.os.SharedMemory")
            sharedMemoryClass = cls
            cls
        } catch (e: Exception) {
            // not available on this compile/runtime
            null
        }
    }

    /**
     * Helper to obtain a FileDescriptor from a MemoryFile via reflection.
     */
    private fun getFdFromMemoryFile(mf: MemoryFile): FileDescriptor? {
        try {
            return try {
                val m: Method = MemoryFile::class.java.getMethod("getFileDescriptor")
                m.isAccessible = true
                m.invoke(mf) as FileDescriptor
            } catch (e: Exception) {
                val f: Field = MemoryFile::class.java.getDeclaredField("mFD")
                f.isAccessible = true
                f.get(mf) as FileDescriptor
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFdFromMemoryFile failed", e)
            return null
        }
    }

    /**
     * Create or return the existing shared memory region and duplicate its FD for IPC.
     */
    fun createSharedMemory(): ParcelFileDescriptor? {
        try {
            val cls = ensureSharedMemoryClass()
            if (cls != null) {
                try {
                    // SharedMemory.create(String, int)
                    val createM: Method = cls.getMethod("create", String::class.java, Int::class.javaPrimitiveType)
                    sharedMemoryObj = createM.invoke(null, "two_ipc_shared_a", MEMORY_SIZE)
                    // getFileDescriptor()
                    val getFd: Method = cls.getMethod("getFileDescriptor")
                    val fd = getFd.invoke(sharedMemoryObj) as FileDescriptor
                    return ParcelFileDescriptor.dup(fd)
                } catch (e: Exception) {
                    Log.w(TAG, "SharedMemory reflection create failed, falling back", e)
                    sharedMemoryObj = null
                }
            }

            if (memoryFile == null) {
                memoryFile = MemoryFile("two_ipc_shared_a", MEMORY_SIZE)
            }

            val fd = getFdFromMemoryFile(memoryFile!!)
            return fd?.let { ParcelFileDescriptor.dup(it) }
        } catch (e: Exception) {
            Log.e(TAG, "createSharedMemory failed", e)
            return null
        }
    }

    /**
     * Open existing shared memory from file descriptor (remote side).
     * Returns true on success.
     */
    fun openSharedMemory(pfd: ParcelFileDescriptor): Boolean {
        try {
            val cls = ensureSharedMemoryClass()
            if (cls != null) {
                try {
                    val openM: Method = cls.getMethod("open", FileDescriptor::class.java)
                    sharedMemoryObj = openM.invoke(null, pfd.fileDescriptor)
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "SharedMemory reflection open failed, falling back", e)
                    sharedMemoryObj = null
                }
            }

            // Try MemoryFile hidden ctor
            try {
                val ctor: Constructor<*> = MemoryFile::class.java.getDeclaredConstructor(FileDescriptor::class.java, Int::class.javaPrimitiveType, String::class.java)
                ctor.isAccessible = true
                memoryFile = ctor.newInstance(pfd.fileDescriptor, MEMORY_SIZE, "two_ipc_shared_a_remote") as MemoryFile
                return true
            } catch (e: Exception) {
                Log.e(TAG, "openSharedMemory: constructing MemoryFile from fd failed", e)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "openSharedMemory failed", e)
            return false
        }
    }

    /**
     * Write data to shared memory (length-prefixed).
     */
    fun writeToMemory(data: String): Boolean {
        try {
            val bytes = data.toByteArray(StandardCharsets.UTF_8)
            val cls = ensureSharedMemoryClass()
            if (cls != null && sharedMemoryObj != null) {
                try {
                    val mapM: Method = cls.getMethod("mapReadWrite", cls)
                    val buf = mapM.invoke(null, sharedMemoryObj) as ByteBuffer
                    try {
                        buf.order(ByteOrder.nativeOrder())
                        buf.position(0)
                        buf.putInt(bytes.size)
                        buf.put(bytes)
                    } finally {
                        val unmapM: Method = cls.getMethod("unmap", ByteBuffer::class.java)
                        unmapM.invoke(null, buf)
                    }
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "SharedMemory reflection write failed, falling back", e)
                }
            }

            val mf = memoryFile ?: run {
                Log.e(TAG, "Memory file not initialized")
                return false
            }

            val buf = ByteBuffer.allocate(4 + bytes.size).order(ByteOrder.nativeOrder())
            buf.putInt(bytes.size)
            buf.put(bytes)
            mf.writeBytes(buf.array(), 0, 0, buf.position())
            return true
        } catch (e: Exception) {
            Log.e(TAG, "writeToMemory failed", e)
            return false
        }
    }

    /**
     * Read data from shared memory (length-prefixed).
     */
    fun readFromMemory(): String? {
        try {
            val cls = ensureSharedMemoryClass()
            if (cls != null && sharedMemoryObj != null) {
                try {
                    val mapM: Method = cls.getMethod("mapReadOnly", cls)
                    val buf = mapM.invoke(null, sharedMemoryObj) as ByteBuffer
                    try {
                        buf.order(ByteOrder.nativeOrder())
                        if (buf.remaining() < 4) return null
                        buf.position(0)
                        val len = buf.int
                        if (len <= 0 || len > MEMORY_SIZE - 4) return null
                        val dst = ByteArray(len)
                        buf.get(dst)
                        return String(dst, StandardCharsets.UTF_8)
                    } finally {
                        val unmapM: Method = cls.getMethod("unmap", ByteBuffer::class.java)
                        unmapM.invoke(null, buf)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SharedMemory reflection read failed, falling back", e)
                }
            }

            val mf = memoryFile ?: run {
                Log.e(TAG, "Memory file not initialized")
                return null
            }

            val temp = ByteArray(MEMORY_SIZE)
            mf.readBytes(temp, 0, 0, MEMORY_SIZE)
            val len = ByteBuffer.wrap(temp, 0, 4).order(ByteOrder.nativeOrder()).int
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
     * Close shared memory resources
     */
    fun close() {
        try {
            try {
                // try to call close reflectively on SharedMemory if present
                val cls = sharedMemoryClass
                if (cls != null && sharedMemoryObj != null) {
                    try {
                        val closeM = cls.getMethod("close")
                        closeM.invoke(sharedMemoryObj)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            sharedMemoryObj = null
            sharedMemoryClass = null
            try { memoryFile?.close() } catch (_: Exception) {}
            memoryFile = null
        } catch (e: Exception) {
            Log.e(TAG, "close failed", e)
        }
    }
}
