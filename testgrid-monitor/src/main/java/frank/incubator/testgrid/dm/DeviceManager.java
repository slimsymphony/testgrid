package frank.incubator.testgrid.dm;

import java.util.List;
import java.util.Map;

import frank.incubator.testgrid.common.model.Device;

/**
 * Device Manager Interface. 
 * 
 * @author Wang Frank
 *
 */
public interface DeviceManager {
	void addDevice( Device device ) throws DeviceManageException;
	void removeDevice( Device device ) throws DeviceManageException;
	void updateDevice( Device device ) throws DeviceManageException;
	List<Device> queryDevices( Map<String,? extends Object> conditions );
}
