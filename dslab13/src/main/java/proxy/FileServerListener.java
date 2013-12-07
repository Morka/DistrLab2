package proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
	
	private HashSet<String> files;

	public FileServerListener(DatagramSocket socket, int timeout, int checkPeriod, AtomicBoolean stop, HashSet<String> files, ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier)
	{
		this.socket = socket;
		this.timeout = timeout;
		this.checkPeriod = checkPeriod;
		this.stop = stop;
		this.files = files;
		lastOnline = new HashMap<Integer, Long>();
		this.serverIdentifier = serverIdentifier;
	}

	@Override
	public void run()
	{
		byte[] buffer;
		while(!stop.get())
		{
			buffer = new byte[4];
			DatagramPacket p = new DatagramPacket(buffer, buffer.length);
			Long current = System.currentTimeMillis();
			try
			{
				socket.receive(p);
				InetAddress address = p.getAddress();
				ByteBuffer wrapped = ByteBuffer.wrap(p.getData());
				int port = wrapped.getInt();
				lastOnline.put(port, current);
				synchronized(serverIdentifier)
				{
					if(!serverIdentifier.containsKey(port))
					{
						serverIdentifier.put(port, new FileServerInfo(address, port, 0, true));
					}
				}
				if(current - lastOnline.get(port) > timeout)
				{
					synchronized(serverIdentifier)
					{
						serverIdentifier.put(port, new FileServerInfo(address, port, 0, false));
					}
				}

			}
			catch (SocketException se)
			{
				return;
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
}
