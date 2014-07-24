package frank.incubator.testgrid.common.file;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Map;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;

/**
 * @author Wang Frank
 * 
 */
public class DirectSocketFTChannel extends FileTransferChannel {

	public DirectSocketFTChannel() {
		this.id = "SOCKET";
		this.setPriority( 0 );
		this.getProperties().put( "host", CommonUtils.getHostName() );
	}

	transient protected ServerSocket ss;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferChannel#validate()
	 */
	@Override
	public boolean validate() {
		Socket socket = null;
		try {
			int servicePort;
			int failCnt = 0;
			if( ! this.getProperties().containsKey( "host" ) ) {
				this.getProperties().put( "host", CommonUtils.getHostName() );
			}
			if ( !this.getProperties().containsKey( "servicePort" ) ) {
				while ( true ) {
					servicePort = CommonUtils.availablePort( 10000 );
					try {
						ss = new ServerSocket( servicePort );
						this.getProperties().put( "servicePort", servicePort );
						break;
					} catch ( IOException e ) {
						failCnt++;
						e.printStackTrace();
						if ( failCnt == 10 )
							return false;
					}
				}
			}
			socket = new Socket();
			socket.connect( new InetSocketAddress( this.getProperty( "host", "localhost" ), this.getProperty( "servicePort", Integer.class ) ) );
		} catch ( Exception ex ) {
			return false;
		} finally {
			CommonUtils.closeQuietly( socket );
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#apply()
	 */
	@Override
	public boolean apply() {
		Socket socket = null;
		try {
			int servicePort;
			int failCnt = 0;
			if( ! this.getProperties().containsKey( "host" ) ) {
				this.getProperties().put( "host", CommonUtils.getHostName() );
			}
			if ( !this.getProperties().containsKey( "servicePort" ) ) {
				while ( true ) {
					servicePort = CommonUtils.availablePort( 10000 );
					try {
						ss = new ServerSocket( servicePort );
						this.getProperties().put( "servicePort", servicePort );
						break;
					} catch ( IOException e ) {
						failCnt++;
						e.printStackTrace();
						if ( failCnt == 10 )
							return false;
					}
				}
			}
			socket = new Socket();
			socket.connect( new InetSocketAddress( this.getProperty( "host", "localhost" ), this.getProperty( "servicePort", Integer.class ) ) );
		} catch ( Exception ex ) {
			return false;
		} finally {
			CommonUtils.closeQuietly( socket );
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferChannel#send(java.
	 * lang.String, java.util.Collection,
	 * frank.incubator.testgrid.common.log.LogConnector)
	 */
	@Override
	public boolean send( String token, Collection<File> fileList, LogConnector log ) {
		CommonUtils.closeQuietly( ss );
		DirectSocketTransferSource dts = new DirectSocketTransferSource( null, this.getProperty( "servicePort", Integer.class ), log.getOs() );
		try {
			dts.publish( token, fileList );
		} catch ( Exception e ) {
			log.error( "Send Files via DirectSocket failed. Token=" + token, e );
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferChannel#receive(java
	 * .lang.String, java.util.Map, java.io.File,
	 * frank.incubator.testgrid.common.log.LogConnector)
	 */
	@Override
	public boolean receive( String token, Map<String, Long> fileList, File localDestDir, LogConnector log ) {
		CommonUtils.closeQuietly( ss );
		DirectSocketTransferTarget dtt = new DirectSocketTransferTarget( this.getProperty( "host", String.class ), this.getProperty( "servicePort", Integer.class ), log.getOs() );
		try {
			dtt.fetch( token, fileList, localDestDir );
		} catch ( Exception e ) {
			log.error( "Receiving files via DirectSocket failed.Token=" + token, e );
			return false;
		}
		return true;
	}

}
