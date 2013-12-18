package security;


import org.bouncycastle.util.encoders.Base64;

public class Base64Channel implements Channel{
	/*	

	public String encode(String input, Key key){
		byte[] base64Message;
		byte[] encryptedMessage;
		
		encryptedMessage = input.getBytes();
		base64Message = Base64.encode(encryptedMessage);
		
		return new String(base64Message);
	}*/
	
	public byte[] encode(byte[] input){
		byte[] base64Message;
	
		base64Message = Base64.encode(input);
		
		return base64Message;
	}
	
	public byte[] decode(String encodedMessage){
		return this.decode(encodedMessage.getBytes());
	}

	@Override
	public byte[] encode(String toEncode) {
		return this.encode(toEncode.getBytes());
	}

	@Override
	public byte[] decode(byte[] toDecode) {
		byte[]decodedMessage;
		
		decodedMessage = Base64.decode(toDecode);
		
		return decodedMessage;
	}
}