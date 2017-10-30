package frank.incubator.testgrid.agent.plugin;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.Device;

public class WlanMonitorPlugin extends AbstractAgentPlugin<Collection<Device>> {
	private final Logger log = LogUtils.getLogger("WlanMonitorPlugin");
	public static String NOTIFICATION_URL = "http://127.0.0.1/sendnotification.json";
	public static String NOTIFY_USERS = "";
	private Map<Device, Long> suspectList = new ConcurrentHashMap<Device, Long>();
	private Map<Device, Long> issueDeviceList = new ConcurrentHashMap<Device, Long>();
	private long beginTime = System.currentTimeMillis();
	private Map<Device, Long> issueStatistic = new ConcurrentHashMap<Device, Long>();
	private String notifyUsers = NOTIFY_USERS;
	private String notifyUrl = NOTIFICATION_URL;
	private boolean switchToMaintain = false;
	private boolean sendNotification = false;

	public boolean isSwitchToMaintain() {
		return switchToMaintain;
	}

	public void setSwitchToMaintain(boolean switchToMaintain) {
		this.switchToMaintain = switchToMaintain;
	}

	public boolean isSendNotification() {
		return sendNotification;
	}

	public void setSendNotification(boolean sendNotification) {
		this.sendNotification = sendNotification;
	}

	public String getNotifyUrl() {
		return notifyUrl;
	}

	public void setNotifyUrl(String notifyUrl) {
		this.notifyUrl = notifyUrl;
	}

	public String getNotifyUsers() {
		return notifyUsers;
	}

	public void setNotifyUsers(String notifyUsers) {
		this.notifyUsers = notifyUsers;
	}

	@Override
	public Collection<Device> call() {
		log.info("Start checking wlan status");
		notifyUsers = (String) this.getAttribute("notifyUsers", NOTIFY_USERS);
		notifyUrl = (String) this.getAttribute("notifyUrl", NOTIFICATION_URL);
		switchToMaintain = (boolean) this.getAttribute("switchToMaintain", false);
		long current = System.currentTimeMillis();
		for (Device device : dm.allDevices().keySet()) {
			switch (device.getState()) {
				case Device.DEVICE_FREE:
				case Device.DEVICE_BUSY:
				case Device.DEVICE_MAINTAIN:
				case Device.DEVICE_RESERVED:
					String val = device.getAttribute(Constants.DEVICE_IP_WLAN);
					if (val == null) {
						if (!issueStatistic.containsKey(device)) {
							issueStatistic.put(device, 0L);
						}
						issueStatistic.put(device, issueStatistic.get(device) + 1);
						if (!issueDeviceList.containsKey(device)) {
							if (suspectList.containsKey(device)) {
								issueDeviceList.put(device, current);
								suspectList.remove(device);
								sendNotification(device, current, true);
								if (switchToMaintain) {
									dm.setDeviceState(device.getId(), Device.DEVICE_MAINTAIN);
								}
							} else {
								suspectList.put(device, current);
							}
						}
					} else {
						boolean available = false;
						if (val.trim().startsWith("192.168")) {
							log.info("Current Device connect to a inner NAT network, can't detectable. Ip:" + val);
							available = true;
						} else {
							try {
								available = InetAddress.getByName(val).isReachable(3000);
							} catch (Exception e) {
								log.error("Can't reach " + val + "," + e.getMessage());
							}
							// reconfirm for 3 times, each time wait for 3
							// seconds
							if (!available) {
								for (int i = 0; i < 3; i++) {
									try {
										TimeUnit.SECONDS.sleep(3 + i);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
									try {
										available = InetAddress.getByName(val).isReachable(3000);
										if (available)
											break;
									} catch (Exception e) {
										log.error("Can't reach " + val + " retry " + (i + 1) + "times," + e.getMessage());
									}
								}
							}
						}

						if (available) {
							if (issueDeviceList.containsKey(device)) {
								if (switchToMaintain) {
									dm.setDeviceState(device.getId(), Device.DEVICE_FREE);
								}
								issueDeviceList.remove(device);
								sendNotification(device, current, false);
							} else if (suspectList.containsKey(device)) {
								suspectList.remove(device);
							}
						} else {
							if (!issueStatistic.containsKey(device)) {
								issueStatistic.put(device, 0L);
							}
							issueStatistic.put(device, issueStatistic.get(device) + 1);
							if (!issueDeviceList.containsKey(device)) {
								if (suspectList.containsKey(device)) {
									if (switchToMaintain) {
										dm.setDeviceState(device.getId(), Device.DEVICE_MAINTAIN);
									}
									issueDeviceList.put(device, current);
									suspectList.remove(device);
									sendNotification(device, current, true);
								} else {
									suspectList.put(device, current);
								}

							}
						}
					}
					break;
			}
		}
		log.info("Finish checking wlan status");
		return issueDeviceList.keySet();
	}

	private void sendNotification(Device device, long time, boolean warn) {
		sendNotification = (boolean) this.getAttribute("sendNotification", false);
		if (!sendNotification) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		Map<String, String> data = new HashMap<String, String>();
		if (warn) {
			data.put("topic", "设备Wlan连接丢失");
			data.put(
					"content",
					"品牌为" + device.getAttribute(Constants.DEVICE_MANUFACTURER) + " SN为" + device.getAttribute(Constants.DEVICE_SN) + "的"
							+ device.getAttribute(Constants.DEVICE_PRODUCTCODE) + "失去Wifi连接. 检测时间:" + CommonUtils.parseTimestamp(time));
		} else {
			data.put("topic", "设备Wlan连接已恢复");
			data.put(
					"content",
					"品牌为" + device.getAttribute(Constants.DEVICE_MANUFACTURER) + " SN为" + device.getAttribute(Constants.DEVICE_SN) + "的"
							+ device.getAttribute(Constants.DEVICE_PRODUCTCODE) + " Wifi连接已恢复. 检测时间:" + CommonUtils.parseTimestamp(time));
		}
		data.put("receivers", notifyUsers);
		int ret = CommonUtils.httpPost(notifyUrl, data, "UTF-8", sb);
		if (ret == 200) {
			log.info("Send Notification return:" + sb.toString());
		} else {
			log.error("Send Notification Failed. Invoke " + notifyUrl + " return status code " + ret);
		}
	}

	@Override
	public void onFailure(Throwable t) {
		log.error("Wlan Monitor Plugin failed.", t);
	}

	@Override
	public void onSuccess(Collection<Device> result) {
		if (result == null || result.isEmpty()) {
			log.info("Great! No Device out of Wlan Service.");
		} else {
			log.warn(result.size() + " devices out of Wlan service. they were:" + CommonUtils.toJson(result));
		}
		log.info("Current statistics begin from {} was: {}", beginTime, CommonUtils.toJson(issueStatistic));
	}

}
