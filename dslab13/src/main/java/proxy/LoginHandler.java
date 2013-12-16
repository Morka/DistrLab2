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
	private byte[] ivParam;
	private byte[] secretKey;
	private byte[] proxyChallenge;
	private String usernameDecoded;
	
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
	
	/**
	 * Takes a login requests and creates all the necessary challenges for the "third handshake"
	 * */

	public LoginResponseHandshake sendBackHandshake(LoginRequestHandshake loginRequest){
			
		byte[] clientChallenge = this.decodeAndDecrypt(loginRequest.getPassword()); //decoded, decrypted (NOT decoded again!!!)
		byte[] username = this.decodeAndDecrypt(loginRequest.getUsername()); //decoded, decrypted (NOT decoded again!!!)
		byte[] proxyChallenge = this.createSecureNumber(); //encoded;
		byte[] ivParamEncoded = this.createIVParameter(); //encoded;
		byte[] secretKeyEncoded = this.createSecretKey(); //encoded; 
		
		this.usernameDecoded = new String(base64Channel.decode(username, null)); //TODO: ATTENTION! is new String applicable here??
		
		PublicKey publicKey = this.importPublicKey(this.usernameDecoded);
		
		String encryptedAndEncodedMessage = encryptAndEncode("!ok " + new String(clientChallenge) + " " + new String(proxyChallenge) + " " + new String(secretKeyEncoded) + " "
				+ new String(ivParamEncoded), publicKey); //Encoded, encrypted, encoded
		
		LoginResponseHandshake loginResponse = new LoginResponseHandshake(encryptedAndEncodedMessage);
		
		return loginResponse;
	}
	
	public byte[] getProxyChallenge(){
		return proxyChallenge;
	}
	
	public byte[] getIVParam(){
		return this.ivParam;
	} 
	
	public byte[] getSecretKey(){
		return this.secretKey;
	}
	
	public String getUsername(){
		return this.usernameDecoded;
	}
	
	/**
	 * Generates secure random number for serverchallenge
	 * 
	 * @return already base64 encoded secure random number
	 * */
	
	private byte[] createSecureNumber(){
		byte[] secureRandomNumber;
		final byte[] randomNumber = new byte[32]; 

		SecureRandom secureRandom = new SecureRandom(); 
		secureRandom.nextBytes(randomNumber);
		
		secureRandomNumber = this.base64Channel.encode(randomNumber, null);
		
		this.proxyChallenge = secureRandomNumber;
		
		return secureRandomNumber;

	}
	
	private byte[] createSecureNumberForIV(){
		final byte[] randomNumber = new byte[16]; 

		SecureRandom secureRandom = new SecureRandom(); 
		secureRandom.nextBytes(randomNumber);
				
		return randomNumber;

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
		
		this.secretKey = key.getEncoded();
		

		return base64Channel.encode(secretKey, null); //ATTENTION
	}
	
	private byte[] createIVParameter(){
		IvParameterSpec ivParamSpec = new IvParameterSpec(this.createSecureNumberForIV());
	
		this.ivParam = ivParamSpec.getIV();
		
		byte[] ivParameter = base64Channel.encode(this.ivParam, null);
		
		return ivParameter;
	}

	private PublicKey importPublicKey(String username){
		Config config = new Config("proxy");
		System.out.println(config.getString("keys.dir") + " *** is there a slash afterwards? if yes, it is okay");
		String pathToPublicKey = config.getString("keys.dir")+ "/" + username + ".pub.pem";
		
		System.out.println("Path to Key: " + pathToPublicKey);
		
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
