package client;

public class AesProperties {

	private byte[] ivParam;
	private byte[] secretKey;
	private String challenge;

	public AesProperties(byte[] ivParam, byte[] secretKey, String challenge){
		this.ivParam = ivParam;
		this.secretKey = secretKey;
		this.challenge = challenge;
	}
	
	public byte[] getIvParam(){
		return ivParam;
	}
	
	public byte[] getSecretKey(){
		return secretKey;
	}
	
	public String getChallenge(){
		return challenge;
	}
}
