package frank.incubator.testgrid.common.model;

import frank.incubator.testgrid.common.CommonUtils;

/**
 * Test Device Requirements.
 * Just drop old S40 concept of "Main,Reference,Remote".
 * We just believe all the Devices in test device poll can be any role.
 * And We didn't support(Expect) any scenario which need more than 2 DUTs.
 * So We can only have one Main device and one Ref device.
 * This definition can greatly decrease the complexity of device selection during test.
 * 
 * @author Wang Frank
 *
 */
public class DeviceRequirement {
	private Device main;
	private Device ref;

	public Device getMain() {
		return main;
	}

	public void setMain( Device main ) {
		this.main = main;
	}

	public Device getRef() {
		return ref;
	}

	public void setReference( Device ref ) {
		this.ref = ref;
	}

	public String toString() {
		return CommonUtils.toJson( this );
	}
}
