package client;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.security.KeyPair;
import java.security.PrivateKey;

import message.Request;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.DownloadFileRequest;
import message.request.DownloadTicketRequest;
import message.request.ListRequest;
import message.request.LoginRequest;
import message.request.LoginRequestFinalHandshake;
import message.request.LoginRequestHandshake;
import message.request.LogoutRequest;
import message.request.UploadRequest;
import message.response.DownloadFileResponse;
import message.response.DownloadTicketResponse;
import message.response.LoginResponse;
import message.response.LoginResponseHandshake;
import message.response.MessageResponse;
import model.DownloadTicket;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;

import security.AESChannel;
import security.Base64Channel;
import security.Channel;
import util.Config;

import cli.Command;
import cli.Shell;
import client.IClientCli;

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
		} catch (SocketException se) {
			try {
				shell.writeLine("Could not find server.");
				exitWithoutConnection();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sendToServer(Request request) throws IOException {
		byte[] toSend = this.serialize(request);

		if (this.aesChannel == null && request instanceof LoginRequest) {
			this.objectOutput.writeObject(toSend);
			this.objectOutput.flush();
		} else {
			byte[] encrypted = aesChannel.encode(toSend);
			encrypted = base64Channel.encode(encrypted);
			this.objectOutput.writeObject(encrypted);
			this.objectOutput.flush();
		}
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

	private Response receiveFromServer() throws IOException {
		try {
			if (this.isLoggedIn) { // is aes Encrypted
				byte[] receive = (byte[])this.objectInput.readObject();
				System.out.println(new String(receive));
				receive = this.base64Channel.decode(receive);
				byte[] decrypted = this.aesChannel.decode(receive);
				Response response = (Response) this.deserialize(decrypted);
				return response;
			} else {
				byte[] receive = (byte[]) this.objectInput.readObject();
				Response response = (Response) this.deserialize(receive);
				return response;
			}
		} catch (EOFException eof) {
			shell.writeLine("Socket closed unexpectedly");
			exit();
		} catch (SocketException se) {
			shell.writeLine("Socket closed unexpectedly.");
			exit();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	@Command
	public LoginResponse login(String username, String password)
			throws IOException {
		
		System.out.println(username);

		assert username.matches("["+B64+"]{1,24}") : "username not applicable"; //username must be b64 and between 1 and 24 charakters
				
		String pathToPrivateKey = config.getString("keys.dir");
		pathToPrivateKey += "/" + username + ".pem";

		PrivateKey privateKey = this.readPrivateKey(pathToPrivateKey, password);

		LoginHandshake loginHandshake = new LoginHandshake(privateKey);
		LoginRequestHandshake request = loginHandshake.startHandshake(username);


		this.sendToServer(request);
		LoginResponseHandshake loginResponse = (LoginResponseHandshake) this.receiveFromServer();


		AesProperties handshakeInfo = loginHandshake.finishHandshake(loginResponse);

		this.aesChannel = new AESChannel(handshakeInfo.getIvParam(), handshakeInfo.getSecretKey());

		LoginRequestFinalHandshake finalLoginRequest = new LoginRequestFinalHandshake(
				handshakeInfo.getChallenge(), null);

		this.isLoggedIn = true;

		this.sendToServer(finalLoginRequest);
		return (LoginResponse)this.receiveFromServer();

	}

	private PrivateKey readPrivateKey(String pathToPrivateKey,
			final String password) {
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
			System.err.println("ERROR: Couldnt read privateKey file");
		}
		try {
			KeyPair keyPair = (KeyPair) in.readObject();
			privateKey = keyPair.getPrivate();
			in.close();
		} catch (IOException ex) {
			System.err
					.println("ERROR: reading Private Key - most likely wrong password");
		}
		System.out.println("Read in Proxy Private Key");
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
		ListRequest request = new ListRequest();

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
				DownloadFileRequest fileRequest = new DownloadFileRequest(
						ticket);

				oos.writeObject(fileRequest);
				oos.flush();

				Response fileResponse = (Response) ois.readObject();
				if (fileResponse instanceof MessageResponse) {
					return fileResponse;
				} else if (fileResponse instanceof DownloadFileResponse) {
					DownloadFileResponse downloadFile = (DownloadFileResponse) fileResponse;
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
		} catch (EOFException eof) {
			shell.writeLine("Socket closed unexpectedly");
			exit();
		} catch (SocketException se) {
			shell.writeLine("Socket closed unexpectedly.");
			exit();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	@Command
	public MessageResponse upload(String filename) throws IOException {
		File f = new File(directory, filename);
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

				UploadRequest request = new UploadRequest(filename, 0,
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

		this.sendToServer(request);
		this.isLoggedIn = false;
		this.aesChannel = null;
		return (MessageResponse) this.receiveFromServer();

	}

	@Override
	@Command
	public MessageResponse exit() throws IOException {
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

	private void exitWithoutConnection() throws IOException {
		shell.writeLine("Exiting...");
		shell.close();
		System.in.close();
		System.out.close();
	}

	public Shell getShell() {
		return shell;
	}

	public void setShell(Shell shell) {
		this.shell = shell;
	}

	public Config getConfig() {
		return config;
	}

	public void setConfig(Config config) {
		this.config = config;
	}
}
