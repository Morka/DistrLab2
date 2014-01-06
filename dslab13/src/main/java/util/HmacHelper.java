package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import security.Base64Channel;

public class HmacHelper
{
	private Key key;
	private Mac hMac;
	private Base64Channel channel;
	
	public HmacHelper(Config config)
	{
		channel = new Base64Channel();
		File hKey = new File(config.getString("hmac.key"));
		if(hKey.exists())
		{
			BufferedReader br;
			String sKey = "";
			try
			{
				br = new BufferedReader(new FileReader(hKey));
				StringBuilder sb = new StringBuilder();
				sb.append(br.readLine());
				sKey = sb.toString();
				br.close();
			} 
			catch (FileNotFoundException e)
			{
				System.err.println("Error Could not find File for HMac key");
			} 
			catch (IOException e)
			{
				System.err.println("Error reading HMac Key");
			}
			key = new SecretKeySpec(sKey.getBytes(), "HmacSHA256");
			try
			{
				hMac = Mac.getInstance("HmacSHA256");
				hMac.reset();
				hMac.init(key);
				
			} 
			catch (NoSuchAlgorithmException e)
			{
				System.err.println("Error Hmac could not be created");
			} catch (InvalidKeyException e)
			{
				System.err.println("Error invalid key for HMac");
			}
		}
		else
		{
			throw new IllegalArgumentException("File not found.");
		}
	}
	
	public String createHash(String message)
	{
		try
		{
			hMac = Mac.getInstance("HmacSHA256");
			hMac.reset();
			hMac.init(key);
		}	catch (NoSuchAlgorithmException e)
			{
				System.err.println("Error Hmac could not be created");
			} catch (InvalidKeyException e)
			{
				System.err.println("Error invalid key for HMac");
			}
		byte[] hash = hMac.doFinal();
		return new String(channel.encode(hash)) + " " + message;
	}
	
	public boolean verifyHash(String computedHash, String originalText)
	{	
		try
		{
			hMac = Mac.getInstance("HmacSHA256");
			hMac.reset();
			hMac.init(key);
		} catch (NoSuchAlgorithmException e)
		{
			System.err.println("Error Hmac could not be created");
		} catch (InvalidKeyException e)
		{
			System.err.println("Error invalid key for HMac");
		}
		byte[] generatedHash = createHash(originalText).getBytes();
		return MessageDigest.isEqual(channel.decode(computedHash), channel.decode(generatedHash));
	}
}
