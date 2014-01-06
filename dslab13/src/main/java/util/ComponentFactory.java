package util;

import proxy.IProxyCli;
import proxy.ProxyCli;
import cli.Shell;
import client.ClientCli;
import client.IClientCli;
import server.FileServerCli;
import server.IFileServerCli;
import util.Config;

/**
 * Provides methods for starting an arbitrary amount of various components.
 */
public class ComponentFactory {
	/**
	 * Creates and starts a new client instance using the provided {@link Config} and {@link Shell}.
	 *
	 * @param config the configuration containing parameters such as connection info
	 * @param shell  the {@code Shell} used for processing commands
	 * @return the created component after starting it successfully
	 * @throws Exception if an exception occurs
	 */
	public IClientCli startClient(Config config, Shell shell) throws Exception {
		// TODO: create a new client instance (including a Shell) and start it
		ClientCli client = new ClientCli(config, shell);
		shell.register(client);
		Thread t = new Thread(shell);
		t.start();
		return client;
	}

	/**
	 * Creates and starts a new proxy instance using the provided {@link Config} and {@link Shell}.
	 *
	 * @param config the configuration containing parameters such as connection info
	 * @param shell  the {@code Shell} used for processing commands
	 * @return the created component after starting it successfully
	 * @throws Exception if an exception occurs
	 */
	public IProxyCli startProxy(Config config, Shell shell) throws Exception {
		// TODO: create a new proxy instance (including a Shell) and start it
		ProxyCli proxy = new ProxyCli(new Config("proxy"), shell);
		shell.register(proxy);
		Thread t = new Thread(shell);
		t.start();
		return proxy;
	}

	/**
	 * Creates and starts a new file server instance using the provided {@link Config} and {@link Shell}.
	 *
	 * @param config the configuration containing parameters such as connection info
	 * @param shell  the {@code Shell} used for processing commands
	 * @return the created component after starting it successfully
	 * @throws Exception if an exception occurs
	 */
	public IFileServerCli startFileServer(Config config, Shell shell) throws Exception {
		// TODO: create a new file server instance (including a Shell) and start it
		FileServerCli server = new FileServerCli(config, shell);
		shell.register(server);
		Thread t = new Thread(shell);
		t.start();
		return server;
	}
}
