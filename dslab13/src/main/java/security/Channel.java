package security;

import java.security.Key;

public interface Channel {
	
	public byte[] decode(String toDecode, Key key);
	
	public byte[] encode(String toEncode, Key key);
	
	public byte[] encode(byte[] toEncode, Key key);
	
	public byte[] decode(byte[] toDecode, Key key);
	

}
