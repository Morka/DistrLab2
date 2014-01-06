package proxy;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;

import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;

import util.Config;

import message.Response;
import message.response.MessageResponse;
import model.FileServerInfo;
import model.UserInfo;
import cli.Command;
import cli.Shell;

public class ProxyCli implements IProxyCli
{
	private Shell shell;
	private ServerSocket serverSocket;
	private ProxyOverseer overseer;
	private AtomicBoolean stop = new AtomicBoolean(false);
	private Thread t; // overseer thread
	protected static PrivateKey privateKey;
	
	private ConcurrentHashMap<String, UserInfo> users; 

	private ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier;

	private String bindingName;
	private int proxyRmiPort;
	private Registry registry;
	
	private IProxyRMI proxyRMI;

	public ProxyCli(Config config, Shell shell)
	{
		readPrivateKey(config.getString("key"));
		
		this.shell = shell;
		users = UserData.getInstance().users;
		serverIdentifier = ServerData.getInstance().servers;
		try
		{
			serverSocket = new ServerSocket(config.getInt("tcp.port"));
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}


        Config mcConfig = new Config("mc");
        
        this.bindingName = mcConfig.getString("binding.name");
        this.proxyRmiPort = mcConfig.getInt("proxy.rmi.port");
        IProxyRMI stub = this.registerProxy();
        
        if(stub != null){
        	overseer = new ProxyOverseer(config, serverSocket, stop, stub);
			t = new Thread(overseer);
			t.start();
        }
        else{
        	try {
				this.exit();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
	}
	
	private void readPrivateKey(String pathToPrivateKey){
		PEMReader in = null;
		try {
			
			in = new PEMReader(new FileReader(pathToPrivateKey), new PasswordFinder() {
					@Override
					public char[] getPassword() {
						System.out.println("Enter pass phrase:");
						try {
							return new BufferedReader(new InputStreamReader(System.in)).readLine().toCharArray();
						} catch (IOException e) {
							System.err.println("ERROR: reading password for privateKey" );
							return null;
						}
					}
			});
		} catch (FileNotFoundException e) {
			System.err.println("ERROR: Couldnt read privateKey file");
		}
		try{
			KeyPair keyPair = (KeyPair) in.readObject(); 
			privateKey = keyPair.getPrivate();
			in.close();
		}catch(IOException ex){
			System.err.println("ERROR: reading Private Key - most likely wrong password");
			readPrivateKey(pathToPrivateKey);
		}
	}
	

    private IProxyRMI registerProxy(){
    	this.proxyRMI = new ProxyRMI();
    	IProxyRMI stub = null;
    	
    	try {
			stub = (IProxyRMI)UnicastRemoteObject.exportObject(this.proxyRMI, 0);
		} catch (RemoteException e) {
			System.err.println("Couldn't export proxyRMI");
			//e.printStackTrace();
			return null;
		}
    	    	
    	try {
			this.registry =  LocateRegistry.createRegistry(this.proxyRmiPort);
			this.registry.bind(this.bindingName, stub);
    	
    	} catch (RemoteException e) {
    		System.err.println(e.getMessage());
			e.printStackTrace();
			return null;
		} catch (AlreadyBoundException e) {
			System.err.println("Registry is already in use");
			return null;
		}
    	
    	return stub;
    }

	@Override
	@Command
	public
	Response fileservers() throws IOException
	{
		String text = "";
		for (Map.Entry<Integer, FileServerInfo> entry : serverIdentifier.entrySet()) 
		{
			text += entry.getValue().getAddress().getHostAddress()+" ";
			text += String.valueOf(entry.getKey())+" ";
			text += String.valueOf(entry.getValue().isOnline())+" ";
			text += String.valueOf(entry.getValue().getUsage())+" ";
			text += "\n";
		}

		return new MessageResponse(text);
	}

	@Override
	@Command
	public
	Response users() throws IOException
	{
		String text = "";
		synchronized(users)
		{
			for (Map.Entry<String, UserInfo> entry : users.entrySet()) 
			{
				text += entry.getValue().toString()+"\n";
				
			}
		}
		return new MessageResponse(text);
	}

	@Override
	@Command
	public MessageResponse exit() throws IOException
	{	
		stop.set(true);
		shell.writeLine("Exiting...");
		System.out.println("Proxy");
		shell.close();
		//System.out.println("shell is closed");
		serverSocket.close();
		System.out.println("serverSocket is closed");
		System.in.close();
	//	System.out.close();
		try{
			UnicastRemoteObject.unexportObject(this.proxyRMI, true);
		}catch(IOException ex){
			ex.printStackTrace();
		}
		
		try {
			this.registry.unbind(this.bindingName);
		} catch (NotBoundException e) {	}
		
		System.out.println("Proxy is closed");
		return null;
	}
}
