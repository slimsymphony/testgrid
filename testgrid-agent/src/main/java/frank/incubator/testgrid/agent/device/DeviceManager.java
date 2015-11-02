package frank.incubator.testgrid.agent.device;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import frank.incubator.testgrid.common.CommonUtils;
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
	private ConcurrentHashMap<Device, Long> devices;
	private long permDisconnTimeout;
	private Observer observer;
	private Map<String, Object> defaultAttributes = new HashMap<String, Object>();
	private Map<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<String, ReentrantReadWriteLock>();

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

	public DeviceManager addDefaultAttribute(String key, Object val) {
		this.defaultAttributes.put(key, val);
		return this;
	}

	public DeviceManager removeDefaultAttribute(String key) {
		this.defaultAttributes.remove(key);
		return this;
	}

	public Map<String, Object> getDefaultAttributes() {
		return defaultAttributes;
	}

	public void setDefaultAttributes(Map<String, Object> defaultAttributes) {
		this.defaultAttributes = defaultAttributes;
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

	public Map<Device, Long> allDevices() {
		return new HashMap<Device, Long>(devices);
	}

	/**
	 * Reserve required device of give task, and change the device state from
	 * free to reserved.
	 * 
	 * @param task
	 * @return
	 */
	public synchronized Collection<Device> reserveDevices(Test test, DeviceRequirement requirements) {
		Collection<Device> candidates = new ArrayList<Device>();
		List<Device> devices = new ArrayList<Device>(this.devices.keySet());
		Collections.shuffle(devices);
		Device require = requirements.getMain();
		for (Device fake : devices) {
			Device dut = this.getDeviceById(fake.getId());
			locks.get(dut.getId()).readLock().lock();
			try {
				if (require.match(dut) && !candidates.contains(dut) && dut.getState() == Device.DEVICE_FREE
						&& (null == dut.getTaskStatus() || "".equals(dut.getTaskStatus()))) {
					locks.get(dut.getId()).readLock().unlock();
					try {
						if (!dut.isConnected()) {
							this.updateDeviceState(dut, Device.DEVICE_LOST_TEMP, dut.getTaskStatus(), true);
							continue;
						}
						dut.setRole(Device.ROLE_MAIN);
						log.info("device status before reserve:" + dut.info());
						this.updateDeviceState(dut, Device.DEVICE_RESERVED, test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId(), true);
					} finally {
						locks.get(dut.getId()).readLock().lock();
					}
					candidates.add(dut);
					log.info("Reserve Main device:" + dut.getId() + " for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + ",DEVICE STATE:"
							+ dut.getStateString());
					log.info("Debug device status:" + CommonUtils.toJson(this.devices.keySet()));
					break;
				}
			} finally {
				locks.get(dut.getId()).readLock().unlock();
			}
		}

		if (candidates.isEmpty())
			return null;

		Device requireRef = requirements.getRef();
		if (requireRef != null) {
			for (Device fake : devices) {
				Device dut = this.getDeviceById(fake.getId());
				locks.get(dut.getId()).readLock().lock();
				try {
					if (requireRef.match(dut) && !candidates.contains(dut) && dut.getState() == Device.DEVICE_FREE) {
						locks.get(dut.getId()).readLock().unlock();
						try {
							if (!dut.isConnected()) {
								this.updateDeviceState(dut, Device.DEVICE_LOST_TEMP, dut.getTaskStatus(), true);
								continue;
							}
							dut.setRole(Device.ROLE_REF);
							log.info("device status before reserve:" + dut.info());
							this.updateDeviceState(dut, Device.DEVICE_RESERVED, test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId(), true);
						} finally {
							locks.get(dut.getId()).readLock().lock();
						}
						candidates.add(dut);
						log.info("Reserve Reference device:" + dut.getId() + " for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() );
						break;
					}
				} finally {
					locks.get(dut.getId()).readLock().unlock();
				}
			}
			if (candidates.size() < 2)
				return null;
		}
		devices.clear();
		return candidates;
	}

	/**
	 * Relase assigned devices from other state to Free.
	 * 
	 * @param dss
	 */
	public void releaseDevices(Device... dss) {
		if (dss != null && dss.length > 0) {
			for (Device d : dss) {
				Device device = getDeviceById(d.getId());
				if (device != null) {
					device.setRole(Device.ROLE_MAIN);
					log.info("Try to relase device:" + device.getId() + " from: " + device.getTaskStatus() + ",device state:" + device.getStateString());

					if (!device.isConnected()) {
						this.updateDeviceState(device, Device.DEVICE_FREE, "", true);
						this.updateDeviceState(device, Device.DEVICE_LOST_TEMP, "", true);
						log.info("Device:" + device.getId() + " is lost connection during releasing.");
						continue;
					} else {
						this.updateDeviceState(device, Device.DEVICE_FREE, "", true);
					}
					log.info("Releasing device:" + device.getId() + " device state:" + device.getStateString());
				}
			}
		}
	}

	public void releaseDevices(Collection<Device> dss) {
		if (dss != null && dss.size() > 0) {
			releaseDevices(dss.toArray(new Device[0]));
		}
	}

	private void mergeInfo(Device currentDevice, Device updateDevice) {
		locks.get(currentDevice.getId()).writeLock().lock();
		try {
			currentDevice.getAttributes().clear();
			currentDevice.getAttributes().putAll(updateDevice.getAttributes());
			currentDevice.getExclusiveAtts().clear();
			currentDevice.getExclusiveAtts().addAll(updateDevice.getExclusiveAtts());
			currentDevice.getCompareAtts().clear();
			currentDevice.getCompareAtts().addAll(updateDevice.getCompareAtts());
			currentDevice.setStatus(updateDevice.getStatus());
			currentDevice.setLastUpdated(updateDevice.getLastUpdated());
			if(currentDevice.getState() == Device.DEVICE_LOST_TEMP || currentDevice.getState() == Device.DEVICE_LOST) {
				this.updateDeviceState(currentDevice, Device.DEVICE_FREE, "", false);
			}
		} finally {
			locks.get(currentDevice.getId()).writeLock().unlock();
		}
	}

	/*
	 * private void merge(Device device, Device duu) {
	 * device.setId(duu.getId()); device.setRole(duu.getRole());
	 * device.setTaskStatus(duu.getTaskStatus());
	 * device.setPreState(duu.getPreState()); device.setState(duu.getState()); }
	 */

	public void removeDevice(Device device) {
		locks.remove(device.getId());
		devices.remove(device);
		device.getExclusiveAtts().clear();
		device.getCompareAtts().clear();
		device.getAttributes().clear();
		device.setStatus(null);
	}

	public void updateDeviceState(Device d, int state, String taskStatus, boolean withLock) {
		log.info("Before update State:" + d.info());
		if (withLock) {
			locks.get(d.getId()).writeLock().lock();
			try {
				d.setPreState(d.getState());
				d.setTaskStatus(taskStatus);
				d.setState(state);
			} finally {
				locks.get(d.getId()).writeLock().unlock();
			}
		} else {
			d.setPreState(d.getState());
			d.setTaskStatus(taskStatus);
			d.setState(state);
		}
		log.info("After update State:" + d.info());
	}

	public void setDeviceState(String id, int state) {
		if (id != null) {
			Device d = this.getDeviceById(id);
			if (d != null) {
				this.updateDeviceState(d, state, d.getTaskStatus(), true);
			}
		}
	}

	public Device getDeviceById(String id) {
		for (Device d : this.devices.keySet()) {
			locks.get(d.getId()).readLock().lock();
			try {
				if (d.getId().equals(id)) {
					return d;
				}
			} finally {
				locks.get(d.getId()).readLock().unlock();
			}
		}
		return null;
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
			locks.get(d.getId()).readLock().lock();
			try {
				if (cond.simpleMatch(d)) {
					results.add(d);
				}
			} finally {
				locks.get(d.getId()).readLock().unlock();
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
		log.info("Start Sync devices...");
		long current = System.currentTimeMillis();
		Device currDevice = null;
		for (Device d : devicesStatus) {
			if (!d.getAttribute(Constants.DEVICE_PLATFORM).equals(detectorPlatform))
				continue;
			for (String key : defaultAttributes.keySet()) {
				Object val = defaultAttributes.get(key);
				if (val != null)
					d.addAttribute(key, val);
			}
			currDevice = this.getDeviceById(d.getId());
			if (currDevice == null) { // add New
				currDevice = d;
				devices.putIfAbsent(currDevice, current);
				locks.put(currDevice.getId(), new ReentrantReadWriteLock());
				log.info("New device added.{}", currDevice);
				currDevice.notifyObservers(Device.DEVICE_NEW);
			} else { // update
				mergeInfo(currDevice, d);
				devices.replace(currDevice, current);
				log.info("Device info updated.{}", currDevice.toString());
				currDevice.notifyObservers(Device.DEVICE_UPDATE);
			}
		}

		int tempDisconn = 0;
		int permDisconn = 0;
		Set<Device> removeDevices = new HashSet<Device>();
		for (Entry<Device, Long> entry : devices.entrySet()) {
			Device d = entry.getKey();
			long updateTime = entry.getValue();
			locks.get(d.getId()).writeLock().lock();
			try {
				if (!d.getAttribute(Constants.DEVICE_PLATFORM).equals(detectorPlatform))
					continue;
				if (updateTime != current) {
					if (this.permDisconnTimeout > (current - updateTime)) {
						// temporary disconnect
						if (d.getState() != Device.DEVICE_LOST_TEMP && d.getState() != Device.DEVICE_LOST) {
							this.updateDeviceState(currDevice, Device.DEVICE_LOST_TEMP, d.getTaskStatus(), false);
							log.info("Device[" + d.getId() + "] state change from " + d.getPreState() + " to " + Device.DEVICE_LOST_TEMP);
						}
						tempDisconn++;
					} else {
						// permanent disconnect
						if (d.getState() == Device.DEVICE_LOST_TEMP)
							log.info("Device[" + d.getId() + "] state change from " + Device.DEVICE_LOST_TEMP + " to " + Device.DEVICE_LOST);
						this.updateDeviceState(d, Device.DEVICE_LOST, d.getTaskStatus(), false);
						permDisconn++;
						// if permanent disconnect, just remove from device
						// lists;
						// dit.remove();
						removeDevices.add(d);
					}
				} else {
					if (d.getState() == Device.DEVICE_LOST_TEMP || d.getState() == Device.DEVICE_LOST) {
						log.info("Device[" + d.getId() + "] state change from " + d.getState() + " to " + d.getPreState());
						this.updateDeviceState(d, d.getPreState(), d.getTaskStatus(), false);
					}
				}
			} finally {
				locks.get(d.getId()).writeLock().unlock();
			}
		}
		for (Device d : removeDevices) {
			this.removeDevice(d);
		}
		removeDevices.clear();
		log.debug("Finishing Sync devices, Found " + tempDisconn + " temporary disconnected devices, " + permDisconn + " Permenant disconnected device....");
	}
}
