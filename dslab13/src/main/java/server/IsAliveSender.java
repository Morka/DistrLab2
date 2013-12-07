package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class IsAliveSender implements Runnable
{
	private DatagramSocket socket;
	private int isAlive;
	private int tcpPort;
	private int proxyUdp;
	private AtomicBoolean stop;

	public IsAliveSender(DatagramSocket datagramSocket, int isAlive, int tcpPort, int proxyUdp, AtomicBoolean stop)
	{
		this.socket = datagramSocket;
		this.isAlive = isAlive;
		this.tcpPort = tcpPort;
		this.proxyUdp = proxyUdp;
		this.stop = stop;
	}

	@Override
	public void run()
	{
		init();
		while(!stop.get())
		{
			ByteBuffer b = ByteBuffer.allocate(4);
			b.putInt(tcpPort);
			byte[] buffer = b.array();
			DatagramPacket p = new DatagramPacket(buffer, buffer.length);
			try
			{
				socket.send(p);
			} 
			catch (IOException e)
			{
				e.printStackTrace();
			}
			try
			{
				Thread.sleep(isAlive);
			} 
			catch (InterruptedException e)
			{
				socket.close();
				return;
			}
		}
		socket.close();
	}
	
	private void init()
	{
		try
		{
			InetAddress address = InetAddress.getByName("localhost");
			socket.connect(address, proxyUdp);
		} 
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}
}
