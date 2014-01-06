package client;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
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
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.openssl.PasswordFinder;

import proxy.IProxyRMI;

import security.AESChannel;
import security.Base64Channel;
import security.Channel;
import security.Serialization;
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
import message.request.LoginRequestFinalHandshake;
import message.request.LoginRequestHandshake;
import message.request.LogoutRequest;
import message.request.PublicKeySetRequest;
import message.request.UploadRequest;
import message.response.DownloadFileResponse;
import message.response.DownloadTicketResponse;
import message.response.LoginResponse;
import message.response.LoginResponseHandshake;
import message.response.MessageResponse;
import message.response.LoginResponse.Type;
import message.response.PublicKeyMessageResponse;
import model.DownloadTicket;

public class ClientCli implements IClientCli {
	private final String B64 = "a-zA-Z0-9/+";

	private Shell shell;
	private Config config;
	private Socket socket;
	private String directory;
	private InputStream input;
	private ObjectInputStream objectInput;
	private OutputStream output;
	private ObjectOutputStream objectOutput;
	private Channel aesChannel;
	private Channel base64Channel;
	private boolean isLoggedIn;

	private ArrayList<ICallbackObject> callbacksList;

	private IProxyRMI proxyRMI;

	public ClientCli(Config config, Shell shell) {
		this.isLoggedIn = false;
		this.base64Channel = new Base64Channel();
		this.config = config;
		this.shell = shell;
		try {
			socket = new Socket(config.getString("proxy.host"),
					config.getInt("proxy.tcp.port"));
			directory = config.getString("download.dir");
			input = socket.getInputStream();
			objectInput = new ObjectInputStream(input);
			output = socket.getOutputStream();
			objectOutput = new ObjectOutputStream(output);
			objectOutput.flush();

			this.bindToProxyRMI();
			this.callbacksList = new ArrayList<ICallbackObject>();

		} catch (SocketException se) {
			try {
				shell.writeLine("Could not find server.");
				exitWithoutConnection();
			} catch (IOException e) {
				System.err.println("Error writing to console");
			}
		} catch (UnknownHostException e) {
			System.err.println("Error Host is unknown");
		} catch (IOException e)
		{
			System.err.println("Error Socket connection");
		}
	}

	private void bindToProxyRMI() {
		Config mcConfig = new Config("mc");
		String bindingName = mcConfig.getString("binding.name");
		int proxyRmiPort = mcConfig.getInt("proxy.rmi.port");
		String proxyHost = mcConfig.getString("proxy.host");
		
		try {
			Registry registry = LocateRegistry.getRegistry(proxyHost,
					proxyRmiPort);
			this.proxyRMI = (IProxyRMI) registry.lookup(bindingName);

		} catch (RemoteException e) {
			System.err.println("Error finding Registry");
		} catch (NotBoundException e) {
			System.err.println("Error binding with Registry");
		}

		System.out.println("Client is binded");
	}

	@Override
	@Command
	public MessageResponse readQuorum() throws IOException {
		return this.proxyRMI.readQuorum();
	}

	@Override
	@Command
	public MessageResponse writeQuorum() throws IOException {

		return this.proxyRMI.writeQuorum();

	}

	@Override
	@Command
	public MessageResponse topThreeDownloads() throws IOException {
		return this.proxyRMI.topThreeDownloads();
	}

	@Override
	@Command
	public MessageResponse subscribe(String filename, int numberOfDownloads)
			throws IOException {
		if (this.isLoggedIn) {
			ICallbackObject callbackObject = new CallbackObject();
			this.callbacksList.add(callbackObject);

			return this.proxyRMI.subscribe(filename, numberOfDownloads,
					callbackObject);
		} else {
			return new MessageResponse("Log in first");
		}
	}

	@Override
	@Command
	public PublicKeyMessageResponse getProxyPublicKey() throws IOException {
		Config mcConfig = new Config("client");
		String keysDir = mcConfig.getString("keys.dir");
		PublicKeyMessageResponse pKM = this.proxyRMI.getProxyPublicKey();

		PEMWriter write = new PEMWriter(new PrintWriter(new File(keysDir,
				"proxy.pub.pem")));
		write.writeObject(pKM.getPublicKey());
		write.close();

		return this.proxyRMI.getProxyPublicKey();
	}

	@Override
	@Command
	public MessageResponse setUserPublicKey(String userName) throws IOException {
		return this.proxyRMI.setUserPublicKey(userName,
				new PublicKeySetRequest(this.readInPublicKey(userName)));
	}

	private PublicKey readInPublicKey(String username) {
		Config config = new Config("client");
		String pathToPublicKey = config.getString("keys.dir");
		pathToPublicKey += "/" + username + ".pub.pem";

		System.out.println(pathToPublicKey);

		PublicKey publicKey = null;
		PEMReader in = null;
		try {
			in = new PEMReader(new FileReader(pathToPublicKey));
			publicKey = (PublicKey) in.readObject();
		} catch (FileNotFoundException ex) {
			System.err.println("ERROR: PublicKey File not found");
		} catch (IOException ex) {
			System.err.println("ERROR: in.readObject() not possible");

		}

		try {
			in.close();
		} catch (IOException e) {
			System.err.println("ERROR 'in' could'nt be closed");
		}

		return publicKey;

	}

	private void sendToServer(Request request) throws IOException {
		byte[] toSend = Serialization.serialize(request);

		if (this.aesChannel == null) {
				this.objectOutput.writeObject(toSend);
				this.objectOutput.flush();
		}else {
			byte[] encrypted = aesChannel.encode(toSend);
			encrypted = base64Channel.encode(encrypted);
			this.objectOutput.writeObject(encrypted);
			this.objectOutput.flush();
		}
	}

	private Response receiveFromServer() throws IOException {
		try {
			if (this.isLoggedIn) { // is aes Encrypted
				byte[] receive = (byte[]) this.objectInput.readObject();
				receive = this.base64Channel.decode(receive);
				byte[] decrypted = this.aesChannel.decode(receive);
				Response response = (Response) Serialization.deserialize(decrypted);
				return response;
			} else {
				byte[] receive = (byte[]) this.objectInput.readObject();
				Response response = (Response) Serialization.deserialize(receive);
				return response;
			}

		} catch (EOFException eof) {
			shell.writeLine("Socket closed unexpectedly");
			exit();
		} catch (SocketException se) {
			shell.writeLine("Socket closed unexpectedly.");
			exit();
		} catch (ClassNotFoundException e) {}
		return null;
	}

	@Override
	@Command
	public LoginResponse login(String username, String password)
			throws IOException {

		if(this.isLoggedIn == true){
			return new LoginResponse(Type.ALREADY_LOGGED_IN);
		}
		
		assert username.matches("[" + B64 + "]{1,24}") : "username not applicable"; // username
																					// must
																					// be
																					// b64
																					// and
																					// between
																					// 1
																					// and
																					// 24
																					// charakters

		String pathToPrivateKey = config.getString("keys.dir");
		pathToPrivateKey += "/" + username + ".pem";

		PrivateKey privateKey = null;

		try {
			privateKey = this.readPrivateKey(pathToPrivateKey, password);
		} catch (PrivateKeyException e) {
			System.err.println(e.getMessage());
			return new LoginResponse(Type.WRONG_CREDENTIALS);
		}

		LoginHandshake loginHandshake = new LoginHandshake(privateKey);
		LoginRequestHandshake request = loginHandshake.startHandshake(username);

		this.sendToServer(request);
		LoginResponseHandshake loginResponse = (LoginResponseHandshake) this
				.receiveFromServer();

		AesProperties handshakeInfo = loginHandshake
				.finishHandshake(loginResponse);
		if (handshakeInfo == null) {
			return new LoginResponse(Type.WRONG_CREDENTIALS);
		}

		this.aesChannel = new AESChannel(handshakeInfo.getIvParam(),
				handshakeInfo.getSecretKey());

		LoginRequestFinalHandshake finalLoginRequest = new LoginRequestFinalHandshake(
				handshakeInfo.getChallenge(), null);

		this.isLoggedIn = true;

		this.sendToServer(finalLoginRequest);
		return (LoginResponse) this.receiveFromServer();
	}

	private PrivateKey readPrivateKey(String pathToPrivateKey,
			final String password) throws PrivateKeyException {
		PEMReader in = null;
		PrivateKey privateKey = null;
		try {
			in = new PEMReader(new FileReader(pathToPrivateKey),
					new PasswordFinder() {
						@Override
						public char[] getPassword() {

							return password.toCharArray();

						}
					});
		} catch (FileNotFoundException e) {
			throw new PrivateKeyException("ERROR: Couldnt read privateKey file");
		}
		try {
			KeyPair keyPair = (KeyPair) in.readObject();
			privateKey = keyPair.getPrivate();
			in.close();
		} catch (IOException ex) {
			// TODO: at the moment a restart is needed in case of an
			// IOException. fix!
			throw new PrivateKeyException(
					"ERROR: reading Private Key - most likely wrong password");
		}

		return privateKey;
	}

	@Override
	@Command
	public Response credits() throws IOException {
		CreditsRequest request = new CreditsRequest();

		this.sendToServer(request);

		return this.receiveFromServer();

	}

	@Override
	@Command
	public Response buy(long credits) throws IOException {
		BuyRequest request = new BuyRequest(credits);

		this.sendToServer(request);

		return this.receiveFromServer();

	}

	@Override
	@Command
	public Response list() throws IOException {
		ListRequest request = new ListRequest("");

		this.sendToServer(request);

		return this.receiveFromServer();

	}

	@Override
	@Command
	public Response download(String filename) throws IOException {
		File f = new File(directory, filename);
		if (f.exists()) {
			f.delete();
		}
		DownloadTicketRequest request = new DownloadTicketRequest(filename);

		this.sendToServer(request);

		try {
			Response r = (Response) this.receiveFromServer();
			if (r instanceof MessageResponse) {
				return r;
			}
			if (r instanceof DownloadTicketResponse) {
				DownloadTicketResponse response = (DownloadTicketResponse) r;
				DownloadTicket ticket = response.getTicket();

				Socket downloadSocket = new Socket(ticket.getAddress(),
						ticket.getPort());
				ObjectOutputStream oos = new ObjectOutputStream(
						downloadSocket.getOutputStream());
				oos.flush();
				ObjectInputStream ois = new ObjectInputStream(
						downloadSocket.getInputStream());
				DownloadFileRequest fileRequest = new DownloadFileRequest("",
						ticket);

				oos.writeObject(fileRequest);
				oos.flush();
				Response fileResponse = (Response) ois.readObject();
				if (fileResponse instanceof MessageResponse) {
					return fileResponse;
				} else if (fileResponse instanceof DownloadFileResponse) {
					DownloadFileResponse downloadFile = (DownloadFileResponse) fileResponse;
					String text = new String(downloadFile.getContent());
					File downloaded = new File(this.directory, filename);
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
		} catch (EOFException eof) {
			shell.writeLine("Socket closed unexpectedly");
			exit();
		} catch (SocketException se) {
			shell.writeLine("Socket closed unexpectedly.");
			exit();
		} catch (ClassNotFoundException e) {}
		return null;
	}

	@Override
	@Command
	public MessageResponse upload(String filename) throws IOException {
		File f = new File(this.directory, filename);
		if (f.exists()) {
			BufferedReader br = new BufferedReader(new FileReader(f));
			try {
				StringBuilder sb = new StringBuilder();
				String line = br.readLine();

				while (line != null) {
					sb.append(line);
					sb.append('\n');
					line = br.readLine();
				}
				String text = sb.toString();

				UploadRequest request = new UploadRequest("", filename, 0,
						text.getBytes());

				this.sendToServer(request);

				try {
					return (MessageResponse) this.receiveFromServer();
				} catch (SocketException se) {
					shell.writeLine("Socket closed unexpectedly.");
					exit();
				} catch (EOFException eof) {
					shell.writeLine("Socket closed unexpectedly");
					exit();
				}

			} finally {
				br.close();
			}
		} else {
			return new MessageResponse(
					"The file you wanted to upload doesn't exist.");
		}
		return null;
	}

	@Override
	@Command
	public MessageResponse logout() throws IOException {
		LogoutRequest request = new LogoutRequest();
		int i = 0;

		this.sendToServer(request);
		this.isLoggedIn = false;
		this.aesChannel = null;
		while (!callbacksList.isEmpty()) {
			try {
				UnicastRemoteObject.unexportObject(callbacksList.get(i), true);
			} catch (NoSuchObjectException e1) {
				System.err.println("Nothing to unexport");
			}

			callbacksList.remove(i);
			i++;
		}

		return (MessageResponse) this.receiveFromServer();
	}

	@Override
	@Command
	public MessageResponse exit() throws IOException {
		shell.writeLine("Exiting...");
		if (this.isLoggedIn == true) {
			this.logout();
		}
		objectInput.close();
		objectOutput.close();
		input.close();
		output.close();
		socket.close();
		shell.close();
		System.in.close();
		//System.out.close();
		return null;
	}

	private void exitWithoutConnection() throws IOException {
		shell.writeLine("Exiting...");
		int i = 0;
		if (callbacksList != null) {
			while (!callbacksList.isEmpty()) {
				try {
					UnicastRemoteObject.unexportObject(callbacksList.get(i),
							true);
				} catch (NoSuchObjectException e1) {
					System.err.println("Nothing to unexport");
				}

				callbacksList.remove(i);
				i++;
			}
		}
		shell.close();
		System.in.close();
		System.out.close();
	}


}
