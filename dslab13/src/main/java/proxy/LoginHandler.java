package proxy;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SecureRandom;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;

import security.Base64Channel;
import security.Channel;
import security.RSAChannel;
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

	public LoginResponseHandshake sendBackHandshake(LoginRequestHandshake loginRequest){
	
		Channel rsaChannel = new RSAChannel();
		final PrivateKey privateKey = ProxyMain.privateKey;
		
		byte[] clientChallenge = this.decodeAndDecrypt(loginRequest.getPassword()); //decoded, decrypted (NOT decoded again!!!)
		byte[] username = this.decodeAndDecrypt(loginRequest.getUsername()); //decoded, decrypted (NOT decoded again!!!)
		
	}
	
	private String createServerChallenge(){
		String clientChallenge;
		final byte[] randomNumber = new byte[32]; 

		// generates a 32 byte secure random number 

		SecureRandom secureRandom = new SecureRandom(); 
		secureRandom.nextBytes(randomNumber);

		clientChallenge = base64Channel.encode(randomNumber, null);

		return clientChallenge;

	}
	
}
