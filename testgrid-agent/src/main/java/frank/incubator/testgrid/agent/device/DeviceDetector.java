package frank.incubator.testgrid.agent.device;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.Device;

public abstract class DeviceDetector extends Thread {

	protected LogConnector log = LogUtils.get( "DeviceDetector" );
	/**
	 * Handler of all discovered products.
	 */
	protected DeviceManager deviceManager;
	public boolean running = true;
	public long waitingSchedule = Constants.ONE_SECOND * 30;
	protected File workspace;

	public DeviceDetector( File workspace, DeviceManager deviceExplorer, long scanInterval ) {
		this.workspace = workspace;
		this.deviceManager = deviceExplorer;
		this.waitingSchedule = scanInterval;
		this.setName( "DeviceDetector" );
		DeviceConfigFileWatcher dcfw;
		try {
			dcfw = new DeviceConfigFileWatcher( workspace.toPath(), this, "properties" );
			dcfw.start();
		} catch ( IOException e ) {
			log.error( "Start Device Config file watcher failed.", e );
		}
	}

	@SuppressWarnings( "unchecked" )
	protected void loadUserDefined( Device device ) {
		BufferedReader reader = null;
		File file = new File( workspace, ( String ) device.getAttribte( Constants.DEVICE_SN ) + ".properties" );
		try {
			if ( !file.exists() ) {
				log.info( "configuration file didn't exist for device[" + device.toString() + "], created a empty one." );
				file.createNewFile();
			} else {
				reader = new BufferedReader( new FileReader( file ) );
				Properties p = new Properties();
				p.load( reader );
				String key = null;
				for( Entry<Object,Object> entry :  p.entrySet() ) {
					key = ((String)entry.getKey()).trim();
					if( key.trim().startsWith( "#" ) ) {
						continue;
					}
					if ( key.startsWith( Constants.DEVICE_EXCLUDE_PREFIX ) )
						device.addExclusiveAttribute( key, entry.getValue() );
					else
						device.addAttribute( key.trim(), entry.getValue() );
				}

				/*String line = null;
				while ( ( line = reader.readLine() ) != null ) {
					if ( line.trim().isEmpty() )
						continue;
					int idx = line.indexOf( '=' );
					String key = line.substring( 0, idx );
					String value = line.substring( idx + 1 );
					if ( key.startsWith( "#" ) )
						continue;
					if ( key.startsWith( Constants.DEVICE_EXCLUDE_PREFIX ) )
						device.addExclusiveAttribute( key, value );
					else
						device.addAttribute( key.trim(), value.trim() );
				}*/
			}
		} catch ( FileNotFoundException e ) {
			log.error( "Didn't find the device file:" + file.getAbsolutePath(), e );
		} catch ( Exception ex ) {
			log.error( "load and combine user-defined attribute for device[" + device + "] failed. persistence file:" + file.getAbsolutePath(), ex );
		} finally {
			CommonUtils.closeQuietly( reader );
		}
	}
	
	@Override
	public void run() {
		log.debug( "DeviceDetector Start!" );
		while ( running ) {
			try {
				refresh();
				TimeUnit.MILLISECONDS.sleep( waitingSchedule );
			} catch ( Exception e ) {
				log.error( "DeviceDetector met exception when refreshing devices status.", e );
			}
		}
		log.debug( "DeviceDetector Stop!" );
	}

	public abstract void refresh();
}