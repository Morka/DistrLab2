package proxy;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import util.Config;
import util.MyConfig;

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
}
