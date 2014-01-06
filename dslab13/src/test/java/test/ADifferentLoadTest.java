package test;

import java.io.IOException;

import proxy.IProxyCli;
import proxy.ProxyCli;
import test.LoadTest.ClientThreadTest;
import test.LoadTest.ProxyThreadTest;
import util.ComponentFactory;
import util.Config;
import cli.Shell;
import client.IClientCli;

public class ADifferentLoadTest
{

	private int clients;
	private int uploadsPerMin;
	private int downloadsPerMin;
	private int fileSizeKB;
	private int overwriteRatio;

	public static void main(String[] args)
	{
		Config config = new Config("loadtest");
		int clients = config.getInt("clients");
		int uploadsPerMin = config.getInt("uploadsPerMin");
		int downloadsPerMin = config.getInt("downloadsPerMin");
		int fileSizeKB = config.getInt("fileSizeKB");
		double overwriteRatio = Double.parseDouble(config.getString("overwriteRatio"));

		ComponentFactory factory = new ComponentFactory();

		try
		{
			IProxyCli proxy = factory.startProxy(new Config("proxy"), new Shell("proxy", System.out, System.in));
			IClientCli c1 = factory.startClient(new Config("client"), new Shell("client 1", System.out, System.in));
			IClientCli c2 = factory.startClient(new Config("client"), new Shell("client 2", System.out, System.in));
			IClientCli c3 = factory.startClient(new Config("client"), new Shell("client 3", System.out, System.in));
			IClientCli c4 = factory.startClient(new Config("client"), new Shell("client 4", System.out, System.in));
			
			c1.login("alice", "12345");
			c1.credits();
			c1.list();
			c1.logout();
			c1.exit();
			
			c2.login("alice", "12345");
			c2.credits();
			c2.list();
			c2.logout();
			c2.exit();
			
			c3.login("alice", "12345");
			c3.credits();
			c3.list();
			c3.logout();
			c3.exit();
			
			c4.login("alice", "12345");
			c4.credits();
			c4.list();
			c4.logout();
			c4.exit();
			
			proxy.exit();
		} 
		catch (Exception e1)
		{
			e1.printStackTrace();
		}
	}
}
