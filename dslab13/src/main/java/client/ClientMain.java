package client;

import cli.Shell;
import util.Config;

public class ClientMain
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		Shell shell = new Shell("client", System.out, System.in);
		ClientCli client = new ClientCli(new Config("client"), shell);
		shell.register(client);
		Thread t = new Thread(shell);
		t.start();
	}
}
