package proxy;

import java.util.concurrent.ConcurrentHashMap;

import model.UserInfo;

public class UserData
{
	public static ConcurrentHashMap<String, UserInfo> users; 
	private static UserData instance;
	
	public static synchronized UserData getInstance() 
	{
	    if(instance == null) 
	    {
	        instance = new UserData();
	    }
	    return instance;
	}
}