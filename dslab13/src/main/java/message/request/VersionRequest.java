package message.request;

import message.Request;

/**
 * Retrieves the highest available version number of a particular file on a certain server.
 * <p/>
 * <b>Request</b>:<br/>
 * {@code !version &lt;filename&gt;}<br/>
 * <b>Response:</b><br/>
 * {@code !version &lt;filename&gt; &lt;version&gt;}<br/>
 *
 * @see message.response.VersionResponse
 */
public class VersionRequest implements Request {
	private static final long serialVersionUID = 3995314039957433479L;

	private final String filename;
	private final String hMac;

	public VersionRequest(String filename) {
		this.filename = filename;
		this.hMac = "";
	}

	public VersionRequest(String hMac, String filename)
	{
		this.filename = filename;
		this.hMac = hMac;
	}

	public String getFilename() {
		return filename;
	}
	
	public String gethMac() {
		return hMac;
	}

	@Override
	public String toString() {
		return "!version " + getFilename();
	}
}
