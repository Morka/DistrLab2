package proxy;

import java.rmi.Remote;
import java.rmi.RemoteException;

import message.request.PublicKeySetRequest;
import message.response.MessageResponse;
import message.response.PublicKeyMessageResponse;

public interface IProxyRMI extends Remote{
	
	public void setQuorums(int readQ, int writeQ) throws RemoteException;
	
	public MessageResponse readQuorum() throws RemoteException;
	
	public MessageResponse writeQuorum() throws RemoteException;
	
	public MessageResponse topThreeDownloads() throws RemoteException;

	public MessageResponse subscribe(String filename, int numberOfDownloads) throws RemoteException;

	public PublicKeyMessageResponse getProxyPublicKey() throws RemoteException;

	public MessageResponse setUserPublicKey(String username, PublicKeySetRequest publicKey) throws RemoteException;


}
