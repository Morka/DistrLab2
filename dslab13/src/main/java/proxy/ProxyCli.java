package proxy;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;

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

	public ProxyCli(Config config, Shell shell)
	{
		readPrivateKey("keys/proxy.pem");
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
		overseer = new ProxyOverseer(config, serverSocket, stop);
		t = new Thread(overseer);
		t.start();
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
				/*text += entry.getKey()+" ";
				for (int i = 1; i < entry.getValue().size(); i++) // skips password
				{
					text += entry.getValue().get(i) + " ";
				}
				text += "\n";*/
			}
		}
		return new MessageResponse(text);
	}

	@Override
	@Command
	public
	MessageResponse exit() throws IOException
	{
		stop.set(true);
		shell.writeLine("Exiting...");
		shell.close();
		serverSocket.close();
		System.in.close();
		System.out.close();
		return null;
	}
	
	private static void readPrivateKey(String pathToPrivateKey){
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
			ex.printStackTrace();
			System.err.println("ERROR: reading Private Key - most likely wrong password");
		}
	}
}
