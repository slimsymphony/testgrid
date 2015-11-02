package frank.incubator.testgrid.agent.device;

import java.util.Map;

import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;

public class IosDevice extends Device {

	public IosDevice(String id) {
		super(id);
		setStatus(new IosDeviceStatus(id));
		this.addAttribute(Constants.DEVICE_PLATFORM, Constants.PLATFORM_IOS);
	}

	public IosDevice() {
		super();
		setStatus(new IosDeviceStatus(id));
		this.addAttribute(Constants.DEVICE_PLATFORM, Constants.PLATFORM_IOS);
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

	@Override
	public boolean isConnected() {
		// TODO Auto-generated method stub
		return true;
	}
}
