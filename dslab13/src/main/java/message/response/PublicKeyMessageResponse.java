package message.response;

import java.security.PublicKey;

import message.Response;

public class PublicKeyMessageResponse implements Response{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 8188162068465970244L;
	private PublicKey publicKey;
	
	public PublicKeyMessageResponse(PublicKey publicKey){
		this.publicKey = publicKey;
	}
	
	public PublicKey getPublicKey(){
		return publicKey;
	}

	@Override
	public String toString() {
		return "Successfully received public key of Proxy.";
	}
	
	
}
