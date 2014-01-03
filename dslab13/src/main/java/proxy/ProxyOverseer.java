package proxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.JCEMac.SHA256;

import model.FileServerInfo;
import model.UserInfo;

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
	
	private IProxyRMI proxyRMI;
	
	private ConcurrentHashMap<String, UserInfo> users; 
	
	public ProxyOverseer(Config config, ServerSocket serverSocket, AtomicBoolean stop, IProxyRMI proxyRMI)
	{
		this.proxyRMI = proxyRMI;
		this.config = config;
		this.users = UserData.getInstance().users;
		this.stop = stop;
		this.serverSocket = serverSocket;
		pool = Executors.newFixedThreadPool(10);
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
			e.printStackTrace();
		}
	}
}