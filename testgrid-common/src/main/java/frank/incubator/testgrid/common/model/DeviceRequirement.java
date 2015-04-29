package frank.incubator.testgrid.common.model;

import java.util.HashMap;

import frank.incubator.testgrid.common.CommonUtils;

/**
 * Test Device Requirements. Just drop old S40 concept of
 * "Main,Reference,Remote". We just believe all the Devices in test device poll
 * can be any role. And We didn't support(Expect) any scenario which need more
 * than 2 DUTs. So We can only have one Main device and one Ref device. This
 * definition can greatly decrease the complexity of device selection during
 * test.
 * 
 * @author Wang Frank
 *
 */
public class DeviceRequirement {
	private Device main = Device.createRequirement(new HashMap<String,Object>());
	private Device ref = null;

	public Device getMain() {
		return main;
	}

	public void setMain(Device main) {
		this.main = main;
	}

	public Device getRef() {
		return ref;
	}

	public void setReference(Device ref) {
		this.ref = ref;
	}

	public String toString() {
		return CommonUtils.toJson(this);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj instanceof DeviceRequirement))
			return false;
		DeviceRequirement dr = (DeviceRequirement) obj;
		Device drM = dr.getMain();
		Device drR = dr.getRef();
		if(main != null) {
			if(drM == null)
				return false;
			if(main.getAttributes().size() != drM.getAttributes().size())
				return false;
			for(String key : main.getAttributes().keySet())
				if(!main.getAttribute(key).equals(drM.getAttribute(key)))
					return false;
			if(main.getCompareAtts().size() != drM.getCompareAtts().size())
				return false;
			for(String att : main.getCompareAtts()) {
				if(!drM.getCompareAtts().contains(att))
					return false;
			}
		}
		if(ref != null) {
			if(drR == null)
				return false;
			if(ref.getAttributes().size() != drR.getAttributes().size())
				return false;
			for(String key : ref.getAttributes().keySet())
				if(!ref.getAttribute(key).equals(drR.getAttribute(key)))
					return false;
			if(ref.getCompareAtts().size() != drR.getCompareAtts().size())
				return false;
			for(String att : ref.getCompareAtts()) {
				if(!drR.getCompareAtts().contains(att))
					return false;
			}
		}
		return true;
	}
}
