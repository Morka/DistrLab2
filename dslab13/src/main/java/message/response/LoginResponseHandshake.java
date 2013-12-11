package message.response;

import message.Response;

public class LoginResponseHandshake implements Response{

	private static final long serialVersionUID = -5583451886815696246L;
	
	private final String clientChallenge;
	private final String proxyChallenge;
}
