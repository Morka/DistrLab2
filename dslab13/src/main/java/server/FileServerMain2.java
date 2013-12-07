package server;

import proxy.ProxyCli;
import util.Config;
import cli.Shell;

public class FileServerMain2
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		Shell shell = new Shell("fs2", System.out, System.in);
		FileServerCli serverCli = new FileServerCli(new Config("fs2"), shell);
		shell.register(serverCli);
		Thread t = new Thread(shell);
		t.start();
	}

}
