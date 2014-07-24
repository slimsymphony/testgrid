package frank.incubator.testgrid.common.file;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * Very simple implementation of FileTransferSource via directly socket
 * transferring.
 * 
 * @author Wang Frank
 * 
 */
public final class DirectSocketTransferSource extends Thread implements FileTransferSource {

	private String token;
	private Map<String, File> filelist;
	private ServerSocket server;
	private String host;
	private int port;
	private LogConnector log;
	private boolean running = true;
	private FileTransferMode mode;

	public DirectSocketTransferSource( String remoteHost, int port, OutputStream out ) {
		this.setName( "DirectSocketTransferSource" );
		this.host = remoteHost;
		this.port = port;
		if( remoteHost == null )
			this.mode = FileTransferMode.SOURCE_HOST;
		else
			this.mode = FileTransferMode.TARGET_HOST;
		log = LogUtils.get( "FileTransferSource", out );
	}

	public int getPort() {
		return port;
	}

	@Override
	public void run() {
		try {
			switch ( mode ) {
				case SOURCE_HOST:
					server = new ServerSocket( port );
					log.info("Start hosting on Port:" + port );
					while ( running ) {
						Socket socket = server.accept();
						log.info( "Incoming request from:" + socket.getRemoteSocketAddress() );
						handle( socket );
					}
					break;
				/*
				 * case ACCEPT: handleActive( host, port ); break;
				 */
				default:
					break;
			}
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}

	/**
	 * Check the token, then receive the request file name and send back the
	 * file content.
	 * 
	 * @param socket
	 * @throws IOException
	 */
	private void handle( Socket socket ) throws IOException {
		DataInputStream in = null;
		DataOutputStream os = null;
		try {
			in = new DataInputStream( socket.getInputStream() );
			String incomingToken = in.readUTF();
			log.info( "Receiving an incoming token:" + incomingToken );
			if ( incomingToken.equals( token ) ) { // valid request.
				os = new DataOutputStream( new BufferedOutputStream( socket.getOutputStream() ) );
				os.writeInt( Constants.VALIDATION_SUCC );
				os.flush();
				log.info( "validate succ" );
				while ( true ) {
					String request = in.readUTF();
					if ( !request.equals( Constants.TRANSFER_ENDED ) ) {
						log.info( "Got request to send file:" + request );
						if ( filelist.keySet().contains( request ) ) {
							File f = filelist.get( request );
							InputStream fin = null;
							try {
								fin = new FileInputStream( f );
								IOUtils.copy( fin, os );
								os.flush();
								log.info( "finish sending file:" + request );
							} catch ( Exception e ) {
								e.printStackTrace();
							} finally {
								CommonUtils.closeQuietly( fin );
							}
						}
					} else {
						break;
					}
				}
			} else {
				os = new DataOutputStream( socket.getOutputStream() );
				os.writeInt( Constants.VALIDATION_FAIL );
				os.flush();
				log.info( "validate failed" );
			}
			running = false;

		} catch ( IOException e ) {
			log.error( "handle incoming request met problem.", e );
		} finally {
			CommonUtils.closeQuietly( os );
			CommonUtils.closeQuietly( in );
			CommonUtils.closeQuietly( socket );
			dispose();
		}
	}

	@Override
	public void publish( String token, Collection<File> fileList ) throws Exception {
		if ( fileList == null || fileList.isEmpty() )
			throw new NullPointerException( "No file provided to be published." );
		this.token = token;

		filelist = new HashMap<String, File>();
		for ( File file : fileList ) {
			if ( !file.exists() )
				throw new FileNotFoundException( "File:" + file + " didn't exists" );
			else
				filelist.put( file.getName(), file );
		}
		this.start();
	}

	@SuppressWarnings( "resource" )
	@Override
	public void push( String token, Collection<File> fileList ) throws Exception {
		Socket socket = null;
		DataInputStream in = null;
		DataOutputStream os = null;
		InputStream fin = null;
		try {
			socket = new Socket();
			int countDown = 3;
			while ( countDown > 0 ) {
				try {
					socket.connect( new InetSocketAddress( host, port ) );
					break;
				} catch ( Exception ex ) {
					log.error( "Connect to Dest Server[" + host + ":" + port + "] failed.", ex );
					countDown--;
					TimeUnit.SECONDS.sleep( 5 - countDown );
					if ( countDown == 0 ) {
						throw new Exception("Can't connect to Destination Server for 3 times.", ex);
					}
				}

			}
			os = new DataOutputStream( new BufferedOutputStream( socket.getOutputStream() ) );
			in = new DataInputStream( new BufferedInputStream( socket.getInputStream() ) );
			os.writeUTF( token );
			os.flush();
			int response = in.readInt();
			if ( response == Constants.VALIDATION_SUCC ) {
				for ( File file : fileList ) {
					try {
						os.writeUTF( file.getName() );
						os.flush();
						response = in.readInt();
						if ( response == Constants.RECEIVE_READY ) {
							log.info( "Source: sending:" + file );
							fin = new BufferedInputStream( new FileInputStream( file ) );
							IOUtils.copy( fin, os );
							os.flush();
						}
						response = in.readInt();
						if ( response == Constants.RECEIVE_FINISHED ) {
							log.info( "Source: finished sent :" + file );
						}
					} finally {
						CommonUtils.closeQuietly( fin );
					}
				}
				os.writeUTF( Constants.TRANSFER_ENDED );
				os.flush();
			} else {
				throw new IOException( "Invalid token for sending files to " + host + ":" + port + ", token:" + token );
			}
		} finally {
			CommonUtils.closeQuietly( os );
			CommonUtils.closeQuietly( in );
			CommonUtils.closeQuietly( socket );
			dispose();
		}
	}
	
	@Override
	public void dispose() {
		CommonUtils.closeQuietly( server );
	}

	@Override
	public FileTransferMode getTransferMode() {
		return mode;
	}
}
