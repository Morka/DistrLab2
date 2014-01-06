package client;

import java.rmi.Remote;
import java.rmi.RemoteException;


public interface ICallbackObject extends Remote{
	
	public void callback(String notificationMessage) throws RemoteException;
	
}
