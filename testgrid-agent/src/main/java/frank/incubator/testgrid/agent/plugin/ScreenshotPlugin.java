package frank.incubator.testgrid.agent.plugin;

import static frank.incubator.testgrid.common.CommonUtils.isWindows;

import java.io.File;
import java.util.Iterator;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;

/**
 * Plugin which used to catch screenshot for all the busy devices.
 * 
 * @author Wang Frank
 * @param <File>
 * 
 */
public class ScreenshotPlugin extends AbstractAgentPlugin<File> {

	public ScreenshotPlugin( ) {
	}
	
	public enum FolderStrategy {
		DEVICE_BASED, TIME_BASED;
		
		public static FolderStrategy parse( String str ) {
			FolderStrategy fs = FolderStrategy.valueOf( str );
			if( fs == null )
				fs = DEVICE_BASED;
			return fs;
		}
	}
	
	private String currentSN;
	private String pattern;
	final private String DEFAULT_PATTERN = "yyyyMMdd-HHmmss";
	private boolean cleanupExpireScreenshots = true;
	private int expireDays = 3;
	
	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public void run() {
		if( this.getAttributes().containsKey( "namingPattern" ) ) {
			pattern = (String)getAttributes().get( "namingPattern" );
		}
		if( pattern == null || pattern.trim().isEmpty() )
			pattern = DEFAULT_PATTERN;
		String current = CommonUtils.getCurrentTime( pattern );
		Iterator<Device> it = dm.listDevices().iterator();
		String sn = null;
		String filename = null;
		File f = null;
		Device d = null;
		FolderStrategy strategy = FolderStrategy.DEVICE_BASED;
		Object o = this.getAttributes().get( "folderStrategy" );
		if( o != null )
			strategy = FolderStrategy.parse( o.toString() );
		if( this.getAttributes().containsKey( "cleanupExpireScreenshots" ) ) {
			this.cleanupExpireScreenshots = CommonUtils.parseBoolean( getAttributes().get( "cleanupExpireScreenshots" ).toString(), true );
		}
		if( this.getAttributes().containsKey( "expireDays" ) ) {
			Object v = getAttributes().get( "expireDays" );
			if( v instanceof Float )
				this.expireDays = ( ( Float ) v ).intValue();
			else if( v instanceof Double )
				this.expireDays = ( (Double) v ).intValue();
			else if( v instanceof Integer )
				this.expireDays = (Integer) v;
			else if( v instanceof String )
				this.expireDays = CommonUtils.parseInt( v.toString(), 3 );
		}
		
		File folder = null;
		switch( strategy ) {
			case DEVICE_BASED:
				while( it.hasNext() ) {
					d = it.next();
					sn = d.getAttribte( Constants.DEVICE_SN );
					currentSN = sn;
					if( d.getState() != Device.DEVICE_LOST && d.getState() != Device.DEVICE_LOST_TEMP ) {
						folder = new File( workspace, "screenshots/" + sn );
						if( !folder.exists() )
							folder.mkdirs();
						filename = "screen_" + sn + "_" + current + ".png";
						f = new File( folder, filename );
						StringBuilder sb = new StringBuilder();
						if( CommonUtils.isWindows() ) {
							sb.append( adb()  ).append( " -s " ).append( sn ).append( " shell screencap -p /sdcard/" )
								.append( filename ).append( " && " ).append( adb() ).append( " -s " ).append( sn )
								.append( " pull /sdcard/" ).append( filename ).append( " && " ).append( adb() )
								.append( " -s " ).append( sn ).append( " shell rm /sdcard/" ).append( filename );
						} else {
							sb.append( adb() ).append( " -s " ).append( sn ).append( " shell screencap -p | sed 's/\r$//' > " ).append( filename );
						}
						try {
							CommonUtils.exec( sb.toString(), folder.getAbsolutePath() );
						} catch ( Exception e ) {
							throw new RuntimeException( "exec screen capture failed.", e );
						}
						if( !f.exists() )
							log.error( "screenshot for device:" + sn +" @" + current + " failed." );
					}
				}
				break;
			case TIME_BASED:
				folder = new File( workspace, "screenshots/screenshot" + "_" + current );
				if( !folder.exists() )
					folder.mkdirs();
				
				while( it.hasNext() ) {
					d = it.next();
					if( d.getState() != Device.DEVICE_LOST && d.getState() != Device.DEVICE_LOST_TEMP ) {
						sn = d.getAttribte( Constants.DEVICE_SN );
						currentSN = sn;
						filename = "screen_" + sn + "_" + current + ".png";
						f = new File( folder, filename );
						StringBuilder sb = new StringBuilder();
						if( CommonUtils.isWindows() ) {
							sb.append( adb()  ).append( " -s " ).append( sn ).append( " shell screencap -p /sdcard/" )
								.append( filename ).append( " && " ).append( adb() ).append( " -s " ).append( sn )
								.append( " pull /sdcard/" ).append( filename ).append( " && " ).append( adb() )
								.append( " -s " ).append( sn ).append( " shell rm /sdcard/" ).append( filename );
						} else {
							sb.append( adb() ).append( " -s " ).append( sn ).append( " shell screencap -p | sed 's/\r$//' > " ).append( filename );
						}
						try {
							CommonUtils.exec( sb.toString(), folder.getAbsolutePath() );
						} catch ( Exception e ) {
							throw new RuntimeException( "exec screen capture failed.", e );
						}
						if( !f.exists() )
							log.error( "screenshot for device:" + sn +" @" + current + " failed." );
					}
				}
				break;
		}
		if( cleanupExpireScreenshots ) {
			cleanupExpired( System.currentTimeMillis(), new File( workspace, "screenshots") );
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.google.common.util.concurrent.FutureCallback#onSuccess(java.lang.Object)
	 */
	@Override
	public void onSuccess( File result ) {
		if( result != null && result.exists() && result.isDirectory() )
			log.info( "Screenshot have been successfully got. Which located @ " + result.getAbsolutePath() );
		else
			onFailure( new Exception( "Didn't find screenshot folder: " + result ) );
	}
	
	@Override 
	public void onFailure( Throwable t ) {
		log.error( "Screenshot met exception. currentSN=" + currentSN, t );
	}
	
	private String adb() {
		if ( isWindows() )
			return "%ADB_HOME%\\adb";
		else
			return "$ADB_HOME/adb";
	}
	
	/**
	 * Cleanup Expired screenshots.
	 * 
	 * @param current
	 * @param folder
	 */
	private void cleanupExpired( long current, File folder ) {
		if( folder != null && folder.exists() && folder.isDirectory() ) {
			for( File f : folder.listFiles() ) {
				
				if( f.isFile() ) {
					if( ( current - f.lastModified() ) > ( (long)this.expireDays * Constants.ONE_DAY ) )
						f.delete();
				}else if( f.isDirectory() ) {
					cleanupExpired( current, f );
					if( f.listFiles() == null || f.listFiles().length == 0 ) {
						f.delete();
					}
				}
			}
		}
	}
}
