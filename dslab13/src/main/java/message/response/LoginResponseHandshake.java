package message.response;

import message.Response;

public class LoginResponseHandshake implements Response{

	private static final long serialVersionUID = -5583451886815696246L;
	
	private final String message;
	
	public LoginResponseHandshake(String message){
		this.message = message;
	}
	
	public String toString(){
		return message;
	}
	
}
