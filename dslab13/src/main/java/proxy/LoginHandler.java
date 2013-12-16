package proxy;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.bouncycastle.openssl.PEMReader;

import security.Base64Channel;
import security.Channel;
import security.RSAChannel;
import util.Config;
import message.request.LoginRequestHandshake;
import message.response.LoginResponseHandshake;

public class LoginHandler {
	
	private Base64Channel base64Channel;
	
	public LoginHandler(){
		this.base64Channel = new Base64Channel();
	}
	
	private byte[] decodeAndDecrypt(String toDeDe){
		Channel rsaChannel = new RSAChannel();
		byte[] decoded = this.base64Channel.decode(toDeDe,null);
		return rsaChannel.decode(decoded, ProxyMain.privateKey);
	}
	
	private String encryptAndEncode(String toEnEn, PublicKey publicKey){
		Channel rsaChannel = new RSAChannel();
		
		byte[] encrypted = rsaChannel.encode(toEnEn, publicKey);
		return new String( this.base64Channel.encode(encrypted, null));
	}

	public LoginResponseHandshake sendBackHandshake(LoginRequestHandshake loginRequest){
			
		byte[] clientChallenge = this.decodeAndDecrypt(loginRequest.getPassword()); //decoded, decrypted (NOT decoded again!!!)
		byte[] username = this.decodeAndDecrypt(loginRequest.getUsername()); //decoded, decrypted (NOT decoded again!!!)
		byte[] proxyChallenge = this.createSecureNumber(32); //encoded;
		byte[] ivParam = this.createIVParameter(); //encoded;
		byte[] secretKey = this.createSecretKey(); //encoded; 
		
		String usernameDecoded = new String(base64Channel.decode(username, null)); //TODO: ATTENTION! is new String applicable here??
		
		PublicKey publicKey = this.importPublicKey(usernameDecoded);
		
		String encryptedAndEncodedMessage = encryptAndEncode("!ok " + new String(clientChallenge) + " " + new String(proxyChallenge) + " " + " " + new String(secretKey) + " "
				+ new String(ivParam), publicKey); //Encoded, encrypted, encoded
		
		LoginResponseHandshake loginResponse = new LoginResponseHandshake(encryptedAndEncodedMessage);
		
		return loginResponse;
	}
	
	/**
	 * Generates secure random number for serverchallenge
	 * 
	 * @return already base64 encoded secure random number
	 * */
	
	private byte[] createSecureNumber(int length){
		byte[] secureRandomNumber;
		final byte[] randomNumber = new byte[length]; 

		SecureRandom secureRandom = new SecureRandom(); 
		secureRandom.nextBytes(randomNumber);

		secureRandomNumber = this.base64Channel.encode(randomNumber, null);

		return secureRandomNumber;

	}
	
	private byte[] createSecretKey(){
		KeyGenerator generator = null;
		try {
			generator = KeyGenerator.getInstance("AES");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("ERROR - No Such Algorithm - AES");
		} 
		// KEYSIZE is in bits 
		generator.init(256); 
		SecretKey key = generator.generateKey();

		return base64Channel.encode(key.getEncoded(), null); //ATTENTION
	}
	
	private byte[] createIVParameter(){
		IvParameterSpec ivParam = new IvParameterSpec(this.createSecureNumber(16));
	
		byte[] ivParameter = base64Channel.encode(ivParam.getIV(), null);
		
		return ivParameter;
	}

	private PublicKey importPublicKey(String username){
		Config config = new Config("proxy");
		System.out.println(config.getString("keys.dir") + " is there a slash afterwards? if yes, it is okay");
		String pathToPublicKey = config.getString("keys.dir")+ "/" + username + ".pub.pem";
		
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
