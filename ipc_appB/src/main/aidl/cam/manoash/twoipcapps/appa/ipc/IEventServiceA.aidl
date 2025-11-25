package cam.manoash.twoipcapps.appa.ipc;

import cam.manoash.twoipcapps.appa.ipc.IEventCallbackA;

interface IEventServiceA {
    void registerCallback(in IEventCallbackA cb);
    void unregisterCallback(in IEventCallbackA cb);
    void sendEvent(String sender, String message);
}

