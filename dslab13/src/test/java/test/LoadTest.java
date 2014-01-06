package test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import proxy.IProxyCli;

import cli.Shell;
import client.IClientCli;
import util.ComponentFactory;
import util.Config;
import util.NullOutputStream;

public class LoadTest
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
			ProxyThreadTest proxy = new ProxyThreadTest(factory.startProxy(new Config("proxy"), new Shell("proxy", NullOutputStream.getInstance(), new ByteArrayInputStream("".getBytes()))));
			Thread t = new Thread(proxy);
			t.start();
		} 
		catch (Exception e1)
		{
			e1.printStackTrace();
		}

		System.out.println(clients);
		for(int i = 0; i < clients; i++)
		{
			try
			{
				ClientThreadTest cT = new ClientThreadTest(factory.startClient(new Config("client"), new Shell("client "+i, NullOutputStream.getInstance(), new ByteArrayInputStream("".getBytes()))));
				Thread t = new Thread(cT);
				t.start();
			} 
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public static class ProxyThreadTest implements Runnable
	{
		private IProxyCli proxy;
		
		public ProxyThreadTest(IProxyCli proxy)
		{
			this.proxy = proxy;
		}
		
		@Override
		public void run()
		{
			long start = System.currentTimeMillis();
			while(System.currentTimeMillis()-start < 10000)
			{
				try
				{
					System.out.println("Proxy alive");
					Thread.sleep(1000);
				} 
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			System.out.println("Proxy done, gov'ner");
			try
			{
				proxy.exit();
				//System.exit(0);
			} 
			catch (IOException e)
			{
				e.printStackTrace();
			}
			
		}

	}

	static class ClientThreadTest implements Runnable
	{
		private IClientCli cli;

		public ClientThreadTest(IClientCli cli)
		{
			this.cli = cli;
		}

		@Override
		public void run()
		{
			long start = System.currentTimeMillis();
			while(System.currentTimeMillis()-start < 5000)
			{
				try
				{
					cli.login("alice", "12345");
					cli.credits();
					cli.list();
					cli.logout();
				} 
				catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try
				{
					Thread.sleep(1000);
				} 
				catch (InterruptedException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			System.out.println("Done, gov'ner");
			try
			{
				cli.exit();
			} 
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}

	}

}
