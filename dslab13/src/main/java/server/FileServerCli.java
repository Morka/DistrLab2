package server;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import util.Config;

import message.response.MessageResponse;
import cli.Command;
import cli.Shell;

public class FileServerCli implements IFileServerCli
{
	private Config config;
	private Shell shell;
	private String directory;
	private ServerSocket serverSocket;
	private AtomicBoolean stop;
	private DatagramSocket datagramSocket;
	private FileServerOverseer overseer;
	private Thread t; //Overseer-Thread
	
	public FileServerCli(Config config, Shell shell)
	{
		this.config = config;
		this.shell = shell;
		stop = new AtomicBoolean(false);
		try
		{
			serverSocket = new ServerSocket(config.getInt("tcp.port"));
		} 
		catch (IOException e)
		{
			System.err.println("Error creating new Server Socket");
		}
		overseer = new FileServerOverseer(config, serverSocket, datagramSocket, stop);
		t = new Thread(overseer);
		t.start();
	}

	@Override
	@Command
	public MessageResponse exit() throws IOException
	{
		stop.set(true);
		shell.writeLine("Exiting...");
		//shell.close();
		System.in.close();
		
		//System.out.close();
		if(serverSocket != null){
			serverSocket.close();
		}
		if(datagramSocket != null){
			datagramSocket.close();
		}
		System.out.println("Fileserver is closed");
		return null;
	}
}
