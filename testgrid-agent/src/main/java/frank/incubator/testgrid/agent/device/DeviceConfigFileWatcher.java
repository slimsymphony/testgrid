package frank.incubator.testgrid.agent.device;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;

import frank.incubator.testgrid.common.file.FileWatchService;

/**
 * Device Configuration File watch service. Which trigger a device refresh when
 * config file been updated or deleted.
 * 
 * @author Wang Frank
 * 
 */
public class DeviceConfigFileWatcher extends FileWatchService {

	private DeviceDetector deviceDetector;
	private String fileExtension;

	public DeviceConfigFileWatcher( Path dir, DeviceDetector dd, String fileExtension ) throws IOException {
		super( dir, false );
		this.fileExtension = fileExtension;
		deviceDetector = dd;
	}

	@Override
	public void handleEvent( WatchEvent<Path> event, Path path ) {
		Kind<Path> kind = event.kind();
		if ( fileExtension == null || fileExtension.trim().isEmpty() || fileExtension.equals( "*" ) || path.getFileName().toString().toLowerCase().endsWith( fileExtension ) ) {
			if ( kind == StandardWatchEventKinds.ENTRY_MODIFY || kind == StandardWatchEventKinds.ENTRY_DELETE ) {
				deviceDetector.refresh();
			}
		}
	}

}
