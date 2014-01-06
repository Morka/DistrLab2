package proxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;

import client.ICallbackObject;

import util.Config;
import util.HmacHelper;

import message.Response;
import message.request.ListRequest;
import message.request.PublicKeySetRequest;
import message.response.ListResponse;
import message.response.MessageResponse;
import message.response.PublicKeyMessageResponse;
import model.FileServerInfo;

public class ProxyRMI implements IProxyRMI {

	private int readQ;
	private int writeQ;
	
	private ArrayList<CallbackProperties> callbackList;
	
	public ProxyRMI(){
		this.callbackList = new ArrayList<CallbackProperties>();
		
	}
	
	public void processDownloadCounterIncrease(String filename, int downloadCounter) throws RemoteException{
		for(CallbackProperties call : callbackList){
			if(call.getFilename().equals(filename)){
				if(call.getNumberOfDownlaods() == downloadCounter){
					call.getCallback().callback("Notification: " + call.getFilename() + " got downloaded " + call.getNumberOfDownlaods() + " times!");
				}
			}
		}
	}

	@Override
	public void setQuorums(int readQ, int writeQ) throws RemoteException {
		this.readQ = readQ;
		this.writeQ = writeQ;
	}

	@Override
	public MessageResponse readQuorum() throws RemoteException {
		return new MessageResponse("Read-Quorum is set to " + this.readQ);
	}

	@Override
	public MessageResponse writeQuorum() throws RemoteException {
		return new MessageResponse("Write-Quorum is set to " + this.writeQ);
	}

	@Override
	public MessageResponse topThreeDownloads() throws RemoteException {
		HashSet<String> files;

		try {
			files = this.list();
		} catch (IOException e) {
			throw new RemoteException(e.getMessage());
		}

		if (files == null) {
			new MessageResponse("No files available");
		} else {
			for (String f : files) {
				if (!Proxy.downloadCounter.containsKey(f)) {
					Proxy.downloadCounter.put(f, 0);
				}
			}
		}

		int third = 0, second = 0, first = 0;
		String thirdFile = "", secondFile = "", firstFile = "";

		for (String fileName : Proxy.downloadCounter.keySet()) {
			int i = Proxy.downloadCounter.get(fileName);
			if (i >= third) {
				if (i >= second) {
					if (i >= first) {
						int tmp = first;
						String tmpFile = firstFile;

						first = i;
						firstFile = fileName;

						int tmp2 = second;
						String tmpFile2 = secondFile;

						second = tmp;
						secondFile = tmpFile;

						third = tmp2;
						thirdFile = tmpFile2;
					} else {
						int tmp = second;
						String tmpFile = secondFile;

						second = i;
						secondFile = fileName;

						third = tmp;
						thirdFile = tmpFile;
					}
				} else {
					third = i;
					thirdFile = fileName;
				}
			}
		}

		return new MessageResponse("Top Three Downloads:\n" + "1. " + firstFile
				+ " " + first + "\n2. " + secondFile + " " + second + "\n3. "
				+ thirdFile + " " + third);
	}

	private HashSet<String> list() throws IOException {
		ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier = ServerData.getInstance().servers;
		HmacHelper hMac = new HmacHelper(new Config("proxy"));

		synchronized (serverIdentifier) {
			if (serverIdentifier.isEmpty()) {
				return null;
			} else {
				HashSet<String> files = new HashSet<String>();
				for (FileServerInfo i : serverIdentifier.values()) {
					if (i.isOnline()) {
						Socket socket = new Socket(i.getAddress(), i.getPort());
						ObjectOutputStream oos = new ObjectOutputStream(
								socket.getOutputStream());
						oos.flush();
						ObjectInputStream ois = new ObjectInputStream(
								socket.getInputStream());
						ListRequest listRequest = new ListRequest(
								hMac.createHash("!list"));

						oos.writeObject(listRequest);
						oos.flush();
						try {
							Response response = (Response) ois.readObject();
							if (response instanceof ListResponse) {
								ListResponse listResponse = (ListResponse) response;
								if (!hMac.verifyHash(listResponse.gethMac(),
										listResponse.toString())) {
									System.out
											.println("This message has been tampered with: "
													+ listResponse.toString());
								}
								oos.close();
								ois.close();
								socket.close();
								files.addAll(listResponse.getFileNames());
							}
							if (response instanceof MessageResponse) {
								if (((MessageResponse) response).getMessage()
										.equals("!again")) {
									return list();
								}
							}
						} catch (ClassNotFoundException e) {}
					}
				}
				return files;
			}
		}
	}

	@Override
	public MessageResponse subscribe(String filename, int numberOfDownloads, ICallbackObject callbackObject)
			throws RemoteException {

		this.callbackList.add(new CallbackProperties(filename, numberOfDownloads, callbackObject));
			
		return new MessageResponse("Successfully subscribed for file: " + filename);
	}

	@Override
	public PublicKeyMessageResponse getProxyPublicKey() throws RemoteException {

		return new PublicKeyMessageResponse(importPublicKey());
	}

	private PublicKey importPublicKey() {
		Config config = new Config("proxy");
		String pathToPublicKey = config.getString("publicKey");

		PublicKey publicKey = null;
		PEMReader in = null;
		try {
			in = new PEMReader(new FileReader(pathToPublicKey));
			publicKey = (PublicKey) in.readObject();
		} catch (FileNotFoundException ex) {
			System.err.println("ERROR: PublicKey File not found");
		} catch (IOException ex) {
			System.err.println("ERROR: in.readObject() not possible");

		}

		try {
			in.close();
		} catch (IOException e) {
			System.err.println("ERROR 'in' could'nt be closed");
		}

		return publicKey;
	}
	
	public void unsubscribe() throws RemoteException{
		int i = 0;
		
		while(!this.callbackList.isEmpty()){
			this.callbackList.remove(i);
			i++;
		}
	}

	@Override
	public MessageResponse setUserPublicKey(String username,
			PublicKeySetRequest publicKey) throws RemoteException {
		Config config = new Config("proxy");

		PEMWriter write;
		try {
			write = new PEMWriter(new PrintWriter(new File(
					config.getString("keys.dir"), username + ".pub.pem")));

			write.writeObject(publicKey.getPublicKey());
			write.close();
		} catch (FileNotFoundException e) {
			throw new RemoteException(e.getMessage());
		} catch (IOException e) {
			throw new RemoteException(e.getMessage());
		}

		return new MessageResponse("Successfully transmitted public key of user: " + username + ".");
	}

}
