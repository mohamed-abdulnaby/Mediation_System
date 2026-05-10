package org.telecom.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteFileService extends Remote {
    void receiveFile(String filename, byte[] data) throws RemoteException;
}