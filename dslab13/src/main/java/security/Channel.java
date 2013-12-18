package security;


public interface Channel {
	
	public byte[] decode(String toDecode);
	
	public byte[] encode(String toEncode);
	
	public byte[] encode(byte[] toEncode);
	
	public byte[] decode(byte[] toDecode);
	

}
