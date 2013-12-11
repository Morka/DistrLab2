package client;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.PublicKey;
import java.security.SecureRandom;

import org.bouncycastle.openssl.PEMReader;

import security.Base64Channel;
import security.Channel;
import security.RSAChannel;

import message.request.LoginRequestHandshake;

public class LoginHandshake {
	
	private Channel base64Channel;
	
	
	public LoginHandshake(){
		base64Channel = new Base64Channel();
	}

	
	public LoginRequestHandshake startHandshake(String username){
		
		byte[] encodedUsername = this.base64Channel.encode(username.getBytes(), null); //encoded
		byte[] encodedClientChallenge = this.createClientChallenge(); //encoded
		PublicKey publicKey = this.importPublicKeyAuctionServer();
		
		Channel rsaChannel = new RSAChannel();
		
		encodedUsername = rsaChannel.encode(encodedUsername, publicKey); //encoded and encrypted
		encodedClientChallenge = rsaChannel.encode(encodedClientChallenge, publicKey); //encoded and encrypted
		
		String encodedAndEncryptedUsername = new String(base64Channel.encode(encodedUsername, null)); //encoded, encrypted, encoded
		String encodedAndEncryptedClientChallenge = new String(base64Channel.encode(encodedClientChallenge, null)); //encoded, encrypted, encoded
		
		return new LoginRequestHandshake(encodedAndEncryptedUsername, encodedAndEncryptedClientChallenge);
	}
	
	/**
	 * generates an encoded(!), random clientChallenge
	 * 
	 * @return clientChallenge as an encoded String
	 * 
	 * */
	private byte[] createClientChallenge(){
		final byte[] randomNumber = new byte[32]; 

		SecureRandom secureRandom = new SecureRandom(); 
		secureRandom.nextBytes(randomNumber);

		byte[] clientChallenge = this.base64Channel.encode(randomNumber, null);
		
		return clientChallenge;

	} 
	
	private PublicKey importPublicKeyAuctionServer(){	
		//TODO: Fix static import of keys!
		
		String pathToPublicKey = "/home/nief/Documents/Studium/6S_WS13/Distributed Systems/Lab3/DistrLab2/dslab13/keys/proxy.pub.pem";
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
}