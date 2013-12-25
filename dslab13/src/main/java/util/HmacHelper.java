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
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
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
		} catch (NoSuchAlgorithmException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] generatedHash = createHash(originalText).getBytes();
		return MessageDigest.isEqual(channel.decode(computedHash), channel.decode(generatedHash));
	}
}
