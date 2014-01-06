package proxy;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import model.UserInfo;

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
	
	private IProxyRMI proxyRMI;
	
	private ConcurrentHashMap<String, UserInfo> users; 
	
	public ProxyOverseer(Config config, ServerSocket serverSocket, AtomicBoolean stop, IProxyRMI proxyRMI)
	{
		this.proxyRMI = proxyRMI;
		this.config = config;
		this.users = UserData.getInstance().users;
		this.stop = stop;
		this.serverSocket = serverSocket;
		pool = Executors.newFixedThreadPool(50);
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
				Proxy proxy = new Proxy(clientSocket, config, stop, this.proxyRMI);
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
						System.err.println("Error closing Client Socket");
					}
				}
				return;
			}
			catch (IOException e)
			{
				System.err.println("Error threadpool shutdown");
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
			Config uConfig = new Config("user");
			MyConfig myConfig = new MyConfig("user");
			Enumeration<String> userNames = myConfig.getKeys();
			while(userNames.hasMoreElements())
			{
				String userName = userNames.nextElement().split("\\.")[0];
				if(users.containsKey(userName))
				{
					continue;
				}
				/*ArrayList<String> values = new ArrayList<String>();
				values.add(config.getString(userName + ".password"));
				values.add(config.getString(userName + ".credits"));
				values.add("offline");*/
				Long credits = Long.valueOf(uConfig.getString(userName + ".credits"));
				UserInfo userinfo = new UserInfo(userName,credits, false);
				users.put(userName, userinfo);
			}
			pool.execute(new FileServerListener(datagramSocket, timeout, checkPeriod, stop));
		} 
		catch (IOException e)
		{
			System.err.println("Error creating new Fileserver Listener");
		}
	}
}