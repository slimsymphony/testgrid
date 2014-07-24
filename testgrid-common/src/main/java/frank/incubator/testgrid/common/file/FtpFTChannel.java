package frank.incubator.testgrid.common.file;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import frank.incubator.testgrid.common.log.LogConnector;

/**
 * FTP file transfer Channel.
 * 
 * @author Wang Frank
 * 
 */
public class FtpFTChannel extends FileTransferChannel {

	public FtpFTChannel() {
		this.id = "FTP";
		this.setPriority( 3 );
	}

	public FtpFTChannel( String host, String user, String pwd, int port ) {
		this.id = "FTP";
		this.properties.put( "user", user );
		this.properties.put( "host", host );
		this.properties.put( "pwd", pwd );
		if ( port < 21 )
			this.properties.put( "port", new Integer(21) );
		else
			this.properties.put( "port", port );
		this.setPriority( 3 );
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferChannel#validate()
	 */
	@Override
	public boolean validate() {
		FTPClient ftp = new FTPClient();
		boolean result = true;
		try {
			if ( !ftp.isConnected() ) {
				ftp.connect( getProperty( "host", String.class ), getProperty( "port", 21 ) );
				ftp.login( getProperty( "user", String.class ), getProperty( "pwd", String.class ) );
				int reply = ftp.getReplyCode();
				if ( !FTPReply.isPositiveCompletion( reply ) )
					result = false;
			}
		} catch ( Exception e ) {
			result = false;
			e.printStackTrace();
		} finally {
			try {
				ftp.disconnect();
			} catch ( Exception e ) {
				e.printStackTrace();
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#apply()
	 */
	@Override
	public boolean apply() {
		return validate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferChannel#send(java.
	 * lang.String, java.io.File[])
	 */
	@Override
	public boolean send( String token, Collection<File> fileList, LogConnector log ) {
		log.info( "FtpFTChannel begin sending files. token=" + token );
		FTPFileTransferSource source = new FTPFileTransferSource( getProperty( "host", String.class ), getProperty( "port", 21 ), getProperty( "user",
				String.class ), getProperty( "pwd", String.class ), log.getOs() );
		try {
			source.push( token, fileList );
		} catch ( Exception e ) {
			log.error( "Sending Files via FTP failed. token=" + token, e );
			return false;
		}
		log.info( "FtpFTChannel finished sending files. token=" + token );
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferChannel#receive(java
	 * .lang.String, java.util.Map, java.io.File)
	 */
	@Override
	public boolean receive( String token, Map<String, Long> fileList, File localDestDir, LogConnector log ) {
		log.info( "FtpFTChannel begin receiving files. token=" + token );
		FTPFileTransferTarget target = new FTPFileTransferTarget( getProperty( "host", String.class ), getProperty( "port", 21 ), getProperty( "user",
				String.class ), getProperty( "pwd", String.class ), log.getOs() );
		try {
			target.fetch( token, fileList, localDestDir );
		} catch ( Exception e ) {
			log.error( "Fetching Files via FTP failed. token=" + token, e );
			return false;
		}
		log.info( "FtpFTChannel finished receiving files. token=" + token );
		target.dispose();
		return true;
	}

}
