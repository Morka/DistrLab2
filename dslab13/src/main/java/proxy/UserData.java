package proxy;

import java.util.concurrent.ConcurrentHashMap;

import model.UserInfo;

public class UserData
{
	public ConcurrentHashMap<String, UserInfo> users; 
	private static UserData instance;
	
	private UserData()
	{
		users = new ConcurrentHashMap<String, UserInfo>();
	}
	
	public static synchronized UserData getInstance() 
	{
	    if(instance == null) 
	    {
	        instance = new UserData();
	    }
	    return instance;
	}
	
	/*public synchronized void setUsers(ConcurrentHashMap<String, UserInfo> usersList) {
		users = usersList;		
	}*/
}