package client;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import org.bouncycastle.openssl.PEMReader;
import security.Base64Channel;
import security.Channel;
import security.RSAChannel;
import util.Config;

import message.request.LoginRequestHandshake;
import message.response.LoginResponseHandshake;

public class LoginHandshake {

	private final String B64 = "a-zA-Z0-9/+";
	private Channel base64Channel;
	private final PrivateKey privateKey;
	private byte[] clientChallenge;

	public LoginHandshake(PrivateKey privateKey) {
		this.base64Channel = new Base64Channel();
		this.privateKey = privateKey;
	}
		
	public LoginRequestHandshake startHandshake(String username) {

		byte[] encodedUsername = this.base64Channel.encode(username.getBytes()); // encoded
		byte[] encodedClientChallenge = this.createClientChallenge(); // encoded
		PublicKey publicKey = this.importPublicKeyProxy();

		Channel rsaChannel = new RSAChannel(publicKey);

		encodedUsername = rsaChannel.encode(encodedUsername); // encode and encrypted
		encodedClientChallenge = rsaChannel.encode(encodedClientChallenge); // encoded and encrypted

		String encodedAndEncryptedUsername = new String(this.base64Channel.encode(encodedUsername)); // encoded, encrypted, encoded
		String encodedAndEncryptedClientChallenge = new String(this.base64Channel.encode(encodedClientChallenge)); // encoded, encrypted, encoded

		return new LoginRequestHandshake(encodedAndEncryptedUsername, encodedAndEncryptedClientChallenge);
	}

	public AesProperties finishHandshake(LoginResponseHandshake loginResponse){
		byte[] ivParam = null;
		byte[] secretKey = null;

		String okMessage = loginResponse.toString();
		byte[] okMessageDecoded = this.base64Channel.decode(okMessage);

		Channel rsaChannel = new RSAChannel(this.privateKey);

		okMessage = new String(rsaChannel.decode(okMessageDecoded));

		String[] splittedMessage = null;

		if(okMessage.startsWith("!ok")){
			splittedMessage = okMessage.split(" ");
			
			if(splittedMessage.length == 5){
				
				//returns true if the client challenge that came back is the one that was sent
				if(!splittedMessage[1].equals(new String(this.clientChallenge))){
					System.err.println("Error: clientChallenge is not correct");
					return null;
				}
				
				ivParam = this.base64Channel.decode(splittedMessage[4]);
				secretKey = this.base64Channel.decode(splittedMessage[3]);
			}
			else{
				System.err.println("Error: ok Message isn't formatted correctly");
				return null;
			}
		}
		
		assert splittedMessage[2].matches("["+B64+"]{43}=") : "3rd message";
		
		AesProperties aesProperties = new AesProperties(ivParam, secretKey, splittedMessage[2]);
		
		return aesProperties;
	}

	/**
	 * generates an encoded(!), random clientChallenge
	 * 
	 * @return clientChallenge as an encoded String
	 * 
	 * */
	private byte[] createClientChallenge() {
		final byte[] randomNumber = new byte[32];

		SecureRandom secureRandom = new SecureRandom();
		secureRandom.nextBytes(randomNumber);

		this.clientChallenge = this.base64Channel.encode(randomNumber);

		return clientChallenge;

	}

	private PublicKey importPublicKeyProxy() {
		Config config = new Config("client");
		String pathToPublicKey = config.getString("proxy.key");

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
