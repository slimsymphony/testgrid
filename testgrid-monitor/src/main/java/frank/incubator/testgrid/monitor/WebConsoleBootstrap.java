package frank.incubator.testgrid.monitor;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * <p>
 * Bootstrap for Test Cloud Service component. Here we should do things below:
 * <p>
 * 1. Register with MQ serivce.
 * </p>
 * <p>
 * 2. Publish Queue for register testnodes/clients.
 * </p>
 * <p>
 * 3. Initial Database service.
 * </p>
 * <p>
 * 4. Inital embed web container for monitor and check services.
 * </p>
 * <p>
 * 5. Start different handlers listening requests. which including:
 * <ol>
 * <li>register handler</li>
 * <li>test handler</li>
 * <li>status handler</li>
 * <ol>
 * </p>
 * 
 */
public class WebConsoleBootstrap extends Thread {

	private LogConnector log = LogUtils.get("Bootstrap");

	public WebConsoleBootstrap(String[] args) {
		this.setName("WebServer");
		this.initial(args);
	}

	public static final String PARAM_URI = "uri";

	public static final String PARAM_USER = "user";

	public static final String PARAM_PASSWD = "passwd";

	public static final String PARAM_PORT = "port";

	@SuppressWarnings("serial")
	final Set<String> params = new HashSet<String>() {
		{
			this.add(PARAM_URI);
			this.add(PARAM_USER);
			this.add(PARAM_PASSWD);
			this.add(PARAM_PORT);
		}
	};

	private String mqUri = "vm://localhost";

	private String mqUser = "";

	private String mqPasswd = "";

	private int serivePort = 5451;

	public String getMqUri() {
		return mqUri;
	}

	public String getMqUser() {
		return mqUser;
	}

	public String getMqPasswd() {
		return mqPasswd;
	}

	public int getSerivePort() {
		return serivePort;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		WebConsoleBootstrap instance = new WebConsoleBootstrap(args);
		instance.setDaemon(true);
		instance.start();
	}

	private void initial(String[] args) {
		this.parseArgs(args);
		// Switch to use InitializationServlet for MQ binding.
		// this.conn2MQ();
		this.startJetty();

	}

	private void startJetty() {
		try {
			Server server = new Server(this.serivePort);
			WebAppContext webapp = new WebAppContext();
			webapp.setContextPath("/");
			webapp.setResourceBase("src/main/webapp/");
			webapp.setDescriptor("WEB-INF/web.xml");
			server.setHandler(webapp);
			server.start();
			server.join();
		} catch (Exception ex) {
			ex.printStackTrace();
			log.error("Start Jetty container failed.", ex);
		}
	}

	private void parseArgs(String[] args) {
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				if (args[i].contains("=")) {
					String[] cms = args[i].split("=");
					String k = cms[0].toLowerCase().trim();
					String v = cms[1].toLowerCase().trim();
					if (params.contains(k)) {
						if (k.equals(PARAM_URI)) {
							this.mqUri = v;
						} else if (k.equals(PARAM_USER)) {
							this.mqUser = v;
						} else if (k.equals(PARAM_PASSWD)) {
							this.mqPasswd = v;
						} else if (k.equals(PARAM_PORT)) {
							this.serivePort = Integer.parseInt(v);
						}
					}
				}
			}
		}
	}

}
