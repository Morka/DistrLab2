package server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import proxy.FileServerListener;

import util.Config;
import util.HmacHelper;
import util.MyConfig;

public class FileServerOverseer implements Runnable
{
	private AtomicBoolean stop;
	private Config config;
	private ServerSocket serverSocket;
	private DatagramSocket datagramSocket;
	private ExecutorService pool;
	private int isAlive;
	private int proxyUdp;
	private String directory;
	private int tcpPort;
	private HashMap<String,Integer> files;

	public FileServerOverseer(Config config, ServerSocket serverSocket, DatagramSocket datagramSocket, AtomicBoolean stop)
	{
		this.config = config;
		this.serverSocket = serverSocket;
		this.datagramSocket = datagramSocket;
		this.stop = stop;
		files = new HashMap<String,Integer>();
		pool = Executors.newFixedThreadPool(20);
	}

	@Override
	public void run()
	{
		init();
		while(!stop.get())
		{
			try
			{
				pool.execute(new FileServer(files, serverSocket.accept(), directory, config, stop));
			}
			catch(SocketException se)
			{
				pool.shutdownNow();
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
			datagramSocket = new DatagramSocket(config.getInt("tcp.port"));
			isAlive = config.getInt("fileserver.alive");
			directory = config.getString("fileserver.dir");
			tcpPort = config.getInt("tcp.port");
			proxyUdp = config.getInt("proxy.udp.port");
			pool.execute(new IsAliveSender(datagramSocket, isAlive, tcpPort, proxyUdp, stop));
			
			File folder = new File(directory);
			File[] listOfFiles = folder.listFiles();
			for (File file : listOfFiles) 
			{
			    files.put(file.getName(),0);
			}
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
