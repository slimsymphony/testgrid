package frank.incubator.testgrid.common.file;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.io.FileUtils;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * @author Wang Frank
 *
 */
public final class LocalFileTransferSource extends Thread implements FileTransferSource {
	
	private FileTransferMode mode;
	private LogConnector log;
	private File remoteBase;
	private File localBase;
	
	public LocalFileTransferSource( FileTransferMode mode, File localBase, File remoteBase, OutputStream out ) {
		this.setName( "LocalFileTransferSource" );
		this.mode = mode;
		this.remoteBase = remoteBase;
		this.localBase = localBase;
		log = LogUtils.get( "FileTransferSource", out );
	}
	/* (non-Javadoc)
	 * @see frank.incubator.testgrid.common.file.FileTransferResource#dispose()
	 */
	@Override
	public void dispose() {
	}

	/* (non-Javadoc)
	 * @see frank.incubator.testgrid.common.file.FileTransferResource#getTransferMode()
	 */
	@Override
	public FileTransferMode getTransferMode() {
		return mode;
	}

	/* (non-Javadoc)
	 * @see frank.incubator.testgrid.common.file.FileTransferSource#publish(java.lang.String, java.util.Collection)
	 */
	@Override
	public void publish( String token, Collection<File> fileList ) throws Exception {
		if( mode != FileTransferMode.SOURCE_HOST ) {
			push( token, fileList );
		}else {
			log.info( "Start transfer to publish with mode:" + mode+", token="+token );
			File folder = new File( localBase, token );
			if( !folder.exists() ) {
				folder.mkdirs();
			}
			for( File f : fileList ) {
				if( !f.getParentFile().equals( folder ) ) {
					FileUtils.copyFileToDirectory( f, folder );
				}
				log.info( "publish " + f.getName() );
			}
			log.info( "end of transfer to publish with mode:" + mode+", token="+token );
		}
	}

	/* (non-Javadoc)
	 * @see frank.incubator.testgrid.common.file.FileTransferSource#push(java.lang.String, java.util.Collection)
	 */
	@Override
	public void push( String token, Collection<File> fileList ) throws Exception {
		if( mode == FileTransferMode.SOURCE_HOST ) {
			publish( token, fileList );
		}else {
			log.info( "Start transfer to push with mode:" + mode+", token="+token );
			File remoteFolder = new File( remoteBase, token );
			File r = null;
			Collection<File> files = new ArrayList<File>();
			long fl = 0;
			for ( File fn : fileList ) {
				if( !remoteFolder.equals( fn.getParentFile() ) ) {
					fl = fn.length();
					FileUtils.copyFileToDirectory( fn, remoteFolder );
					r = new File( remoteFolder, fn.getName() );
					if ( r.exists() && r.isFile() && r.length() == fl ) {
						files.add( r );
					} else {
						throw new Exception( "File[" + fn + "] transfer failed." );
					}
				}
				log.info( "pushed " + fn.getName() );
			}
			log.info( "end of transfer to push with mode:" + mode+", token="+token );
		}
	}

}
