package frank.incubator.testgrid.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

public class HttpServer {

	private LogConnector log = LogUtils.get( "Http" );

	public HttpServer( Object appReference, int port ) {
		this.servicePort = port;
		appRef = appReference;
	}

	private int servicePort;
	private Server server;
	private ExecutorService pool = Executors.newFixedThreadPool( 1 );
	private static Object appRef;

	public static Object getAppRef() {
		return appRef;
	}

	public void stop() {
		if ( server != null ) {
			if ( !server.isStopped() && !server.isStopping() ) {
				try {
					server.stop();
					log.info( "Http service have been stopped." );
				} catch ( Exception e ) {
					log.error( "Stop Http service failed.", e );
				}
			}
		}
	}

	public void start() {
		pool.execute( new Runnable() {
			public void run() {
				try {
					if ( server != null && server.isStarted() ) {
						try {
							server.stop();
						} catch ( Exception ex ) {
							log.error( "Stop server before restart service got exception.", ex );
						}
					}
					server = new Server( servicePort );
					WebAppContext webapp = new WebAppContext();
					webapp.setContextPath( "/" );
					webapp.setResourceBase( "src/main/webapp/" );
					webapp.setDescriptor( "WEB-INF/web.xml" );
					server.setHandler( webapp );
					server.start();
					server.join();
					log.info( "Http service have been started on port:" + servicePort );
				} catch ( Exception ex ) {
					log.error( "Start Http service container failed.", ex );
				}
			}
		} );
	}
}
