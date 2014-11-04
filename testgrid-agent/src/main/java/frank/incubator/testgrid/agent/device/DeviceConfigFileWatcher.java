package frank.incubator.testgrid.agent.device;

import java.io.File;
import java.io.IOException;

import frank.incubator.testgrid.common.file.FileWatchService;
import frank.incubator.testgrid.common.file.WatchEvent;

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

	public DeviceConfigFileWatcher(File dir, DeviceDetector dd,
			String fileExtension) throws IOException {
		super(dir, false);
		this.fileExtension = fileExtension;
		deviceDetector = dd;
	}

	@Override
	public void handleEvent(WatchEvent event, File path) {
		if (fileExtension == null
				|| fileExtension.trim().isEmpty()
				|| fileExtension.equals("*")
				|| path.getName().toString().toLowerCase()
						.endsWith(fileExtension)) {
			switch (event) {
			case UPDATED:
			case DELETED:
				deviceDetector.refresh();
				break;
			case CREATED:
				log.info("File["+path.getAbsolutePath()+"] was created.");
			}
		}
	}
}
