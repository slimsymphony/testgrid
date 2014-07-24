package frank.incubator.testgrid.monitor;

import java.util.HashSet;
import java.util.Set;

import javax.jms.Topic;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageHub;

import frank.incubator.testgrid.common.model.Agent;
import frank.incubator.testgrid.common.model.Client;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.TestSuite;

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
 * @author Wang Frank
 * 
 */
public class WebConsoleBootstrap extends Thread {

	private LogConnector log = LogUtils.get( "Bootstrap" );
	
	public WebConsoleBootstrap( String[] args ) {
		this.setName( "WebServer" );
		this.initial( args );
	}

	public static final String PARAM_URI = "uri";

	public static final String PARAM_USER = "user";

	public static final String PARAM_PASSWD = "passwd";

	public static final String PARAM_PORT = "port";

	@SuppressWarnings( "serial" )
	final Set<String> params = new HashSet<String>() {
		{
			this.add( PARAM_URI );
			this.add( PARAM_USER );
			this.add( PARAM_PASSWD );
			this.add( PARAM_PORT );
		}
	};

	private String mqUri = "vm://localhost";

	private String mqUser = "";

	private String mqPasswd = "";

	private int serivePort = 5451;
        
        private String icaseDir = "C:\\icase_svn\\icase";
        
        private String svn = "C:\\Program Files\\CollabNet\\Subversion client\\svn.exe";

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
	public static void main( String[] args ) {
		WebConsoleBootstrap instance = new WebConsoleBootstrap( args );
		instance.setDaemon( true );
		instance.start();
	}

	private void initial( String[] args ) {
		this.parseArgs( args );
                //Switch to use InitializationServlet for MQ binding.
		//this.conn2MQ();
		this.startJetty();

	}

	private void startJetty() {
		try {
		Server server = new Server( this.serivePort );
		WebAppContext webapp = new WebAppContext();
		webapp.setContextPath( "/" );
		webapp.setResourceBase("src/main/webapp/");
		webapp.setDescriptor("WEB-INF/web.xml");
		server.setHandler( webapp );
		server.start();
                
                /*
                Task task = new Task();
                task.setPhase(Task.Phase.FINISHED);
                task.setTaskOwner("larry");
                
                Client client = new Client();
                client.setId("localhost-1");
                client.setHost("localhost");
                client.setTaskId(task.getId());
                client.setStatus(Client.Status.INUSE);
                MonitorCache.updateClient(client);
                
                Test test = new Test();
                test.setTaskID(task.getId());
                System.out.println(test.getId());
                test.setPhase(Test.Phase.FINISHED);
                test.setUrl("http://tech.sina.com.cn/zl/");
                MonitorCache.updateTest(test);
                
                Test test2 = new Test();
                test2.setTaskID(task.getId());
                System.out.println(test2.getId());
                test2.setPhase(Test.Phase.FINISHED);
                test2.setUrl("test url 2");
                MonitorCache.updateTest(test2);
                
                TestSuite testsuite = new TestSuite();
                testsuite.addTest(test);
                testsuite.addTest(test2);
                task.setTestsuite(testsuite);
                MonitorCache.updateTask(task);
                
                Device device = new Device();
                device.getAttributes().put("sn", "sn-001");
                device.getAttributes().put("imei", "IMEI-00001");
                device.getAttributes().put("tag", "TAG-001");
                device.getAttributes().put("productcode", "PRODUCT-001");
                device.setTaskStatus(test.getId());
                System.out.println(device.toString());
                MonitorCache.updateDevice(device);
                Device device2 = new Device();
                device2.getAttributes().put("sn", "sn-002");
                device2.getAttributes().put("imei", "IMEI-00002");
                device2.getAttributes().put("tag", "TAG-002");
                device2.getAttributes().put("productcode", "PRODUCT-002");
                device2.setTaskStatus(test2.getId());
                System.out.println(device2.toString());
                MonitorCache.updateDevice(device2);
                
                Agent agent = new Agent();
                agent.setHost("localhost");
                agent.setStatus(Agent.AgentStatus.NORMAL);
                agent.getDevices().add(device);
                agent.getDevices().add(device2);
                MonitorCache.updateAgent(agent);
                */
                
		server.join();
		}catch(Exception ex) {
                    ex.printStackTrace();
			log.error( "Start Jetty container failed.", ex );
		}
	}

	private void parseArgs( String[] args ) {
		if ( args != null ) {
			for ( int i = 0; i < args.length; i++ ) {
				if ( args[i].contains( "=" ) ) {
					String[] cms = args[i].split( "=" );
					String k = cms[0].toLowerCase().trim();
					String v = cms[1].toLowerCase().trim();
					if ( params.contains( k ) ) {
						if ( k.equals( PARAM_URI ) ) {
							this.mqUri = v;
						} else if ( k.equals( PARAM_USER ) ) {
							this.mqUser = v;
						} else if ( k.equals( PARAM_PASSWD ) ) {
							this.mqPasswd = v;
						} else if ( k.equals( PARAM_PORT ) ) {
							this.serivePort = Integer.parseInt( v );
						}
					}
				}
			}
		}
	}

}
