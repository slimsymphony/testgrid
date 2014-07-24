package frank.incubator.testgrid.common.model;

import frank.incubator.testgrid.common.CommonUtils;

/**
 * Encapsulate the capacity in one Agent Node of assigned DeviceRequirement.
 * It contains how many sets of devices the current agent node device pool could provided.
 * And which include the available devices right now and busy devices which may release later. 
 *  
 * @author Wang Frank
 *
 */
public class DeviceCapacity {

	public DeviceCapacity( DeviceRequirement requirement ) {
		this.requirement = requirement;
	}
	
	DeviceRequirement requirement;
	
	int available;

	int needWait;

	public int getAvailable() {
		return available;
	}

	public void setAvailable( int available ) {
		this.available = available;
	}

	public int getNeedWait() {
		return needWait;
	}

	public void setNeedWait( int needWait ) {
		this.needWait = needWait;
	}

	public DeviceRequirement getRequirement() {
		return requirement;
	}

	public void setRequirement( DeviceRequirement requirement ) {
		this.requirement = requirement;
	}

	public String toString() {
		return CommonUtils.toJson( this );
	}
}
