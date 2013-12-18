package security;


import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.Key;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class RSAChannel implements Channel{

	private Key key;
	
	public RSAChannel(Key key){
		this.key = key;
	}

	/**
	 * this method encrypts a string with the given RSA key
	 * 
	 * @param stringToEncrypt: bytes that ought to be encrypted
	 * 		  key: rsa public key
	 * 
	 * @return byte array of the encrypted String. make sure it is encoded via base64 afterwards 
	 * */
	
	public byte[] encode(String stringToEncrypt){
		return this.encode(stringToEncrypt.getBytes());
	}
	
	public byte[] encode(byte[] bytesToEncrypt){
		PublicKey publicKey = (PublicKey)key;

		//String encryptedAndEncoded = "";

		Cipher crypt = null;

		try {
			crypt = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("ERROR: No Such Algorithm - cipher");
		} catch (NoSuchPaddingException e) {
			System.err.println("ERROR: NO Such Padding - cipher");
		} 

		try {
			crypt.init(Cipher.ENCRYPT_MODE, publicKey);
		} catch (InvalidKeyException e) {
			System.err.println("ERROR: Invalid Key");
		} 

		try {
			bytesToEncrypt = crypt.doFinal(bytesToEncrypt);
		} catch (IllegalBlockSizeException e) {
			System.err.println("ERROR: trying to doFinal() - illegal Block Size");
		} catch (BadPaddingException e) {
			System.err.println("ERROR: trying to doFinal() - Bad Padding");
		}	
		//encryptedAndEncoded = base64Channel.encode(bytesToEncrypt, null);
		//return encryptedAndEncoded;
		return bytesToEncrypt; //encrypted Bytes. 
	}
	
	/**
	 * takes and encrypted BUT decoded string, and decrypts it via privateKey
	 * 
	 * @param stringToDecrypt: decoded(!) string, that needs to be decrypted
	 * 		  key: private key for decryption
	 * */

	public byte[] decode(String stringToDecrypt){

		//bytesToDecrypt = (base64Channel.decode(stringToDecrypt, null));
		return this.decode(stringToDecrypt.getBytes());
	}
	
	public byte[] decode(byte[] bytesToDecrypt){
		PrivateKey privateKey = (PrivateKey)key;

		//bytesToDecrypt = (base64Channel.decode(stringToDecrypt, null));
		
		Cipher crypt = null;

		try {
			crypt = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("ERROR: No Such Algorithm - cipher Decrypting");
		} catch (NoSuchPaddingException e) {
			System.err.println("ERROR: NO Such Padding - cipher Decrypting");
		}  
		
		try {
			//DECRYPT
			crypt.init(Cipher.DECRYPT_MODE, privateKey);
		} catch (InvalidKeyException e) {
			System.err.println("ERROR: Invalid Key - Decrypting");
		}		

		try {
			bytesToDecrypt = crypt.doFinal(bytesToDecrypt);
		
		} catch (IllegalBlockSizeException e) {
			System.err.println("ERROR: trying to doFinal() - illegal Block Size - Decrypting");
		} catch (BadPaddingException e) {
			System.err.println("ERROR: trying to doFinal() - Bad Padding - Decrypting");
			e.printStackTrace();
		}		

		return bytesToDecrypt;
	}
}