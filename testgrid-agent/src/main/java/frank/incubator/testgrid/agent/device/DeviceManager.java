package frank.incubator.testgrid.agent.device;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.concurrent.ConcurrentHashMap;

import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.DeviceRequirement;
import frank.incubator.testgrid.common.model.Test;

/**
 * Manager of devices which provide functional interface for public to
 * get/update device information.
 * 
 * @author Wang Frank
 * 
 */
public class DeviceManager {

	private LogConnector log;
	private Map<Device, Long> devices;
	private long permDisconnTimeout;
	private Observer observer;

	public final static long DEFAULT_PERM_DISCONNECT_TIMEOUT = Constants.ONE_MINUTE * 5;

	public DeviceManager(Observer observer) {
		this(DEFAULT_PERM_DISCONNECT_TIMEOUT, observer);
	}

	public DeviceManager(long permDisconnTimeout, Observer ob) {
		log = LogUtils.get("DeviceManager");
		this.observer = ob;
		devices = new ConcurrentHashMap<Device, Long>();
		this.permDisconnTimeout = permDisconnTimeout;
	}

	public long getPermDisconnTimeout() {
		return permDisconnTimeout;
	}

	public void setPermDisconnTimeout(int permDisconnTimeout) {
		this.permDisconnTimeout = permDisconnTimeout;
	}

	public Collection<Device> listDevices() {
		return devices.keySet();
	}
	
	public Map<Device,Long> allDevices() {
		return new HashMap<Device,Long>(devices);
	}
	
	/**
	 * Reserve required device of give task, and change the device state from
	 * free to reserved.
	 * 
	 * @param task
	 * @return
	 */
	public Collection<Device> reserveDevices(Test test, DeviceRequirement requirements) {
		List<Device> devices = new ArrayList<Device>(this.devices.keySet());
		Collections.shuffle(devices);
		Collection<Device> candidates = new ArrayList<Device>();
		Device require = requirements.getMain();
		for (Device dut : devices) {
			if (require.match(dut) && !candidates.contains(dut) && dut.getState() == Device.DEVICE_FREE) {
				if(!dut.isConnected()) {
					Device d = getDeviceBy(Constants.DEVICE_ID, dut.getId());
					if(d != null) {
						d.setPreState(Device.DEVICE_FREE);
						d.setState(Device.DEVICE_LOST_TEMP);
						continue;
					}
				}
				dut.setRole(Device.ROLE_MAIN);
				dut.setState(Device.DEVICE_RESERVED);
				dut.setTaskStatus(test.getTaskID()+Constants.TASK_TEST_SPLITER+test.getId());
				candidates.add(dut);
				log.info("Reserve Main device:" + dut.getId() +" for test:" + test.getId()+", taskId:"+ test.getTaskID());
				break;
			}
		}

		if (candidates.isEmpty())
			return null;

		Device requireRef = requirements.getRef();
		if (requireRef != null) {
			for (Device dut : devices) {
				if (requireRef.match(dut) && !candidates.contains(dut) && dut.getState() == Device.DEVICE_FREE) {
					if(!dut.isConnected()) {
						Device d = getDeviceBy(Constants.DEVICE_ID, dut.getId());
						d.setState(Device.DEVICE_LOST_TEMP);
						continue;
					}
					dut.setRole(Device.ROLE_REF);
					dut.setState(Device.DEVICE_RESERVED);
					dut.setTaskStatus(test.getId());
					candidates.add(dut);
					log.info("Reserve Reference device:" + dut.getId() +" for test:" + test.getId()+", taskId:"+ test.getTaskID());
					break;
				}
			}
			if (candidates.size() < 2)
				return null;
		}
		return candidates;
	}
	
	/**
	 * Relase assigned devices from other state to Free.
	 * @param dss
	 */
	public void releaseDevices(Device ... dss) {
		if(dss != null && dss.length>0) {
			for(Device d : dss) {
				Device device = getDeviceBy(Constants.DEVICE_SN, d.getAttribute(Constants.DEVICE_SN));
				if(device != null) {
					device.setRole(Device.ROLE_MAIN);
					log.info("Try to relase device:" + device.getId()+" from: " + device.getTaskStatus());
					device.setTaskStatus("");
					device.setPreState(Device.DEVICE_FREE);
					if(!device.isConnected()) {
						device.setState(Device.DEVICE_LOST_TEMP);
						continue;
					}else {
						device.setState(Device.DEVICE_FREE);
					}
					log.info("Releasing device:" + device.getId());
				}
			}
		}
	}
	
	public void releaseDevices(Collection<Device> dss) {
		if(dss != null && dss.size()>0) {
			releaseDevices(dss.toArray(new Device[0]));
		}
	}

	private void merge(Device device, Device duu) {
		device.setId(duu.getId());
		device.setRole(duu.getRole());
		device.setTaskStatus(duu.getTaskStatus());
		device.setState(duu.getState());
		device.setPreState(duu.getPreState());
		// device.setExclusiveAtts( duu.getExclusiveAtts() );
	}

	public void removeDevice(Device device) {
		devices.remove(device);
	}

	public synchronized void setDeviceState(String id, int state) {
		if(id != null) {
			for(Device d : devices.keySet()) {
				if(d.getId().equals(id)) {
					d.setState(state);
					break;
				}
			}
		}
	}
	
	@SuppressWarnings("serial")
	public Device getDeviceBy(final String key, final Object val) {
		Collection<Device> results = queryDevices(new HashMap<String, Object>() {
			{
				put(key, val);
			}
		});
		if (results == null || results.isEmpty()) {
			return null;
		}

		return results.iterator().next();
	}

	public Collection<Device> queryDevices(Map<String, Object> condition) {
		Device cond = Device.createRequirement(condition);
		Collection<Device> results = new ArrayList<Device>();
		for (Device d : this.devices.keySet()) {
			if (cond.simpleMatch(d)) {
				results.add(d);
			}
		}
		return results;
	}

	/**
	 * Sync the devices detected by detector. And DeviceManager should check
	 * these devices with current status. Then make changes.
	 * 
	 * @param devices
	 */
	public void sync(Collection<Device> devicesStatus, String detectorPlatform) {
		log.debug("Start Sync devices...");
		long current = System.currentTimeMillis();
		for (Device d : devicesStatus) {
			d.addObserver(observer);
			boolean isNewAdded = true;
			Iterator<Device> it = devices.keySet().iterator();
			while (it.hasNext()) {
				Device dv = it.next();
				if (dv.equals(d)) {
					isNewAdded = false;
					merge(d, dv);
					it.remove();
					dv.deleteObservers();
					break;
				}
			}
			devices.put(d, current);
			if (isNewAdded) {
				d.notifyObservers(Device.DEVICE_NEW);
			} else {
				// d.notifyObservers( Device.DEVICE_UPDATE );
			}
		}

		int tempDisconn = 0;
		int permDisconn = 0;
		Iterator<Device> dit = devices.keySet().iterator();
		while(dit.hasNext()) {
			Device d = dit.next();
			if(!d.getAttribute(Constants.DEVICE_PLATFORM).equals(detectorPlatform))
				continue;
			long updateTime = devices.get(d);
			if (updateTime != current) {
				if (this.permDisconnTimeout > (current - updateTime)) {
					// temporary disconnect
					if (d.getState() != Device.DEVICE_LOST_TEMP) {
						d.setPreState(d.getState());
						d.setState(Device.DEVICE_LOST_TEMP);
						log.info("Device[" + d.getId() + "] state change from " + d.getPreState() + " to "
								+ Device.DEVICE_LOST_TEMP);
					}
					tempDisconn++;
				} else {
					// permanent disconnect
					if (d.getState() == Device.DEVICE_LOST_TEMP)
						log.info("Device[" + d.getId() + "] state change from " + Device.DEVICE_LOST_TEMP + " to "
								+ Device.DEVICE_LOST);
					d.setState(Device.DEVICE_LOST);
					permDisconn++;
					// if permanent disconnect, just remove from device lists;
					dit.remove();
				}
			} else {
				if (d.getState() == Device.DEVICE_LOST_TEMP || d.getState() == Device.DEVICE_LOST) {
					log.info("Device[" + d.getId() + "] state change from " + d.getState() + " to " + d.getPreState());
					d.setState(d.getPreState());
				}
			}
		}
		log.debug("Finishing Sync devices, Found " + tempDisconn + " temporary disconnected devices, " + permDisconn
				+ " Permenant disconnected device....");
	}
}
