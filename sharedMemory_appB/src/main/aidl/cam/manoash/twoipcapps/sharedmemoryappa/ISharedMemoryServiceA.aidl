package cam.manoash.twoipcapps.sharedmemoryappa;

import android.os.ParcelFileDescriptor;
import cam.manoash.twoipcapps.sharedmemoryappa.IMemoryCallbackA;

interface ISharedMemoryServiceA {
    ParcelFileDescriptor getSharedMemory();
    void notifyDataChanged();
    void registerCallback(in IMemoryCallbackA cb);
    void unregisterCallback(in IMemoryCallbackA cb);

    // Remote read/write helpers
    String readData();
    void writeData(String data);
}
