package frank.incubator.testgrid.common.file;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * @author Wang Frank
 * 
 */
public class LocalFileTransferTarget extends Thread implements FileTransferTarget {

	private FileTransferMode mode;
	private LogConnector log;
	private File remoteBase;
	private File localBase;
	private String token;
	private File localDestDir;

	public LocalFileTransferTarget( FileTransferMode mode, File localBase, File remoteBase, OutputStream out ) {
		this.setName( "LocalFileTransferTarget" );
		this.mode = mode;
		this.localBase = localBase;
		this.remoteBase = remoteBase;
		log = LogUtils.get( "FileTransferTarget", out );
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferResource#dispose()
	 */
	@Override
	public void dispose() {
		try {
			File t = null;
			if ( mode == FileTransferMode.TARGET_HOST ) {
				t = new File( localBase, token );
				if( t.exists() && !t.equals( localDestDir ) ) {
					t.delete();
				}
			} else {
				t = new File( remoteBase, token );
				if( t.exists() && !t.equals( localDestDir ) ) {
					t.delete();
				}
			}
		}catch( Exception ex ) {
			ex.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferResource#getTransferMode
	 * ()
	 */
	@Override
	public FileTransferMode getTransferMode() {
		return mode;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferTarget#fetch(java.
	 * lang.String, java.util.Map, java.io.File)
	 */
	@Override
	public Collection<File> fetch( String token, Map<String, Long> fileList, File localDestDir ) throws Exception {
		this.token = token; 
		this.localDestDir = localDestDir;
		if ( mode == FileTransferMode.TARGET_HOST ) {
			return accept( token, fileList, localDestDir );
		} else {
			log.info( "Start transfer to target with mode:" + mode + ", token=" + token );
			Collection<File> files = new ArrayList<File>();
			File remoteFolder = new File( remoteBase, token );
			File t = null;
			File r = null;
			long fl = 0;
			for ( String fn : fileList.keySet() ) {
				fl = fileList.get( fn );
				t = new File( remoteFolder, fn );
				r = new File( localDestDir, fn );
				if ( !remoteFolder.equals( localDestDir ) ) {
					FileUtils.copyFileToDirectory( t, localDestDir );
				}
				if ( r.exists() && r.isFile() && r.length() == fl ) {
					files.add( r );
				} else {
					throw new Exception( "File[" + fn + "] transfer failed." );
				}
				log.info( "fetched " + fn );
			}
			log.info( "end of transfer to target with mode:" + mode + ", token=" + token );
			return files;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferTarget#accept(java
	 * .lang.String, java.util.Map, java.io.File)
	 */
	@Override
	public Collection<File> accept( String token, Map<String, Long> fileList, File localDestDir ) throws Exception {
		this.token = token;
		this.localDestDir = localDestDir;
		if ( mode != FileTransferMode.TARGET_HOST ) {
			return fetch( token, fileList, localDestDir );
		} else {
			log.info( "Start transfer to target with mode:" + mode + ", token=" + token );
			File localFolder = new File( localBase, token );
			Collection<File> files = new ArrayList<File>();
			long fl = 0;
			File f = null;
			if ( !localFolder.equals( localDestDir ) ) {
				for ( String fn : fileList.keySet() ) {
					f = new File( localFolder, fn );
					FileUtils.copyFileToDirectory( f, localDestDir );
				}
			}

			for ( String fn : fileList.keySet() ) {
				fl = fileList.get( fn );
				f = new File( localFolder, fn );
				if ( f.exists() && f.isFile() && f.length() == fl ) {
					files.add( f );
				} else {
					throw new Exception( "File[" + fn + "] transfer failed." );
				}
				log.info( "accept " + fn );
			}
			log.info( "end of transfer to target with mode:" + mode + ", token=" + token );
			return files;
		}
	}

}
