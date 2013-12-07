package proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import model.FileServerInfo;

public class FileServerListener implements Runnable
{
	private DatagramSocket socket;
	private int timeout;
	private int checkPeriod;
	private AtomicBoolean stop;
	private HashMap<Integer, Long> lastOnline;

	private ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier;
	
	//private HashSet<String> files;

	public FileServerListener(DatagramSocket socket, int timeout, int checkPeriod, AtomicBoolean stop, HashSet<String> files, ConcurrentHashMap<Integer, FileServerInfo> s)
	{
		this.socket = socket;
		this.timeout = timeout;
		this.checkPeriod = checkPeriod;
		this.stop = stop;
		//this.files = files;
		lastOnline = new HashMap<Integer, Long>();
		this.serverIdentifier = s;
	}

	@Override
	public void run()
	{

		Thread t = new Thread(new MessageLoop());
		t.start();
		try {
		while (!socket.isClosed()) {
			Thread.sleep(checkPeriod);
			for (int key : lastOnline.keySet()) {
				long time = lastOnline.get(key);
				try {
				if (((System.currentTimeMillis() - time) > timeout) && serverIdentifier.get(key).isOnline()) {
					this.setFileServerOffline(key);
					System.out.println("One File Server has been set on offline.");
				}
				} catch (NullPointerException e1) {
					System.out.println("Noch keine Server erkannt");
				}
			}
		}
		} catch (InterruptedException e) {
			System.out.println("Notification: Checking if File Servers are online has been Interrupted.");
		}
	}

	public void setFileServerOffline(int key) {
		FileServerInfo old = serverIdentifier.get(key);
		FileServerInfo info = new FileServerInfo(old.getAddress(), old.getPort(), old.getUsage(), false);
		serverIdentifier.put(key, info);
	}
	
	private class MessageLoop implements Runnable {
		public void run() {
			byte[] buffer;
			while (!stop.get()) {
				buffer = new byte[4];
				DatagramPacket p = new DatagramPacket(buffer, buffer.length);
				Long current = System.currentTimeMillis();
				try {
					socket.receive(p);
					InetAddress address = p.getAddress();
					ByteBuffer wrapped = ByteBuffer.wrap(p.getData());
					int port = wrapped.getInt();
					lastOnline.put(port, current);
					
					synchronized (serverIdentifier) {
						FileServerInfo fileserverinfo = null;
						if (!serverIdentifier.containsKey(port)) 
							fileserverinfo = new FileServerInfo(address, port, 0, true);
						else {
							FileServerInfo tmp = serverIdentifier.get(port);
							fileserverinfo = new FileServerInfo(tmp.getAddress(), tmp.getPort(), tmp.getUsage(), true);
						}
						serverIdentifier.put(port, fileserverinfo);

					}

				} catch (SocketException se) {
					return;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}	
			
		}
	}
}