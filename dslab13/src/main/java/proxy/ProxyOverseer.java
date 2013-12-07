package proxy;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import model.FileServerInfo;

import cli.Shell;

import util.Config;
import util.MyConfig;

public class ProxyOverseer implements Runnable
{
	private Config config;
	private ExecutorService pool;
	private ServerSocket serverSocket;
	private DatagramSocket datagramSocket;
	private int timeout;
	private int checkPeriod;
	private AtomicBoolean stop;
	
	/*
	 * username is the key, values (in that order) are password, credits, online status
	 */
	private ConcurrentHashMap<String, ArrayList<String>> users; 

	private ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier;

	private HashSet<String> files;
	
	public ProxyOverseer(Config config, ServerSocket serverSocket, ConcurrentHashMap<String, ArrayList<String>> users, ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier, AtomicBoolean stop)
	{
		this.config = config;
		this.users = users;
		this.stop = stop;
		this.serverSocket = serverSocket;
		files = new HashSet<String>();
		pool = Executors.newFixedThreadPool(10);
		this.serverIdentifier = serverIdentifier;
	}
	
	@Override
	public void run()
	{
		ArrayList<Socket> sockets = new ArrayList<Socket>();
		init();
		while(!stop.get())
		{
			try
			{
				Socket clientSocket = serverSocket.accept();
				Proxy proxy = new Proxy(clientSocket, users, files, serverIdentifier, stop);
				sockets.add(clientSocket);
				pool.execute(proxy);
			} 
			catch(SocketException se)
			{
				pool.shutdownNow();
				datagramSocket.close();
				for(Socket s: sockets)
				{
					try
					{
						s.close();
					} 
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
				return;
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private void init()
	{
		try
		{
			datagramSocket = new DatagramSocket(config.getInt("udp.port"));
			timeout = config.getInt("fileserver.timeout");
			checkPeriod = config.getInt("fileserver.checkPeriod");
			config = new Config("user");
			MyConfig myConfig = new MyConfig("user");
			Enumeration<String> userNames = myConfig.getKeys();
			while(userNames.hasMoreElements())
			{
				String userName = userNames.nextElement().split("\\.")[0];
				if(users.containsKey(userName))
				{
					continue;
				}
				ArrayList<String> values = new ArrayList<String>();
				values.add(config.getString(userName + ".password"));
				values.add(config.getString(userName + ".credits"));
				values.add("offline");
				users.put(userName, values);
			}
			pool.execute(new FileServerListener(datagramSocket, timeout, checkPeriod, stop, files, serverIdentifier));
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}