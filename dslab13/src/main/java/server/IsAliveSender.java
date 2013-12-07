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
		// Continuously sending the Proxy isAlive packages:
		while (true) {
			try {
				Thread.sleep(this.isAlive);
			} catch (InterruptedException e1) {
				// Thread has been Interrupted.
				System.out.println("File Server Datagram Thread has been broken off.");
				return;
			}

			// Sends tcp Port of the file server to Proxy every isAlive Message.
			ByteBuffer b = ByteBuffer.allocate(4);
			b.putInt(tcpPort);
			byte[] buffer = b.array();
			try {
				InetAddress address = InetAddress.getByName("localhost");
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length,address, this.proxyUdp);
				socket.send(packet);
			} catch (UnknownHostException e) {
				System.err.println("Error: Host InetAdress is unknown. No Connection possible.");
				socket.close();
				return;
			} catch (IOException e) {
				System.err.println("Error: Sending is Alive Packages from this File Server did not work.");
				socket.close();
				return;
			}
		}
		/*
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
		*/
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
