package server;

import util.Config;
import cli.Shell;

public class FileServerMain1
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		Shell shell = new Shell("fs1", System.out, System.in);
		FileServerCli serverCli = new FileServerCli(new Config("fs1"), shell);
		shell.register(serverCli);
		Thread t = new Thread(shell);
		t.start();
	}

}
