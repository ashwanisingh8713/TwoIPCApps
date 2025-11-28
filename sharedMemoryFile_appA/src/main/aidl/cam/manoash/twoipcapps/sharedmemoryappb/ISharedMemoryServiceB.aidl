package cam.manoash.twoipcapps.sharedmemoryappb;

import android.os.ParcelFileDescriptor;
import cam.manoash.twoipcapps.sharedmemoryappb.IMemoryCallbackB;

interface ISharedMemoryServiceB {
    ParcelFileDescriptor getSharedMemory();
    void notifyDataChanged();
    void registerCallback(in IMemoryCallbackB cb);
    void unregisterCallback(in IMemoryCallbackB cb);

    // Remote read/write helpers
    String readData();
    void writeData(String data);
}
