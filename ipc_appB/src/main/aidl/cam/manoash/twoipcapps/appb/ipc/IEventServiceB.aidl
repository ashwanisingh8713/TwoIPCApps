package cam.manoash.twoipcapps.appb.ipc;

import cam.manoash.twoipcapps.appb.ipc.IEventCallbackB;

interface IEventServiceB {
    void registerCallback(in IEventCallbackB cb);
    void unregisterCallback(in IEventCallbackB cb);
    void sendEvent(String sender, String message);
}

