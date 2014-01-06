package proxy;

import client.ICallbackObject;

public class CallbackProperties {

	private String filename;
	private int numberOfDownlaods;
	private ICallbackObject callback;
	
	public CallbackProperties(String filename, int numberOfDownloads, ICallbackObject callback){
		this.filename = filename;
		this.numberOfDownlaods = numberOfDownloads;
		this.callback = callback;
	}
	
	public String getFilename() {
		return filename;
	}

	public int getNumberOfDownlaods() {
		return numberOfDownlaods;
	}

	public ICallbackObject getCallback() {
		return callback;
	}
	
}
