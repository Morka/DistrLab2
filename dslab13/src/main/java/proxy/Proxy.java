package proxy;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.DownloadTicketResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.LoginResponse;
import message.response.MessageResponse;
import model.DownloadTicket;
import model.FileServerInfo;

public class Proxy implements IProxy, Runnable
{
	private InputStream input;
	private ObjectInputStream objectInput;
	private OutputStream output;
	private ObjectOutputStream objectOutput;
	private Socket clientSocket;
	private String username;
	private AtomicBoolean stop;

	/*
	 * username is the key, values (in that order) are password, credits, online status
	 */
	private ConcurrentHashMap<String, ArrayList<String>> users; 

	private ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier;

	private HashSet<String> files;

	public Proxy(Socket clientSocket, ConcurrentHashMap<String, ArrayList<String>> users, HashSet<String> files, ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier, AtomicBoolean stop)
	{
		this.clientSocket = clientSocket;
		this.users = users;
		this.files = files;
		this.stop = stop;
		this.serverIdentifier = serverIdentifier;
		username = "";
		try
		{
			output = clientSocket.getOutputStream();
			objectOutput = new ObjectOutputStream(output);
			objectOutput.flush();
			input = clientSocket.getInputStream();
			objectInput = new ObjectInputStream(input);  
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}  
	}

	@Override
	public LoginResponse login(LoginRequest request) throws IOException
	{
		if(username.equals(""))
		{
			synchronized(users)
			{
				if(users.containsKey(request.getUsername()))
				{
					if(users.get(request.getUsername()).get(0).equals(request.getPassword()))
					{
						username = request.getUsername();
						ArrayList<String> values = users.get(username);
						values.set(2, "online");
						return new LoginResponse(LoginResponse.Type.SUCCESS);
					}
					else
					{
						return new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
					}
				}
				else
				{
					return new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
				}
			}
		}
		else
		{
			return new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
		}
	}

	@Override
	public Response credits() throws IOException
	{
		if(username.equals(""))
		{
			return new MessageResponse("Please log in first.");
		}
		else
		{
			synchronized(users)
			{
				return new CreditsResponse(Long.valueOf(users.get(username).get(1)).longValue());
			}
		}
	}

	@Override
	public Response buy(BuyRequest credits) throws IOException
	{
		if(username.equals(""))
		{
			return new MessageResponse("Please log in first.");
		}
		else
		{
			synchronized(users)
			{
				Long newCredits = Long.valueOf(users.get(username).get(1)).longValue() + credits.getCredits();
				ArrayList<String> values = users.get(username);
				values.set(1, Long.toString(newCredits));
				return new BuyResponse(newCredits);
			}
		}
	}

	@Override
	public Response list() throws IOException
	{
		if(username.equals(""))
		{
			return new MessageResponse("Please log in first.");
		}
		else
		{
			synchronized(serverIdentifier)
			{
				if(serverIdentifier.isEmpty())
				{
					return new MessageResponse("No files available");
				}
				else
				{
					for(Map.Entry<Integer, FileServerInfo> entry: serverIdentifier.entrySet())
					{
						if(entry.getValue().isOnline())
						{
							Socket socket = new Socket(entry.getValue().getAddress(), entry.getValue().getPort());
							ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
							oos.flush();
							ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
							ListRequest listRequest = new ListRequest();

							oos.writeObject(listRequest);
							oos.flush();
							try
							{
								ListResponse listResponse = (ListResponse) ois.readObject();
								oos.close();
								ois.close();
								socket.close();
								synchronized(files)
								{
									for(String s : listResponse.getFileNames())
									{
										files.add(s);
									}
									return new ListResponse(files);
								}
							} 
							catch (ClassNotFoundException e)
							{
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
		return new ListResponse(files);
	}

	@Override
	public Response download(DownloadTicketRequest request) throws IOException
	{
		if(username.equals(""))
		{
			return new MessageResponse("Please log in first.");
		}
		else
		{
			list();
			synchronized(files)
			{
				if(files.contains(request.getFilename()))
				{
					synchronized(serverIdentifier)
					{
						if(serverIdentifier.isEmpty())
						{
							return new MessageResponse("No servers available.");
						}
						long min = Long.MAX_VALUE;
						InetAddress address = null;
						int port = 0;
						long usage = 0;
						for(Map.Entry<Integer, FileServerInfo> entry : serverIdentifier.entrySet())
						{
							usage = entry.getValue().getUsage();
							if(min > usage)
							{
								min = usage;
								address = entry.getValue().getAddress();
								port = entry.getKey();
							}
						}
						usage = serverIdentifier.get(port).getUsage();
						Socket socket = new Socket(address, port);
						ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
						oos.flush();
						ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
						InfoRequest infoRequest = new InfoRequest(request.getFilename());

						oos.writeObject(infoRequest);
						oos.flush();
						long fileSize = 0;
						InfoResponse infoResponse;
						try
						{
							infoResponse = (InfoResponse) ois.readObject();
							fileSize = infoResponse.getSize();
							oos.close();
							ois.close();
							socket.close();
						} 
						catch (ClassNotFoundException e)
						{
							e.printStackTrace();
						}
						synchronized(users)
						{
							if (fileSize > Long.parseLong(users.get(username).get(1)))
							{
								return new MessageResponse("The file requires "+String.valueOf(fileSize)+" credits to download, but you only have "+users.get(username).get(1));
							}
							String checksum = ChecksumUtils.generateChecksum(username, request.getFilename(), 0, fileSize);
							DownloadTicket ticket = new DownloadTicket(username, request.getFilename(), checksum, address, port);
							ArrayList<String> values = users.get(username);
							values.set(1, String.valueOf(Long.parseLong(values.get(1))-fileSize));
							serverIdentifier.put(port, new FileServerInfo(address, port, usage+fileSize, true));
							return new DownloadTicketResponse(ticket);
						}
					}
				}
				else
				{
					return new MessageResponse("The file you wanted to download doesn't exist");
				}
			}
		}
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException
	{
		if(username.equals(""))
		{
			return new MessageResponse("Please log in first.");
		}
		else
		{
			synchronized(serverIdentifier)
			{
				if(serverIdentifier.isEmpty())
				{
					return new MessageResponse("No servers available.");
				}
				for(Map.Entry<Integer, FileServerInfo> entry : serverIdentifier.entrySet())
				{
					Socket socket = new Socket(entry.getValue().getAddress(), entry.getKey());
					ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
					oos.flush();
					ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
					String s = new String(request.getContent());

					oos.writeObject(request);
					oos.flush();

					long fileSize = s.length();
					synchronized(files)
					{
						files.add(request.getFilename());
					}
					synchronized(users)
					{
						ArrayList<String> values = users.get(username);
						values.set(1, String.valueOf(Long.parseLong(values.get(1))+fileSize));
					}
					oos.close();
					ois.close();
					socket.close();
					serverIdentifier.put(entry.getKey(), new FileServerInfo(entry.getValue().getAddress(), entry.getKey(), entry.getValue().getUsage()+fileSize, true));
				}
				synchronized(users)
				{
					return new MessageResponse("Uploaded file to server, new Credits: "+users.get(username).get(1));
				}
			}
		}
	}



	@Override
	public MessageResponse logout() throws IOException
	{
		if(username.equals(""))
		{
			return new MessageResponse("Please log in first.");
		}
		else
		{
			synchronized(users)
			{
				ArrayList<String> values = users.get(username);
				values.set(2, "offline");
				return new MessageResponse("You were logged out");
			}
		}
	}

	@Override
	public void run()
	{
		while(!stop.get())
		{
			try
			{
				Request message = (Request)objectInput.readObject();
				if (message instanceof LoginRequest)
				{
					LoginResponse response = login((LoginRequest)message);
					objectOutput.writeObject(response);
					objectOutput.flush();
				}
				if (message instanceof CreditsRequest)
				{
					Response response = credits();
					objectOutput.writeObject(response);
					objectOutput.flush();
				}
				if (message instanceof BuyRequest)
				{
					Response response = buy((BuyRequest)message);
					objectOutput.writeObject(response);
					objectOutput.flush();
				}
				if (message instanceof ListRequest)
				{
					Response response = list();
					objectOutput.writeObject(response);
					objectOutput.flush();
				}
				if (message instanceof DownloadTicketRequest)
				{
					Response response = download((DownloadTicketRequest)message);
					objectOutput.writeObject(response);
					objectOutput.flush();
				}
				if (message instanceof UploadRequest)
				{
					MessageResponse response = upload((UploadRequest)message);
					objectOutput.writeObject(response);
					objectOutput.flush();
				}
				if (message instanceof LogoutRequest)
				{
					MessageResponse response = logout();
					objectOutput.writeObject(response);
					objectOutput.flush();
				}
			}
			catch(SocketException se)
			{
				try
				{
					objectOutput.close();
					objectInput.close();
					output.close();
					input.close();
				} 
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
			catch(EOFException eof)
			{
				synchronized(users)
				{
					if(!username.equals(""))
					{
						ArrayList<String> values = users.get(username);
						values.set(2, "offline");
					}
				}
				username = "";
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
}
