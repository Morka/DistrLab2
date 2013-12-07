package proxy;

import java.util.concurrent.ConcurrentHashMap;

import model.FileServerInfo;

public class ServerData
{
	public ConcurrentHashMap<Integer, FileServerInfo> servers; 
	private static ServerData instance;
	
	private ServerData()
	{
		servers = new ConcurrentHashMap<Integer, FileServerInfo>();
	}
	
	public static synchronized ServerData getInstance()
	{
		if (instance == null)
		{
			instance = new ServerData();
		}
		return instance;
	}
}