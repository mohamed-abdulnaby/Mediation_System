package org.telecom.common;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public abstract class AbstractRemoteFileService extends UnicastRemoteObject implements RemoteFileService {
    private static final long serialVersionUID = 1L;

    protected AbstractRemoteFileService() throws RemoteException {
        super();
    }

    @Override
    public void receiveFile(String filename, byte[] data) throws RemoteException {
        onFileReceived(filename, data);
    }

    protected abstract void onFileReceived(String filename, byte[] data);
}