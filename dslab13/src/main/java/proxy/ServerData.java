package proxy;

import java.util.concurrent.ConcurrentHashMap;

import model.FileServerInfo;

public class ServerData
{
	public static ConcurrentHashMap<Integer, FileServerInfo> servers; 
	private static ServerData instance;
	
	public static synchronized ServerData getInstance()
	{
		if (instance == null)
		{
			instance = new ServerData();
		}
		return instance;
	}
}