package message.request;

import message.Request;
import model.DownloadTicket;

/**
 * Downloads the file for the given {@link DownloadTicket}.
 * <p/>
 * <b>Request (client to server)</b>:<br/>
 * {@code !download &lt;ticket&gt;}<br/>
 * <b>Response (server to client):</b><br/>
 * {@code !data &lt;content&gt;}<br/>
 *
 * @see message.response.DownloadFileResponse
 */
public class DownloadFileRequest implements Request {
	private static final long serialVersionUID = 210648071424508878L;

	private final DownloadTicket ticket;
	private final String hMac;

	public DownloadFileRequest(DownloadTicket ticket) {
		this.hMac = "";
		this.ticket = ticket;
	}
	
	public DownloadFileRequest(String hMac, DownloadTicket ticket) {
		this.hMac = hMac;
		this.ticket = ticket;
	}

	public String gethMac() 
	{
		return hMac;
	}
	
	public DownloadTicket getTicket() {
		return ticket;
	}

	@Override
	public String toString() {
		return hMac + "!download " + ticket;
	}
}
