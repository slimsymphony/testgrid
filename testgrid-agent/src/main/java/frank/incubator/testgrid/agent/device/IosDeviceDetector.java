package frank.incubator.testgrid.agent.device;

import static frank.incubator.testgrid.common.CommonUtils.exec;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;

import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;

public class IosDeviceDetector extends DeviceDetector {

	public IosDeviceDetector(File workspace, DeviceManager deviceExplorer, long scanInterval) {
		super(workspace, deviceExplorer, scanInterval);
	}

	@Override
	public void refresh() {
		try {
			// udidetect -z
			// CommClient get_udidlist
			parseProducts(exec("curl -s http://127.0.0.1:8081/getdeviceid", null, 10000L, log.getLog()));
		} catch (Exception ex) {
			log.error("Check Adb Status failed.", ex);
		}
	}

	private void parseProducts(String str) {
		Collection<Device> devices = new ArrayList<Device>();
		BufferedReader br = new BufferedReader(new StringReader(str));
		String line = null;
		try {
			Device d = null;
			while ((line = br.readLine()) != null) {
				try {
					line = line.trim();
					// 2015-01-07 11:59:59.342 CommClient[15934:507]
					// udid:7b15f052424a89ae5a749576eb3c91f839084b96
					// device_name:“Administrator”的 iPhone product_type:iPhone 6
					// ios_version:8.0.2 resolution:1334x750
					if (!line.isEmpty() && line.startsWith("udid")) {
						// String time = line.substring(0,
						// line.indexOf("CommClient")).trim();
						String sn = line.substring(line.indexOf("udid:") + 5, line.indexOf("device_name") - 1).trim();
						String device_name = line.substring(line.indexOf("device_name:") + 12, line.indexOf("product_type") - 1).trim();
						String product_type = line.substring(line.indexOf("product_type:") + 13, line.indexOf("ios_version") - 1).trim();
						String ios_version = line.substring(line.indexOf("ios_version:") + 12, line.indexOf("resolution") - 1).trim();
						String resolution = line.substring(line.indexOf("resolution:") + 11, line.indexOf("ip:") - 1).trim();
						String ip = line.substring(line.indexOf("ip:") + 3).trim();
						d = new IosDevice();
						d.addAttribute(Constants.DEVICE_SN, sn);
						d.addAttribute(Constants.DEVICE_PRODUCT_NAME, device_name);
						d.addAttribute(Constants.DEVICE_PRODUCTCODE, product_type);
						d.addAttribute(Constants.DEVICE_PLATFORM_VERSION, ios_version);
						d.addAttribute(Constants.DEVICE_RESOLUTION, resolution);
						d.addAttribute(Constants.DEVICE_PLATFORM, Constants.PLATFORM_IOS);
						d.addAttribute(Constants.DEVICE_HOST_OS_TYPE, Constants.OS_MAC);
						d.addAttribute(Constants.DEVICE_MANUFACTURER, "Apple");
						d.addAttribute(Constants.DEVICE_IP_WLAN, ip);
						d.addExclusiveAttribute(Constants.DEVICE_HOST_OS_TYPE, Constants.OS_MAC);
						// d.setLastUpdated(CommonUtils.parseDate(time,
						// "yyyy-MM-dd HH:mm:ss.SSS").getTime());
						loadUserDefined(d);
						if (!devices.contains(d))
							devices.add(d);
						log.debug("Append product:" + sn + "\n" + d.toString());
					}
				} catch (Exception e) {
					log.error("Parse product sn failed. line=" + line, e);
				}
			}

			this.deviceManager.sync(devices, Constants.PLATFORM_IOS);
		} catch (Exception ex) {
			log.error("Parse products Info met error.", ex);
		}
	}
}
