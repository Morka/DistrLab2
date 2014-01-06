package client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class CallbackObject extends UnicastRemoteObject  implements ICallbackObject{

	protected CallbackObject() throws RemoteException {
		super();
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -8179698343944553299L;

	@Override
	public void callback(String notificationMessage) throws RemoteException {

		System.out.println(notificationMessage);
	}

	
}
