package message.response;

import message.Response;

/**
 * Sends a custom message to the receiver.
 */
public class MessageResponse implements Response {
	private static final long serialVersionUID = 4550680230065708876L;

	private final String hMac;
	private final String message;

	public MessageResponse(String message) {
		this.message = message;
		this.hMac = "";
	}
	
	public MessageResponse(String hMac, String message) {
		this.message = message;
		this.hMac = hMac;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public String toString() {
		return getMessage();
	}
	
	public String gethMac() {
		return hMac;
	}
}
