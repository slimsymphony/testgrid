package frank.incubator.testgrid.agent.device;

import static frank.incubator.testgrid.common.CommonUtils.isWindows;

import java.util.Map;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;

public class AndroidDevice extends Device {

	public AndroidDevice(String id) {
		super(id);
		setStatus(new AndroidDeviceStatus(id));
		this.addAttribute(Constants.DEVICE_PLATFORM, Constants.PLATFORM_ANDROID);
	}

	public AndroidDevice() {
		super();
		setStatus(new AndroidDeviceStatus(id));
		this.addAttribute(Constants.DEVICE_PLATFORM, Constants.PLATFORM_ANDROID);
	}

	@Override
	public void setAttributes(Map<String, Object> attributes) {
		super.setAttributes(attributes);
		if (attributes.containsKey(Constants.DEVICE_SN)) {
			this.getStatus().setId(this.id);
		}
	}

	@Override
	public <T extends Object> void addAttribute(String key, T value) {
		super.addAttribute(key, value);
		if (Constants.DEVICE_SN.equals(key)) {
			this.getStatus().setId(this.id);
		}
	}

	@Override
	public void setId(String id) {
		super.setId(id);
		this.getStatus().setId(id);
	}

	@Override
	public boolean equals(Object obj) {
		if (super.equals(obj))
			return true;
		else {
			String tSn = (String) this.getAttributes().get(Constants.DEVICE_SN);
			String cSn = (String) ((Device) obj).getAttributes().get(Constants.DEVICE_SN);
			if (tSn != null && tSn.equals(cSn)) {
				return true;
			} else {
				return false;
			}
		}
	}

	private String adb() {
		if (isWindows())
			return "%ADB_HOME%\\adb";
		else
			return "$ADB_HOME/adb";
	}

	@Override
	public boolean isConnected() {
		StringBuilder sb = new StringBuilder(50);
		sb.append(adb()).append(" -s ").append(this.getAttribute(Constants.DEVICE_SN)).append(" shell pwd");
		try {
			String output = CommonUtils.exec(sb.toString(), null);
			if (output.toLowerCase().contains("error") || output.toLowerCase().contains("device not found")) {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}
}
