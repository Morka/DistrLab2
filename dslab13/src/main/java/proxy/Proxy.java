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
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import security.AESChannel;
import security.Base64Channel;
import security.Channel;
import security.Serialization;
import util.ChecksumUtils;
import util.Config;
import util.HmacHelper;
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
import message.request.VersionRequest;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.DownloadTicketResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.LoginResponse;
import message.response.LoginResponseHandshake;
import message.response.MessageResponse;
import message.response.VersionResponse;
import message.response.LoginResponse.Type;
import model.DownloadTicket;
import model.FileServerInfo;
import model.UserInfo;

//TODO: Using the wrong password for the private key of the proxy isn't handled correctly!

public class Proxy implements IProxy, Runnable
{
        private InputStream input;
        private ObjectInputStream objectInput;
        private OutputStream output;
        private ObjectOutputStream objectOutput;
        private String username;
        private AtomicBoolean stop;
        private HmacHelper hMac;
        private Channel aesChannel;
    	private Channel base64Channel;
    	private byte[] serverChallenge;
    	private boolean loggedIn;
    	private String tmpUsername;
    	
    	private IProxyRMI proxyRMI;

        private ConcurrentHashMap<Integer, FileServerInfo> readQuorum = null;
        private ConcurrentHashMap<Integer, FileServerInfo> writeQuorum = null;
   
        private ConcurrentHashMap<String, UserInfo> users;
        
        public static ConcurrentHashMap<String, Integer> downloadCounter = new ConcurrentHashMap<String, Integer>();

        private ConcurrentHashMap<Integer, FileServerInfo> serverIdentifier;

        public Proxy(Socket clientSocket, Config config, AtomicBoolean stop, IProxyRMI proxyRMI)
        {                    	

    			this.base64Channel = new Base64Channel();
        		hMac = new HmacHelper(config);
                this.users = UserData.getInstance().users;        
                this.stop = stop;
                this.serverIdentifier = ServerData.getInstance().servers;
                this.username = "";
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
                this.proxyRMI = proxyRMI;
                
                this.setQuorums();
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
                                        Config config = new Config("user");
                                        if(config.getString(request.getUsername()+".password").equals(request.getPassword()))
                                        {
                                                username = request.getUsername();
                                                UserInfo old = users.get(username);
                                                UserInfo info = new UserInfo(username, old.getCredits(), true);
                                                users.put(username, info);
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
                                return new CreditsResponse(Long.valueOf(users.get(username).getCredits()));
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
                                Long newCredits = users.get(username).getCredits() + credits.getCredits();
                                UserInfo old = users.get(username);
                                UserInfo info = new UserInfo(username, newCredits, old.isOnline());
                                users.put(username, info);
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
                                        Set<String> files = new HashSet<String>();
                                        for(FileServerInfo i: serverIdentifier.values())
                                        {
                                                if(i.isOnline())
                                                {
                                                        Socket socket = new Socket(i.getAddress(), i.getPort());
                                                        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                                                        oos.flush();
                                                        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                                                        ListRequest listRequest = new ListRequest(hMac.createHash("!list"));

                                                        oos.writeObject(listRequest);
                                                        oos.flush();
                                                        try
                                                        {
                                                                Response response = (Response) ois.readObject();
                                                                if(response instanceof ListResponse)
                                                                {
                                                                        ListResponse listResponse = (ListResponse)response;
                                                                        if(!hMac.verifyHash(listResponse.gethMac(), listResponse.toString()))
                                                                        {
                                                                                System.out.println("This message has been tampered with: " + listResponse.toString());
                                                                        }
                                                                        oos.close();
                                                                        ois.close();
                                                                        socket.close();
                                                                        files.addAll(listResponse.getFileNames());
                                                                }
                                                                if(response instanceof MessageResponse)
                                                                {
                                                                        if(((MessageResponse)response).getMessage().equals("!again"))
                                                                        {
                                                                                return list();
                                                                        }
                                                                }
                                                        }
                                                        catch (ClassNotFoundException e)
                                                        {
                                                                e.printStackTrace();
                                                        }
                                                }
                                        }
                                        return new ListResponse("", files);
                                }
                        }
                }
        }

        @Override
        public Response download(DownloadTicketRequest request) throws IOException
        {
                long fileSize = 0;
                int version = -1;
                this.setQuorums();

                InetAddress address = null;
                int port = 0;
                long usage = 0;

                if(username.equals(""))
                {
                        return new MessageResponse("Please log in first.");
                }
                else
                {
                        synchronized(readQuorum)
                        {
                                for (FileServerInfo i : readQuorum.values())
                                {
                                        // Request Info if FileServer has File
                                        try
                                        {
                                                // ------------------ Info ---------------------
                                                Socket socket = new Socket(i.getAddress(),i.getPort());
                                                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                                                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                                                InfoRequest infoTmp = new InfoRequest(request.getFilename());
                                                InfoRequest info = new InfoRequest(hMac.createHash(infoTmp.toString()),request.getFilename());
                                                out.writeObject(info);
                                                out.flush();
                                                // One Line is received and passed along to the Shell Commands:
                                                Response response = (Response)in.readObject();
                                                if(response instanceof MessageResponse)
                                                {
                                                        if(!hMac.verifyHash(((MessageResponse) response).gethMac(), response.toString()))
                                                        {
                                                                System.out.println("This message has been tampered with: " + response.toString());
                                                        }
                                                        if(response.toString().equals("!again"))
                                                        {
                                                                return download(request); // TODO check for endless
                                                        }
                                                }
                                                else
                                                {
                                                        InfoResponse inforesponse = (InfoResponse)response;
                                                        if(!hMac.verifyHash(inforesponse.gethMac(), inforesponse.toString()))
                                                        {
                                                                System.out.println("This message has been tampered with: " + inforesponse.toString());
                                                        }
                                                        if (inforesponse.getSize() == -1) {
                                                                // File does not exist on FileServer.
                                                                in.close();
                                                                out.close();
                                                                socket.close();
                                                                continue;
                                                        }
                                                        fileSize = ((InfoResponse)inforesponse).getSize();

                                                        in.close();
                                                        out.close();
                                                        socket.close();
                                                        
                                                        // if yes save Version and Fileserver
                                                        // ------------------ Version ------------------
                                                        Socket socket2 = new Socket(i.getAddress(),i.getPort());
                                                        ObjectOutputStream out2 = new ObjectOutputStream(socket2.getOutputStream());
                                                        ObjectInputStream in2 = new ObjectInputStream(socket2.getInputStream());
                                                        VersionRequest versionTmp = new VersionRequest(request.getFilename());
                                                        VersionRequest versionRequest = new VersionRequest(hMac.createHash(versionTmp.toString()), request.getFilename());
                                                        out2.writeObject(versionRequest);
                                                        out2.flush();
                                                        // One Line is received and passed along to the Shell Commands:
                                                        response = (Response)in2.readObject();
                                                        if(response instanceof MessageResponse)
                                                        {
                                                                if(!hMac.verifyHash(((MessageResponse) response).gethMac(), response.toString()))
                                                                {
                                                                        System.out.println("This message has been tampered with: " + response.toString());
                                                                }
                                                                if(response.toString().equals("!again"))
                                                                {
                                                                        out.writeObject(versionRequest);
                                                                        out.flush();
                                                                }
                                                        }
                                                        else
                                                        {
                                                                VersionResponse versionResponse = (VersionResponse)response;
                                                                if(!hMac.verifyHash(versionResponse.gethMac(), versionResponse.toString()))
                                                                {
                                                                        System.out.println("This message has been tampered with: " + versionResponse.toString());
                                                                }
                                                                int tmp = versionResponse.getVersion();
                                                                // Take Fileserver with highest Version
                                                                if (tmp == version) {
                                                                        if (i.getUsage() < usage) {
                                                                                version = tmp;
                                                                                usage = i.getUsage();
                                                                                address = i.getAddress();
                                                                                port = i.getPort();
                                                                        }
                                                                }
                                                                if (tmp > version) {
                                                                        version = tmp;
                                                                        usage = i.getUsage();
                                                                        address = i.getAddress();
                                                                        port = i.getPort();                                        
                                                                }
                                                        }
                                                        in2.close();
                                                        out2.close();
                                                        socket2.close();
                                                }
                                        } catch (ClassNotFoundException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                        }
                                }
                        }
                        if (version != -1) {
                                synchronized (serverIdentifier) {
                                        
                                        synchronized (users)
                                        {
                                                if (fileSize > users.get(username).getCredits())
                                                {
                                                        return new MessageResponse(
                                                                        "The file requires "
                                                                                        + String.valueOf(fileSize)
                                                                                        + " credits to download, but you only have "
                                                                                        + users.get(username).getCredits());
                                                }
                                                String checksum = ChecksumUtils.generateChecksum(
                                                                username, request.getFilename(), 0, fileSize);
                                                DownloadTicket ticket = new DownloadTicket(username,
                                                                request.getFilename(), checksum, address, port);
                                                UserInfo old = users.get(username);
                                                UserInfo info = new UserInfo(username, old.getCredits()
                                                                - fileSize, old.isOnline());
                                                users.put(username, info);
                                                serverIdentifier.put(port, new FileServerInfo(address,
                                                                port, usage + fileSize, true));
                                                
                                                if(downloadCounter.get(request.getFilename()) == null){
                                                	downloadCounter.put(request.getFilename(), 1);
                                                }else{
                                                	downloadCounter.put(request.getFilename(), downloadCounter.get(request.getFilename())+1);
                                                }
                                                
                                                this.proxyRMI.processDownloadCounterIncrease(request.getFilename(), downloadCounter.get(request.getFilename()));
                                                return new DownloadTicketResponse(ticket);
                                        }
                                }
                        }
                        else
                        {
                                return new MessageResponse(
                                                "The file you wanted to download doesn't exist");
                        }

                }
        }

        @Override
        public MessageResponse upload(UploadRequest request) throws IOException
        {
                setQuorums();
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
                                int version = -10;
                                synchronized(readQuorum)
                                {
                                        for(FileServerInfo f : readQuorum.values())
                                        {
                                                version = Math.max(version, getVersionNumberFromFileServer(request.getFilename(), f));
                                        }
                                }
                                System.out.println("Version before " + version);
                                if(version != -1)
                                {
                                        version++;
                                }
                                else
                                {
                                        version = 1;
                                }
                                System.out.println("Version after " + version);
                                synchronized(writeQuorum)
                                {
                                        for(FileServerInfo f : writeQuorum.values())
                                        {
                                                UploadRequest tmp = new UploadRequest("", request.getFilename(), version, request.getContent());
                                                UploadRequest updatedRequest = new UploadRequest(hMac.createHash(tmp.toString()), tmp.getFilename(), tmp.getVersion(), tmp.getContent());
                                                Socket socket = new Socket(f.getAddress(), f.getPort());
                                                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                                                oos.flush();
                                                ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                                                String s = new String(updatedRequest.getContent());

                                                oos.writeObject(updatedRequest);
                                                oos.flush();

                                                try
                                                {
                                                        MessageResponse response = (MessageResponse) ois.readObject();
                                                        if(!hMac.verifyHash(response.gethMac(), response.toString()))
                                                        {
                                                                System.out.println("This message has been tampered with: " + response.toString());
                                                        }
                                                        if(response.getMessage().equals("!again"))
                                                        {
                                                                return upload(request);
                                                        }
                                                }
                                                catch (ClassNotFoundException e)
                                                {
                                                        // TODO Auto-generated catch block
                                                        e.printStackTrace();
                                                }

                                                long fileSize = s.length();
                                                synchronized(users)
                                                {
                                                        UserInfo old = users.get(username);
                                                        UserInfo info = new UserInfo(username, old.getCredits()+fileSize*2, old.isOnline());
                                                        users.put(username, info);
                                                }
                                                oos.close();
                                                ois.close();
                                                socket.close();
                                                writeQuorum.put(f.getPort(), new FileServerInfo(f.getAddress(), f.getPort(), f.getUsage()+fileSize, true));
                                        }
                                }
                                synchronized(users)
                                {
                                        return new MessageResponse("Uploaded file to server, new Credits: "+users.get(username).getCredits());
                                }
                        }
                }
        }




        @Override
        public MessageResponse logout() throws IOException
        {
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
    				return new MessageResponse("Successfully logged out");
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
        
        private Request receiveRequest() throws IOException, ClassNotFoundException {
    		Request request = null;
    		if (this.aesChannel == null) {
    			byte[] message = (byte[]) this.objectInput.readObject();
    			request = (Request) Serialization.deserialize(message);
    		} else {
    			byte[] message = (byte[]) this.objectInput.readObject();
    			message = this.base64Channel.decode(message);
    			byte[] decrypted = this.aesChannel.decode(message);
    			request = (Request) Serialization.deserialize(decrypted);
    		}

    		return request;
    	}
        

    	private void sendResponse(Response response) throws SocketException,
    			EOFException, IOException {
    		if (this.loggedIn == false) {
    			byte[] toSend = Serialization.serialize(response);
    			this.objectOutput.writeObject(toSend);
    			this.objectOutput.flush();
    		} else {
    			byte[] toSend = Serialization.serialize(response);
    			byte[] encrypted = this.aesChannel.encode(toSend);
    			encrypted = this.base64Channel.encode(encrypted);
    			this.objectOutput.writeObject(encrypted);
    			this.objectOutput.flush();
    		}
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
        public void run()
        {
                while(!stop.get())
                {
                        try
                        {
                                Request message = this.receiveRequest();
                                if (message instanceof LoginRequestHandshake) {
            						LoginResponseHandshake response = this.loginHandshake((LoginRequestHandshake) message);
            						this.sendResponse(response);
            									
                                } else if (message instanceof LoginRequestFinalHandshake) {
            					//TODO: log in the user at the proxy machine.
            					this.sendResponse(this.checkIfChallengeIsOkay(message));
                                }
            					else if (message instanceof CreditsRequest)
                                {
                                        Response response = credits();
                                       this.sendResponse(response);
                                }
            					else if (message instanceof BuyRequest)
                                {
                                        Response response = buy((BuyRequest)message);
                                        this.sendResponse(response);

                                }
            					else if (message instanceof ListRequest)
                                {
                                        Response response = list();
                                        this.sendResponse(response);

                                }
            					else if (message instanceof DownloadTicketRequest)
                                {
                                        Response response = download((DownloadTicketRequest)message);
                                        this.sendResponse(response);

                                }
            					else if (message instanceof UploadRequest)
                                {
                                        MessageResponse response = upload((UploadRequest)message);
                                        this.sendResponse(response);

                                }
            					else if (message instanceof LogoutRequest)
                                {
                                        MessageResponse response = logout();
                                        this.sendResponse(response);

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
                                                UserInfo old = users.get(username);
                                                UserInfo info = new UserInfo(username, old.getCredits(), false);
                                                users.put(username, info);
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


        private void setQuorums()
        {
                HashMap<FileServerInfo, Integer> servers = new HashMap<FileServerInfo, Integer>();
                for(FileServerInfo f : serverIdentifier.values())
                {
                        servers.put(f, (int) f.getUsage());
                }

                servers = (HashMap<FileServerInfo, Integer>) sortByComparator(servers);

                int quorumSize = Math.round(servers.size()/2)+1;

                readQuorum = new ConcurrentHashMap<Integer, FileServerInfo>();
                writeQuorum = new ConcurrentHashMap<Integer, FileServerInfo>();

                int x = 0;

                for(FileServerInfo i : servers.keySet())
                {

                        if(x < quorumSize)
                        {
                                readQuorum.put(i.getPort(), i);
                                x++;
                        }
                        else
                        {
                                break;
                        }
                }
                x = 0;
                for(FileServerInfo i : servers.keySet())
                {

                        if(x < quorumSize)
                        {
                                writeQuorum.put(i.getPort(), i);
                                x++;
                        }
                        else
                        {
                                break;
                        }
                }
                try {
					this.proxyRMI.setQuorums(readQuorum.size(), writeQuorum.size());
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        }

        private int getVersionNumberFromFileServer(String filename, FileServerInfo info)
        {
                int version = -1;
                try
                {
                        Socket socket = new Socket(info.getAddress(), info.getPort());
                        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                        oos.flush();
                        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                        VersionRequest tmp = new VersionRequest(filename);
                        VersionRequest request = new VersionRequest(hMac.createHash(tmp.toString()), filename);
                        oos.writeObject(request);
                        oos.flush();
                        Response response = (Response)ois.readObject();
                        if (response instanceof MessageResponse)
                        {
                                System.out.println(((MessageResponse)response).gethMac());
                                System.out.println(response.toString());
                                if (!hMac.verifyHash(((MessageResponse)response).gethMac(), response.toString()))
                                {
                                        System.out.println("This message has been tampered with: " + response.toString());
                                }
                                if (response.toString().equals("!again"))
                                {
                                        oos.writeObject(request);
                                        oos.flush();
                                }
                        }
                        if (response instanceof VersionResponse)
                        {
                                version = ((VersionResponse) response).getVersion();
                        }
                        oos.close();
                        ois.close();
                        socket.close();
                }
                catch (IOException e)
                {
                        e.printStackTrace();
                }
                catch (ClassNotFoundException e)
                {
                        e.printStackTrace();
                }
                return version;
        }

        private static Map sortByComparator(Map unsortMap) {

                List list = new LinkedList(unsortMap.entrySet());

                // sort list based on comparator
                Collections.sort(list, new Comparator() {
                        public int compare(Object o1, Object o2) {
                                return ((Comparable) ((Map.Entry) (o1)).getValue())
                                                .compareTo(((Map.Entry) (o2)).getValue());
                        }
                });

                // put sorted list into map again
                //LinkedHashMap make sure order in which keys were inserted
                Map sortedMap = new LinkedHashMap();
                for (Iterator it = list.iterator(); it.hasNext();) {
                        Map.Entry entry = (Map.Entry) it.next();
                        sortedMap.put(entry.getKey(), entry.getValue());
                }
                return sortedMap;
        }
}
