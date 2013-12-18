package client;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import util.Config;
import cli.Command;
import cli.Shell;
import message.Request;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.DownloadFileRequest;
import message.request.DownloadTicketRequest;
import message.request.ListRequest;
import message.request.LoginRequest;
import message.request.LogoutRequest;
import message.request.UploadRequest;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.DownloadFileResponse;
import message.response.DownloadTicketResponse;
import message.response.ListResponse;
import message.response.LoginResponse;
import message.response.MessageResponse;
import model.DownloadTicket;

public class ClientCli implements IClientCli
{
	private Shell shell;
	private Config config;
	private Socket socket;
	private String directory;
	private InputStream input;
	private ObjectInputStream objectInput;
	private OutputStream output;
	private ObjectOutputStream objectOutput;

	public ClientCli(Config config, Shell shell)
	{
		this.config = config;
		this.shell = shell;
		try
		{
			socket = new Socket(config.getString("proxy.host"), config.getInt("proxy.tcp.port"));
			directory = config.getString("download.dir");
			input = socket.getInputStream();
			objectInput = new ObjectInputStream(input);
			output = socket.getOutputStream();
			objectOutput = new ObjectOutputStream(output);
			objectOutput.flush();
		}
		catch(SocketException se)
		{
			try
			{
				shell.writeLine("Could not find server.");
				exitWithoutConnection();
			} 
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	private void sendToServer(Request request) throws IOException{
		//TODO: Does not throw a customized Exception.
		//The encryption code can be put here later on.
		this.objectOutput.writeObject(request);
		this.objectOutput.flush();
	}
	
	private Response receiveFromServer() throws IOException{
		try
		{
			Response response = (Response)this.objectInput.readObject();
			return response;
		}
		catch(EOFException eof)
		{
			shell.writeLine("Socket closed unexpectedly");
			exit();
		}
		catch(SocketException se)
		{
			shell.writeLine("Socket closed unexpectedly.");
			exit();
		}
		catch (ClassNotFoundException e)
		{
			e.printStackTrace();
		} 
		return null;
	}

	@Override
	@Command
	public LoginResponse login(String username, String password)
			throws IOException
			{
		LoginRequest request = new LoginRequest(username, password);
		this.sendToServer(request);
		
		return (LoginResponse)this.receiveFromServer();
		
	}

	@Override
	@Command
	public Response credits() throws IOException
	{
		CreditsRequest request = new CreditsRequest();
		
		this.sendToServer(request);
		
		return this.receiveFromServer();
		
	}

	@Override
	@Command
	public Response buy(long credits) throws IOException
	{
		BuyRequest request = new BuyRequest(credits);
		
		this.sendToServer(request);
		
		return this.receiveFromServer();
		
	}


	@Override
	@Command
	public Response list() throws IOException
	{
		ListRequest request = new ListRequest("");
		
		this.sendToServer(request);
		
		return this.receiveFromServer();
		
	}



	@Override
	@Command
	public Response download(String filename) throws IOException
	{
		File f = new File(directory, filename);
		if(f.exists())
		{
			f.delete();
		}
		DownloadTicketRequest request = new DownloadTicketRequest(filename);
		
		this.sendToServer(request);
		
		try
		{
			Response r = (Response) objectInput.readObject(); 
			if(r instanceof MessageResponse)
			{
				return r;
			}
			if(r instanceof DownloadTicketResponse)
			{
				DownloadTicketResponse response = (DownloadTicketResponse)r;
				DownloadTicket ticket = response.getTicket();

				Socket downloadSocket = new Socket(ticket.getAddress(), ticket.getPort());
				ObjectOutputStream oos = new ObjectOutputStream(downloadSocket.getOutputStream());
				oos.flush();
				ObjectInputStream ois = new ObjectInputStream(downloadSocket.getInputStream());
				DownloadFileRequest fileRequest = new DownloadFileRequest("",ticket);

				oos.writeObject(fileRequest);
				oos.flush();
				Response fileResponse = (Response) ois.readObject();
				if(fileResponse instanceof MessageResponse)
				{
					return fileResponse;
				}
				else if(fileResponse instanceof DownloadFileResponse)
				{
					DownloadFileResponse downloadFile = (DownloadFileResponse)fileResponse;
					String text = new String(downloadFile.getContent());
					File downloaded = new File(directory, filename);
					downloaded.createNewFile();

					PrintWriter out = new PrintWriter(downloaded);
					out.println(text);
					out.flush();
					out.close();
					oos.close();
					ois.close();
					downloadSocket.close();
					return downloadFile;
				}
			}
		} 
		catch(EOFException eof)
		{
			shell.writeLine("Socket closed unexpectedly");
			exit();
		}
		catch(SocketException se)
		{
			shell.writeLine("Socket closed unexpectedly.");
			exit();
		}
		catch (ClassNotFoundException e)
		{
			e.printStackTrace();
		}
		return null;
	}


	@Override
	@Command
	public MessageResponse upload(String filename) throws IOException
	{
		File f = new File(directory, filename);
		if(f.exists())
		{
			BufferedReader br = new BufferedReader(new FileReader(f));
			try
			{
				StringBuilder sb = new StringBuilder();
				String line = br.readLine();

				while (line != null) 
				{
					sb.append(line);
					sb.append('\n');
					line = br.readLine();
				}
				String text = sb.toString();

				UploadRequest request = new UploadRequest("",filename, 0, text.getBytes());
				
				this.sendToServer(request);
					
				try
				{
					return (MessageResponse)objectInput.readObject();
				} 
				catch(SocketException se)
				{
					shell.writeLine("Socket closed unexpectedly.");
					exit();
				}
				catch(EOFException eof)
				{
					shell.writeLine("Socket closed unexpectedly");
					exit();
				}
				catch (ClassNotFoundException e)
				{
					e.printStackTrace();
				}
			} 
			finally 
			{
				br.close();
			}
		}
		else
		{
			return new MessageResponse("The file you wanted to upload doesn't exist.");
		}
		return null;
	}


	@Override
	@Command
	public MessageResponse logout() throws IOException
	{
		LogoutRequest request = new LogoutRequest();
	
			this.sendToServer(request);
			return (MessageResponse)this.receiveFromServer();

		
	}


	@Override
	@Command
	public MessageResponse exit() throws IOException
	{
		shell.writeLine("Exiting...");
		objectInput.close();
		objectOutput.close();
		input.close();
		output.close();
		socket.close();
		shell.close();
		System.in.close();
		System.out.close();
		return null;
	}

	private void exitWithoutConnection() throws IOException
	{
		shell.writeLine("Exiting...");
		shell.close();
		System.in.close();
		System.out.close();
	}

	public Shell getShell()
	{
		return shell;
	}

	public void setShell(Shell shell)
	{
		this.shell = shell;
	}

	public Config getConfig()
	{
		return config;
	}

	public void setConfig(Config config)
	{
		this.config = config;
	}
}
