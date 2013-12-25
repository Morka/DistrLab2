package proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import security.AESChannel;
import security.Base64Channel;
import security.Channel;
import util.ChecksumUtils;
import util.Config;
import message.Request;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.DownloadTicketRequest;
import message.request.InfoRequest;
import message.request.ListRequest;
import message.request.LoginRequest;
import message.request.LoginRequestFinalHandshake;
import message.request.LoginRequestHandshake;
import message.request.LogoutRequest;
import message.request.UploadRequest;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.DownloadTicketResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.LoginResponse;
import message.response.LoginResponse.Type;
import message.response.LoginResponseHandshake;
import message.response.MessageResponse;
import model.DownloadTicket;
import model.FileServerInfo;
import model.UserInfo;

public class Proxy_Alex /*implements IProxy, Runnable*/ {
	/*private InputStream input;
	private ObjectInputStream objectInput;
	private OutputStream output;
	private ObjectOutputStream objectOutput;
	private String username;
	private AtomicBoolean stop;
	private Channel aesChannel;
	private Channel base64Channel;
	private byte[] serverChallenge;
	private boolean loggedIn;
	private String tmpUsername;

	private ConcurrentHashMap<String, UserInfo> users;

	private ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier;

	private HashSet<String> files;

	public Proxy_Alex(Socket clientSocket, HashSet<String> files,
			ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier,
			AtomicBoolean stop) {
		this.base64Channel = new Base64Channel();
		this.users = UserData.getInstance().users;
		this.stop = stop;
		this.serverIdentifier = ServerData.getInstance().servers;
		this.files = files; //<--- whooot?
		username = "";
		try {
			output = clientSocket.getOutputStream();
			objectOutput = new ObjectOutputStream(output);
			objectOutput.flush();
			input = clientSocket.getInputStream();
			objectInput = new ObjectInputStream(input);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public LoginResponse login(LoginRequest request) throws IOException {
		if (username.equals("")) {
			synchronized (users) {
				if (users.containsKey(request.getUsername())) {
					Config config = new Config("user");
					if (config.getString(request.getUsername() + ".password")
							.equals(request.getPassword())) {
						username = request.getUsername();
						UserInfo old = users.get(username);
						UserInfo info = new UserInfo(username,
								old.getCredits(), true);
						users.put(username, info);
						return new LoginResponse(LoginResponse.Type.SUCCESS);
					} else {
						return new LoginResponse(
								LoginResponse.Type.WRONG_CREDENTIALS);
					}
				} else {
					return new LoginResponse(
							LoginResponse.Type.WRONG_CREDENTIALS);
				}
			}
		} else {
			return new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
		}
	}

	@Override
	public Response credits() throws IOException {
		if (username.equals("")) {
			return new MessageResponse("Please log in first.");
		} else {
			synchronized (users) {
				return new CreditsResponse(Long.valueOf(users.get(username)
						.getCredits()));
			}
		}
	}

	@Override
	public Response buy(BuyRequest credits) throws IOException {
		if (username.equals("")) {
			return new MessageResponse("Please log in first.");
		} else {
			synchronized (users) {
				Long newCredits = users.get(username).getCredits()
						+ credits.getCredits();
				UserInfo old = users.get(username);
				UserInfo info = new UserInfo(username, newCredits,
						old.isOnline());
				users.put(username, info);
				return new BuyResponse(newCredits);
			}
		}
	}

	@Override
	public Response list() throws IOException {
		if (username.equals("")) {
			return new MessageResponse("Please log in first.");
		} else {
			synchronized (serverIdentifier) {
				if (serverIdentifier.isEmpty()) {
					return new MessageResponse("No files available");
				} else {
					for (Map.Entry<Integer, FileServerInfo> entry : serverIdentifier
							.entrySet()) {
						if (entry.getValue().isOnline()) {
							Socket socket = new Socket(entry.getValue()
									.getAddress(), entry.getValue().getPort());
							ObjectOutputStream oos = new ObjectOutputStream(
									socket.getOutputStream());
							oos.flush();
							ObjectInputStream ois = new ObjectInputStream(
									socket.getInputStream());
							ListRequest listRequest = new ListRequest();

							oos.writeObject(listRequest);
							oos.flush();
							try {
								ListResponse listResponse = (ListResponse) ois
										.readObject();
								oos.close();
								ois.close();
								socket.close();
								synchronized (files) {
									for (String s : listResponse.getFileNames()) {
										files.add(s);
									}
									return new ListResponse(files);
								}
							} catch (ClassNotFoundException e) {
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
	public Response download(DownloadTicketRequest request) throws IOException {
		if (username.equals("")) {
			return new MessageResponse("Please log in first.");
		} else {
			list();
			synchronized (files) {
				if (files.contains(request.getFilename())) {
					synchronized (serverIdentifier) {
						if (serverIdentifier.isEmpty()) {
							return new MessageResponse("No servers available.");
						}
						long min = Long.MAX_VALUE;
						InetAddress address = null;
						int port = 0;
						long usage = 0;
						for (Map.Entry<Integer, FileServerInfo> entry : serverIdentifier
								.entrySet()) {
							usage = entry.getValue().getUsage();
							if (min > usage) {
								min = usage;
								address = entry.getValue().getAddress();
								port = entry.getKey();
							}
						}
						usage = serverIdentifier.get(port).getUsage();
						Socket socket = new Socket(address, port);
						ObjectOutputStream oos = new ObjectOutputStream(
								socket.getOutputStream());
						oos.flush();
						ObjectInputStream ois = new ObjectInputStream(
								socket.getInputStream());
						InfoRequest infoRequest = new InfoRequest(
								request.getFilename());

						oos.writeObject(infoRequest);
						oos.flush();
						long fileSize = 0;
						InfoResponse infoResponse;
						try {
							infoResponse = (InfoResponse) ois.readObject();
							fileSize = infoResponse.getSize();
							oos.close();
							ois.close();
							socket.close();
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						}
						synchronized (users) {
							if (fileSize > users.get(username).getCredits()) {
								return new MessageResponse(
										"The file requires "
												+ String.valueOf(fileSize)
												+ " credits to download, but you only have "
												+ users.get(username)
														.getCredits());
							}
							String checksum = ChecksumUtils.generateChecksum(
									username, request.getFilename(), 0,
									fileSize);
							DownloadTicket ticket = new DownloadTicket(
									username, request.getFilename(), checksum,
									address, port);
							UserInfo old = users.get(username);
							UserInfo info = new UserInfo(username,
									old.getCredits() - fileSize, old.isOnline());
							users.put(username, info);
							serverIdentifier.put(port, new FileServerInfo(
									address, port, usage + fileSize, true));
							return new DownloadTicketResponse(ticket);
						}
					}
				} else {
					return new MessageResponse(
							"The file you wanted to download doesn't exist");
				}
			}
		}
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException {
		if (username.equals("")) {
			return new MessageResponse("Please log in first.");
		} else {
			synchronized (serverIdentifier) {
				if (serverIdentifier.isEmpty()) {
					return new MessageResponse("No servers available.");
				}
				for (Map.Entry<Integer, FileServerInfo> entry : serverIdentifier
						.entrySet()) {
					Socket socket = new Socket(entry.getValue().getAddress(),
							entry.getKey());
					ObjectOutputStream oos = new ObjectOutputStream(
							socket.getOutputStream());
					oos.flush();
					ObjectInputStream ois = new ObjectInputStream(
							socket.getInputStream());
					String s = new String(request.getContent());

					oos.writeObject(request);
					oos.flush();

					long fileSize = s.length();
					synchronized (files) {
						files.add(request.getFilename());
					}
					synchronized (users) {
						UserInfo old = users.get(username);
						UserInfo info = new UserInfo(username, old.getCredits()
								+ fileSize, old.isOnline());
						users.put(username, info);
					}
					oos.close();
					ois.close();
					socket.close();
					serverIdentifier.put(entry.getKey(), new FileServerInfo(
							entry.getValue().getAddress(), entry.getKey(),
							entry.getValue().getUsage() + fileSize, true));
				}
				synchronized (users) {
					return new MessageResponse(
							"Uploaded file to server, new Credits: "
									+ users.get(username).getCredits());
				}
			}
		}
	}

	@Override
	public MessageResponse logout() throws IOException {
		if (username.equals("")) {
			return new MessageResponse("Please log in first.");
		} else {
			synchronized (users) {
				UserInfo old = users.get(username);
				UserInfo info = new UserInfo(username, old.getCredits(), false);
				users.put(username, info);
				this.loggedIn = false;
				this.aesChannel = null;
				this.username = "";
				return new MessageResponse("You were logged out");
			}
		}
	}

	private LoginResponseHandshake loginHandshake(LoginRequestHandshake loginRequest) {
		LoginHandler loginHandler = new LoginHandler();
		LoginResponseHandshake loginResponse = loginHandler.sendBackHandshake(loginRequest);

		this.aesChannel = new AESChannel(loginHandler.getIVParam(),
				loginHandler.getSecretKey());
		
		this.serverChallenge = loginHandler.getProxyChallenge();
		this.tmpUsername = loginHandler.getUsername();
		
		return loginResponse;
	}

	
	private byte[] serialize(Object obj) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(obj);
		return bos.toByteArray();
	}

	private Object deserialize(byte[] bytes) throws IOException,
			ClassNotFoundException {
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		ObjectInputStream ois = new ObjectInputStream(bis);
		return ois.readObject();
	}

	private void sendResponse(Response response) throws SocketException,
			EOFException, IOException {
		if (this.loggedIn == false) {
			byte[] toSend = this.serialize(response);
			this.objectOutput.writeObject(toSend);
			this.objectOutput.flush();
		} else {
			byte[] toSend = this.serialize(response);
			byte[] encrypted = this.aesChannel.encode(toSend);
			encrypted = this.base64Channel.encode(encrypted);
			this.objectOutput.writeObject(encrypted);
			this.objectOutput.flush();
		}
	}

	private Request receiveRequest() throws IOException, ClassNotFoundException {
		Request request = null;
		if (this.aesChannel == null) {
			byte[] message = (byte[]) this.objectInput.readObject();
			request = (Request) this.deserialize(message);
		} else {
			byte[] message = (byte[]) this.objectInput.readObject();
			message = this.base64Channel.decode(message);
			byte[] decrypted = this.aesChannel.decode(message);
			request = (Request) this.deserialize(decrypted);
		}

		return request;
	}
	
	private Response checkIfChallengeIsOkay(Request request){
		LoginRequestFinalHandshake loginRequest = (LoginRequestFinalHandshake)request;
		
		if(loginRequest.getUsername().equals(new String(this.serverChallenge))){
			if(username.equals("")){
				synchronized(users){
					if(users.containsKey(this.tmpUsername)){
						this.username = this.tmpUsername;
						UserInfo old = users.get(username);
						UserInfo info = new UserInfo(username, old.getCredits(), true);
						users.put(username, info);
						this.loggedIn = true;
						return new LoginResponse(Type.SUCCESS);

					}else{
						System.err.println("Not Logged In 1");
						//TODO: If this fails, the server tries to send it without aes encryption altough the client waits for such an encrpytion... therefore the client breaks down.
						return new LoginResponse(Type.WRONG_CREDENTIALS);
					}
				}
			}else{
				System.err.println("Not Logged In 2");
				return new LoginResponse(Type.WRONG_CREDENTIALS);
			}
		}
		else{
			System.err.println("Not Logged In 3");
			//TODO: If this fails, the server tries to send it without aes encryption altough the client waits for such an encrpytion... therefore the client breaks down.
			return new LoginResponse(Type.WRONG_CREDENTIALS);
		}
	}
	
	@Override
	public void run() {
		while (!stop.get()) {
			try {
				Request message = this.receiveRequest();
				/*
				 * if (message instanceof LoginRequest) { LoginResponse response
				 * = login((LoginRequest)message); this.sendResponse(response);
				 * }
				 *//*
				if (message instanceof LoginRequestHandshake) {
						LoginResponseHandshake response = this.loginHandshake((LoginRequestHandshake) message);
						this.sendResponse(response);
									
				} else if (message instanceof LoginRequestFinalHandshake) {
					//TODO: log in the user at the proxy machine.
					this.sendResponse(this.checkIfChallengeIsOkay(message));
					
				} else if (message instanceof CreditsRequest) {
					Response response = credits();
					this.sendResponse(response);

				} else if (message instanceof BuyRequest) {
					Response response = buy((BuyRequest) message);
					this.sendResponse(response);

				} else if (message instanceof ListRequest) {
					Response response = list();
					this.sendResponse(response);

				} else if (message instanceof DownloadTicketRequest) {
					Response response = download((DownloadTicketRequest) message);
					this.sendResponse(response);

				} else if (message instanceof UploadRequest) {
					MessageResponse response = upload((UploadRequest) message);
					this.sendResponse(response);

				} else if (message instanceof LogoutRequest) {
					MessageResponse response = logout();
					this.sendResponse(response);
				}

			} catch (SocketException se) {
				try {
					objectOutput.close();
					objectInput.close();
					output.close();
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} catch (EOFException eof) {
				synchronized (users) {
					if (!username.equals("")) {
						UserInfo old = users.get(username);
						UserInfo info = new UserInfo(username,
								old.getCredits(), false);
						users.put(username, info);
					}
				}
				username = "";
			} catch (IOException e) {
				e.printStackTrace();
				return;
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				return;
			}
		}
	}*/
}
