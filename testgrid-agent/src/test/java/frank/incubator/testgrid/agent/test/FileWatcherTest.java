package frank.incubator.testgrid.agent.test;

import java.io.File;
import java.io.IOException;

import frank.incubator.testgrid.agent.device.AndroidDeviceDetector;
import frank.incubator.testgrid.agent.device.DeviceConfigFileWatcher;
import frank.incubator.testgrid.agent.device.DeviceDetector;
import frank.incubator.testgrid.agent.device.DeviceManager;
import frank.incubator.testgrid.common.Constants;

public class FileWatcherTest {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main( String[] args ) throws IOException {
		String p = "C:/develop/eclipse-workspaces/java8_workspace/testgrid/testgrid-agent/workspace";
		File path = new File( p );
		DeviceManager dm = new DeviceManager(null);
		DeviceDetector dd = new AndroidDeviceDetector(path, dm, Constants.ONE_MINUTE);
		DeviceConfigFileWatcher dcfw = new DeviceConfigFileWatcher(path, dd,"properties");
		dcfw.start();
	}

}
