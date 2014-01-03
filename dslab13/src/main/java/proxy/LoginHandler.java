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
	
	private final String B64 = "a-zA-Z0-9/+"; 
	private Base64Channel base64Channel;
	private byte[] ivParam;
	private byte[] secretKey;
	private byte[] proxyChallenge;
	private String usernameDecoded;
	
	public LoginHandler(){
		this.base64Channel = new Base64Channel();
	}
	
	private byte[] decodeAndDecrypt(String toDeDe){
		Channel rsaChannel = new RSAChannel(ProxyMain.privateKey);
		byte[] decoded = this.base64Channel.decode(toDeDe);
		return rsaChannel.decode(decoded);
	}
	
	private String encryptAndEncode(String toEnEn, PublicKey publicKey){
		Channel rsaChannel = new RSAChannel(publicKey);
		
		byte[] encrypted = rsaChannel.encode(toEnEn);
		return new String( this.base64Channel.encode(encrypted));
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
		
		
		
		this.usernameDecoded = new String(base64Channel.decode(username)); //TODO: ATTENTION! is new String applicable here??
		
		assert this.usernameDecoded.matches("["+B64+"]{1,24}");
		
		PublicKey publicKey = this.importPublicKey(this.usernameDecoded);
		
		String okMessage = "!ok " + new String(clientChallenge) + " " + new String(proxyChallenge) + " " + new String(secretKeyEncoded) + " "
				+ new String(ivParamEncoded);
		
		assert okMessage.matches("!ok ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{22}==");
		
		String encryptedAndEncodedMessage = encryptAndEncode(okMessage, publicKey); //Encoded, encrypted, encoded
		
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
		
		secureRandomNumber = this.base64Channel.encode(randomNumber);
		
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
		

		return base64Channel.encode(secretKey); //ATTENTION
	}
	
	private byte[] createIVParameter(){
		IvParameterSpec ivParamSpec = new IvParameterSpec(this.createSecureNumberForIV());
	
		this.ivParam = ivParamSpec.getIV();
		
		byte[] ivParameter = base64Channel.encode(this.ivParam);
		
		return ivParameter;
	}

	private PublicKey importPublicKey(String username){
		Config config = new Config("proxy");
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
