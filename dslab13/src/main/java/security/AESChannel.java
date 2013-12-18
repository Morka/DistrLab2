package security;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class AESChannel implements Channel {
	private byte[] ivParameter;
	private byte[] secretKey;

	public AESChannel(byte[] ivParameter, byte[] secretKey) {
		this.ivParameter = ivParameter;
		this.secretKey = secretKey;
	}

	public byte[] encode(String stringToEncrypt) {
		return this.encode(stringToEncrypt.getBytes());
	}

	/**
	 * Takes a DECODED string an decrypts it
	 * */

	public byte[] decode(String stringToDecrypt) {
		return this.decode(stringToDecrypt.getBytes());
	}

	@Override
	public byte[] encode(byte[] bytesToEncrypt) {
		SecretKey secretKey = null;
		IvParameterSpec ivSpec = null;
		try {
			secretKey = new SecretKeySpec(this.secretKey, "AES/CTR/NoPadding");
			ivSpec = new IvParameterSpec(this.ivParameter);

		} catch (IllegalArgumentException ex) {
			System.err.println("Error formating key");
		}

		Cipher crypt = null;

		try {
			crypt = Cipher.getInstance("AES/CTR/NoPadding");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("ERROR: No Such Algorithm - cipher");
		} catch (NoSuchPaddingException e) {
			System.err.println("ERROR: NO Such Padding - cipher");
		}

		try {
			crypt.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
		} catch (InvalidKeyException e) {
			System.err.println("ERROR: Invalid AES Key");
		} catch (InvalidAlgorithmParameterException e) {
			System.err.println("ERROR: Invalid AES IV parameter");
		}

		byte[] encryptedNotEncoded = null;

		try {
			encryptedNotEncoded = crypt.doFinal(bytesToEncrypt);
		} catch (IllegalBlockSizeException e) {
			System.err
					.println("ERROR: trying to doFinal() - illegal Block Size");
		} catch (BadPaddingException e) {
			System.err.println("ERROR: trying to doFinal() - Bad Padding");
		}

		return encryptedNotEncoded; // this is encrypted but not encoded
	}

	@Override
	public byte[] decode(byte[] bytesToDecrypt) {
		IvParameterSpec ivSpec = null;
		SecretKey skey = null;

		try {
			skey = new SecretKeySpec(this.secretKey, "AES/CTR/NoPadding");

			ivSpec = new IvParameterSpec(this.ivParameter);

		} catch (IllegalArgumentException ex) {
			System.err.println("Error formating key");
		}

		Cipher crypt = null;

		try {
			crypt = Cipher.getInstance("AES/CTR/NoPadding");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("ERROR: No Such Algorithm - cipher");
		} catch (NoSuchPaddingException e) {
			System.err.println("ERROR: NO Such Padding - cipher");
		}

		try {
			crypt.init(Cipher.DECRYPT_MODE, skey, ivSpec);
		} catch (InvalidKeyException e) {
			System.err.println("ERROR: Invalid AES Key");
		} catch (InvalidAlgorithmParameterException e) {
			System.err.println("ERROR: Invalid AES IV parameter " +e.getMessage());
		}

		try {
			bytesToDecrypt = crypt.doFinal(bytesToDecrypt);
		} catch (IllegalBlockSizeException e) {
			System.err.println("ERROR: trying to doFinal() - illegal Block Size");
			e.printStackTrace();

		} catch (BadPaddingException e) {
			System.err.println("ERROR: trying to doFinal() - Bad Padding");
			e.printStackTrace();

		}

		return bytesToDecrypt;
	}
}