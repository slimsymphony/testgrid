package frank.incubator.testgrid.common.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Collection;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

public final class FTPFileTransferSource implements FileTransferSource {

	public FTPFileTransferSource( String host, int port, String userName, String passwd, OutputStream out ) {
		this.host = host;
		this.port = port;
		this.userName = userName;
		this.password = passwd;
		this.log = LogUtils.get( "FileTransferSource", out );
		ftp = new FTPClient();
		ftp.setBufferSize( 8192*1024 );
		try {
			ftp.setSendBufferSize( 8192 * 1024 );
		} catch ( SocketException e ) {
			log.error( "Send send buffer to 1024*1024 failed.", e );
		}
	}

	private String host;
	private int port;
	private String userName;
	private String password;
	private FTPClient ftp;
	private String token;
	private LogConnector log;

	@Override
	public void publish( String token, Collection<File> fileList ) throws Exception {
		push( token, fileList );

	}

	@Override
	public void push( String token, Collection<File> fileList ) throws Exception {
		this.token = token;
		try {
			if ( connectToServer( host, port, userName, password ) ) {
				log.info( "Current working directory is:" + ftp.printWorkingDirectory() );
				boolean ret = ftp.makeDirectory( token );
				log.info( "Make dir "+ token+" "+ ret );
				ftp.setFileType( FTP.BINARY_FILE_TYPE );
				ftp.changeWorkingDirectory( token );
				log.info( "Current working directory is:" + ftp.printWorkingDirectory() );
				for ( File file : fileList ) {
					FileInputStream input = null;
					try {
						input = new FileInputStream( file );
						boolean result = ftp.storeFile( file.getName(), input );
						if ( result && validate( file ) ) {
							log.info( "Succeed to upload file " + file.getName() );
						} else {
							log.error( "Failed to upload file " + file.getName() );
							throw new Exception( "Fail to upload file to FTP server. token=" + token + ", failure file="
									+ file.getName() );
						}
					} catch ( Exception e ) {
						log.error( "Fail to push file to FTP server. token=" + token, e );
						throw new Exception( "Fail to push file to FTP server. token=" + token, e );
					} finally {
						CommonUtils.closeQuietly( input );
					}
	
				}
			}
		}finally {
			ftp.disconnect();
		}
	}

	@Override
	public void dispose() {
		if ( connectToServer( host, port, userName, password ) ) {
			try {
				deleteFiles( token );
			} catch ( Exception e ) {
				log.error( "Dispose failed.", e );
			} finally {
				try {
					ftp.disconnect();
				} catch ( Exception e ) {
					log.error( "Close ftp connection got exception.", e );
				}
			}
		}
	}

	public boolean deleteFiles( String directoyNamePath ) throws Exception {
		boolean result = true;
		ftp.changeToParentDirectory();
		String targetPath = ftp.printWorkingDirectory() + "/" + directoyNamePath;
		targetPath = targetPath.replaceAll( "//", "/" );
		FTPFile[] fileList = ftp.listFiles( targetPath );
		for ( FTPFile file : fileList ) {
			if ( file.isDirectory() ) {
				log.info( "Delete sub directory " + file.getName() );
				String subDirectoryNamePath = directoyNamePath + "/" + file.getName();
				deleteFiles( subDirectoryNamePath );
			}
			if ( file.isFile() ) {
				log.info( "Delete file " + file.getName() );
				try {
					ftp.deleteFile( targetPath + "/" + file.getName() );
				} catch ( IOException e ) {
					log.error( "Delete File failed from:" + directoyNamePath, e );
				}
			}
		}
		log.info( "Delete empty directory after " + directoyNamePath );
		ftp.removeDirectory( targetPath );
		return result;
	}

	public boolean connectToServer( String host, int port, String userName, String password ) {
		boolean result = true;
		try {
			if ( !ftp.isConnected() ) {
				ftp.connect( host, port );
				log.info( "Connected to " + host + ":" + port );
				ftp.login( userName, password );
				int reply = ftp.getReplyCode();
				log.debug( "Got reply:" + reply );
				if ( !FTPReply.isPositiveCompletion( reply ) ) {
					ftp.disconnect();
					log.error( "FTP server refused connection." );
					result = false;
				}
			}
		} catch ( SocketException e ) {
			result = false;
			log.error( "Connect to FTP server timeout", e );
		} catch ( IOException e ) {
			result = false;
			log.error( "Connect to FTP server failed ", e );
		}

		return result;
	}
	
	private boolean validate( File file ) throws IOException {
		boolean validate = false;
		long size = file.length();
		String fn = file.getName();
		FTPFile[] fs = ftp.listFiles();
		for( FTPFile f : fs ) {
			if( f.getName().equals( fn ) && f.getSize() == size ) {
				validate = true;
				break;
			}
		}
		return validate;
	}

	@Override
	public FileTransferMode getTransferMode() {
		return FileTransferMode.THIRDPARTY_HOST;
	}

}
