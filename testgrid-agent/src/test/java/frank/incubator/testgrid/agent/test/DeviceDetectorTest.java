package frank.incubator.testgrid.agent.test;

import java.io.File;
import java.util.concurrent.TimeUnit;

import frank.incubator.testgrid.agent.device.AndroidDeviceDetector;
import frank.incubator.testgrid.agent.device.DeviceManager;
import frank.incubator.testgrid.common.Constants;

public class DeviceDetectorTest {

	/**
	 * @param args
	 */
	public static void main( String[] args ) throws Exception {
		DeviceManager dm = new DeviceManager( Constants.ONE_MINUTE * 5, null );
		AndroidDeviceDetector p = new AndroidDeviceDetector( new File("workspace"), dm, Constants.ONE_MINUTE );
		p.start();
		TimeUnit.SECONDS.sleep( 15 );
		p.waitingSchedule = 1500l;
		TimeUnit.SECONDS.sleep( 15 );
		p.running = false;
	}
}
