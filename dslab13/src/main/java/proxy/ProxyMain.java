package proxy;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.util.Iterator;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;

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
