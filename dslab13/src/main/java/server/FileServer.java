package server;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import util.ChecksumUtils;

import message.Request;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.DownloadFileRequest;
import message.request.DownloadTicketRequest;
import message.request.InfoRequest;
import message.request.ListRequest;
import message.request.LoginRequest;
import message.request.LogoutRequest;
import message.request.UploadRequest;
import message.request.VersionRequest;
import message.response.DownloadFileResponse;
import message.response.DownloadTicketResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.LoginResponse;
import message.response.MessageResponse;
import model.DownloadTicket;

public class FileServer implements IFileServer, Runnable
{
	private HashSet<String> files;
	private Socket socket;
	private String directory;
	private AtomicBoolean stop;
	private ObjectInputStream objectInput;
	private ObjectOutputStream objectOutput;

	public FileServer(HashSet<String> files, Socket socket, String directory, AtomicBoolean stop)
	{
		this.socket = socket;
		this.files = files;
		this.directory = directory;
		this.stop = stop;
		try
		{
			objectOutput = new ObjectOutputStream(socket.getOutputStream());
			objectOutput.flush();
			objectInput = new ObjectInputStream(socket.getInputStream());  
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}  
	}

	@Override
	public Response list() throws IOException
	{
		synchronized(files)
		{
			return new ListResponse(files);
		}
	}

	@Override
	public Response download(DownloadFileRequest request) throws IOException
	{
		synchronized(files)
		{
			DownloadTicket ticket = request.getTicket();
			File f = new File(directory, request.getTicket().getFilename());
			long fileSize = f.length();
			String checksum = ChecksumUtils.generateChecksum(ticket.getUsername(), ticket.getFilename(), 0, fileSize);
			if(checksum.equals(ticket.getChecksum()))
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

					DownloadFileResponse fileResponse = new DownloadFileResponse(ticket, text.getBytes());

					objectOutput.writeObject(fileResponse);
					objectOutput.flush();
					try
					{
						return (MessageResponse)objectInput.readObject();
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
				return new MessageResponse("Checksum not the same.");
			}
			return new MessageResponse("Error while downloading file.");
		}
	}

	@Override
	public Response info(InfoRequest request) throws IOException
	{
		synchronized(files)
		{
			if(files.contains(request.getFilename()))
			{
				File f = new File(directory, request.getFilename());
				if(f.exists())
				{
					return new InfoResponse(request.getFilename(), f.length());
				}
			}

			return new InfoResponse(request.getFilename(),0);
		}
	}

	@Override
	public Response version(VersionRequest request) throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException
	{
		File f = new File(directory, request.getFilename());
		if(f.exists())
		{
			f.delete();
		}
		String text = new String(request.getContent());
		File downloaded = new File(directory, request.getFilename());
		downloaded.createNewFile();

		PrintWriter out = new PrintWriter(downloaded);
		out.println(text);
		out.flush();
		out.close();
		return new MessageResponse("File uploaded to server.");
	} 

	@Override
	public void run()
	{
		while(!stop.get() && !socket.isClosed())
		{
			try
			{
				Request message = (Request)objectInput.readObject();
				if (message instanceof ListRequest)
				{
					ListResponse response = (ListResponse)list();
					objectOutput.writeObject(response);
					objectOutput.flush();
					closeConnection();
				}
				if (message instanceof DownloadFileRequest)
				{
					DownloadFileResponse response = (DownloadFileResponse)download((DownloadFileRequest)message);
					objectOutput.writeObject(response);
					objectOutput.flush();
					closeConnection();
				}
				if (message instanceof InfoRequest)
				{
					InfoResponse response = (InfoResponse)info((InfoRequest)message);
					objectOutput.writeObject(response);
					objectOutput.flush();
					closeConnection();
				}
				if (message instanceof UploadRequest)
				{
					MessageResponse response = (MessageResponse)upload((UploadRequest)message);
					objectOutput.writeObject(response);
					objectOutput.flush();
					closeConnection();
				}
			}
			catch(EOFException eof)
			{
				closeConnection();
			}
			catch (IOException e)
			{
				e.printStackTrace();
				return;
			} 
			catch (ClassNotFoundException e)
			{
				e.printStackTrace();
				return;
			}
		}
	}

	private void closeConnection()
	{
		try
		{
			objectInput.close();
			objectOutput.close();
			socket.close();
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}