package message.request;

import java.security.PublicKey;

import message.Request;

public class PublicKeySetRequest implements Request{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private PublicKey publicKey;
	
	public PublicKeySetRequest(PublicKey publicKey) {
		this.publicKey = publicKey;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	@Override
	public String toString() {
		return "PublicKeySetRequest [publicKey=" + publicKey + "]";
	}
}
