package frank.incubator.testgrid.agent.device;

import java.util.Map;

import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;

public class AndroidDevice extends Device {

	public AndroidDevice( String  id ) {
		super( id );
		status = new AndroidDeviceStatus( id );
	}
	
	public AndroidDevice( ) {
		super();
		status = new AndroidDeviceStatus( id );
	}
	
	private AndroidDeviceStatus status;

	public AndroidDeviceStatus getStatus() {
		return status;
	}

	public void setStatus( AndroidDeviceStatus status ) {
		this.status = status;
	}
	
	@Override
	public void setAttributes( Map<String, Object> attributes ) {
		super.setAttributes( attributes );
		if( attributes.containsKey( Constants.DEVICE_SN ) ){
			this.status.setId( this.id );
		}
	}
	
	@Override
	public <T extends Object> void addAttribute( String key, T value ) {
		super.addAttribute( key, value );
		if( Constants.DEVICE_SN.equals( key ) ) {
			this.status.setId( this.id );
		}
	}
	
	@Override
	public void setId( String id ) {
		super.setId( id );
		this.status.setId( id );
	}
	
	@Override
	public boolean equals( Object obj ) {
		if( super.equals( obj ) )
			return true;
		else {
			String tSn = ( String ) this.getAttributes().get( Constants.DEVICE_SN );
			String cSn = ( String ) ( ( Device ) obj ).getAttributes().get( Constants.DEVICE_SN );
			if ( tSn != null && tSn.equals( cSn ) ) {
				return true;
			} else {
				return false;
			}
		}
	}
}
