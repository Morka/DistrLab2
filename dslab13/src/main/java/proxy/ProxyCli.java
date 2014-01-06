package proxy;

import java.io.IOException;
import java.net.ServerSocket;

import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
	
	private ConcurrentHashMap<String, UserInfo> users; 

	private ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier;

	private String bindingName;
	private int proxyRmiPort;
	private Registry registry;
	
	private IProxyRMI proxyRMI;

	public ProxyCli(Config config, Shell shell)
	{
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
		shell.close();
		serverSocket.close();
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
		return null;
	}
}
