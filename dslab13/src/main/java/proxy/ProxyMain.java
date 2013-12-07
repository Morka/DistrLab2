package proxy;

import util.Config;
import cli.Shell;

public class ProxyMain
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		Shell shell = new Shell("proxy", System.out, System.in);
		ProxyCli proxy = new ProxyCli(new Config("proxy"), shell);
		shell.register(proxy);
		Thread t = new Thread(shell);
		t.start();
	}

}
