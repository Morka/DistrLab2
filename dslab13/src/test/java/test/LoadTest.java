package test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import proxy.IProxyCli;

import cli.Shell;
import client.IClientCli;
import server.IFileServerCli;
import util.ComponentFactory;
import util.Config;
import util.NullOutputStream;

public class LoadTest
{
	private static int clients;
	private static int uploadsPerMin;
	private static int downloadsPerMin;
	private static int fileSizeKB;
	private static double overwriteRatio;
	private static long clientDuration;
	private static long fileserverDuration;
	private static long proxyDuration;
	private static int count;

	public static void main(String[] args)
	{
		Config config = new Config("loadtest");
		clients = config.getInt("clients");
		uploadsPerMin = config.getInt("uploadsPerMin");
		downloadsPerMin = config.getInt("downloadsPerMin");
		fileSizeKB = config.getInt("fileSizeKB");
		overwriteRatio = Double.parseDouble(config.getString("overwriteRatio"));
		clientDuration = config.getInt("clientDuration");
		fileserverDuration = config.getInt("fileserverDuration");
		proxyDuration = config.getInt("proxyDuration");

		ComponentFactory factory = new ComponentFactory();

		try
		{
			ProxyThreadTest proxy = new ProxyThreadTest(factory.startProxy(new Config("proxy"), new Shell("proxy", NullOutputStream.getInstance(), new ByteArrayInputStream("".getBytes()))));
			Thread t = new Thread(proxy);
			t.start();
		} 
		catch (Exception e)
		{
			e.printStackTrace();
		}

		try
		{
			ServerThreadTest sT = new ServerThreadTest(factory.startFileServer(new Config("fs1"), new Shell("fileserver 1", NullOutputStream.getInstance(), new ByteArrayInputStream("".getBytes()))));
			Thread t = new Thread(sT);
			t.start();
			generateFiles(new File("files/fileserver1/"), 10);

			ServerThreadTest sT2 = new ServerThreadTest(factory.startFileServer(new Config("fs2"), new Shell("fileserver 2", NullOutputStream.getInstance(), new ByteArrayInputStream("".getBytes()))));
			Thread t2 = new Thread(sT2);
			t2.start();
			generateFiles(new File("files/fileserver2/"), 10);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

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
			while(System.currentTimeMillis()-start < proxyDuration)
			{
				try
				{
					//System.out.println("Proxy alive");
					Thread.sleep(1000);
				} 
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			System.out.println("Proxy done");
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

	static class ServerThreadTest implements Runnable
	{
		private IFileServerCli cli;

		public ServerThreadTest(IFileServerCli cli)
		{
			this.cli = cli;
		}

		@Override
		public void run()
		{
			long start = System.currentTimeMillis();
			while(System.currentTimeMillis()-start < fileserverDuration)
			{
				//System.out.println("Fileserver alive");
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
			System.out.println("Fileserver Done");
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
			try
			{
				cli.login("alice", "12345");

				long start = System.currentTimeMillis();
				long uploadCount = start;
				long downloadCount = start;
				int ratioCount = 0;
				while (System.currentTimeMillis() - start < clientDuration)
				{
					cli.credits();
					cli.list();
					if (System.currentTimeMillis() - uploadCount > 60000 / uploadsPerMin)
					{
						System.out.println("Upload..."+ratioCount);
						ratioCount++;
						if(ratioCount <= overwriteRatio*10)
						{
							System.out.println("Overwrite");
							cli.upload("upload.txt");
						}
						else
						{
							System.out.println("New file");
							cli.upload(generateSingleFile(new File("files/client/")));
						}
						if(ratioCount == 10)
						{
							ratioCount = 0;
						}
						uploadCount = System.currentTimeMillis();
					}
					if (System.currentTimeMillis() - downloadCount > 60000 / downloadsPerMin)
					{
						System.out.println("Download...");
						cli.download("short.txt");
						downloadCount = System.currentTimeMillis();
					}
					try
					{
						Thread.sleep(1000);
					} catch (InterruptedException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				System.out.println("Done");

				cli.exit();

			} catch (IOException e)
			{
				System.err.println("No Fileserver connected!");
			}

		}
	}

	private static void generateFiles(File file, int numberOfFiles)
	{
		numberOfFiles+=count;
		while(count < numberOfFiles)
		{
			try 
			{
				RandomAccessFile f = new RandomAccessFile(file+"/file"+count, "rw");
				count++;
				f.setLength(fileSizeKB*1024);
				f.close();
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
	}

	private static String generateSingleFile(File file)
	{
		try 
		{
			String s = "/file"+count;
			RandomAccessFile f = new RandomAccessFile(file+s, "rw");
			count++;
			f.setLength(fileSizeKB*1024);
			f.close();
			return s;
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		return null;
	}
}
