package frank.incubator.testgrid.common.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;

/**
 * Device Model Class, describe the Device Information in test cloud.
 * 
 * @author Wang Frank
 * 
 */
public class Device extends BaseObject {

	public static final int DEVICE_FREE = 0;
	public static final int DEVICE_BUSY = 1;
	public static final int DEVICE_RESERVED = 2;
	public static final int DEVICE_LOST_TEMP = -1;
	public static final int DEVICE_LOST = -2;
	public static final int DEVICE_MAINTAIN = -3;
	public static final int DEVICE_NEW = 3;
	public static final int DEVICE_UPDATE = 4;

	public static final int ROLE_MAIN = 0;
	public static final int ROLE_REF = 1;

	public Device( String id ) {
		this.id = id;
		this.compareAtts.add( Constants.DEVICE_ID );
		this.attributes.put( Constants.DEVICE_HOST, CommonUtils.getHostName() );
		this.attributes.put( Constants.DEVICE_HOST_OS_TYPE, CommonUtils.getOsType() );
	}

	public Device() {
		this( "Device_" + CommonUtils.getHostName() + "_" + CommonUtils.generateToken( 5 ));
	}
	
	public String getStateString() {
		String stateString = "";

		switch ( state ) {
			case DEVICE_FREE:
				stateString = "FREE";
				break;
			case DEVICE_BUSY:
				stateString = "BUSY";
				break;
			case DEVICE_RESERVED:
				stateString = "RESERVED";
				break;
			case DEVICE_LOST_TEMP:
				stateString = "LOST_TEMP";
				break;
			case DEVICE_LOST:
				stateString = "LOST";
				break;
			case DEVICE_MAINTAIN:
				stateString = "MAINTAIN";
				break;
			case DEVICE_NEW:
				stateString = "NEW";
				break;
			case DEVICE_UPDATE:
				stateString = "UPDATE";
				break;
		}

		return stateString;

	}

	/**
	 * Create Requirement Instance. Device Requirement share the Device Class to
	 * save the device condition.
	 * 
	 * @param att
	 *            attributes Map which contains real attributes.
	 * @return
	 */
	public static Device createRequirement( Map<String, Object> att ) {
		Device d = new Device();
		d.compareAtts.clear();
		for( String name : att.keySet() )
			d.getCompareAtts().add( name );
		d.setAttributes( att );
		return d;
	}

	/**
	 * All the attributes of one common *Device*. Both static attributes like
	 * imei, sim-info, Or dynamic attributes like signal and user-defined
	 * attributes could be stored here.
	 */
	private Map<String, Object> attributes = new HashMap<String, Object>();

	/**
	 * Device State.
	 */
	private int state;

	/**
	 * Device State before it change.
	 */
	private int preState;

	/**
	 * Role of current running device.
	 */
	private int role;

	/**
	 * Detail task status of current device.
	 */
	private String taskStatus;

	/**
	 * Record attribute names which could be used to compare with other devices.
	 * Attribute names in this set should inclusive. e.g.: require> {imei:aaab}
	 * , Device> {imei:aaab, tag:FRT}, then the device could be used.
	 */
	private Set<String> compareAtts = new HashSet<String>();

	/**
	 * Record attribute names which could be used to compare with other devices.
	 * Attribute names in this set should exclusive. e.g.: require> {imei:aaab}
	 * , Device> {imei:aaab,tag:FRT}, then the device couldn't be used.
	 * Normally, this member variable shouldn't be used by DeviceRequirement.
	 */
	private Set<String> exclusiveAtts = new HashSet<String>();

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes( Map<String, Object> attributes ) {
		this.attributes = attributes;
		if ( attributes.containsKey( Constants.DEVICE_SN ) ) {
			this.id = "Device_" + CommonUtils.getHostName() + "_" + attributes.get( Constants.DEVICE_SN );
		}
	}

	public int getState() {
		return state;
	}

	public void setState( int state ) {
		if ( this.state != state )
			setChanged();
		this.state = state;
		if ( state == Device.DEVICE_LOST_TEMP ) {
			notifyObservers( Device.DEVICE_LOST_TEMP );
		} else if ( state == Device.DEVICE_LOST ) {
			notifyObservers( Device.DEVICE_LOST );
		} else {
			notifyObservers( Device.DEVICE_UPDATE );
		}
	}

	public int getPreState() {
		return preState;
	}

	public void setPreState( int preState ) {
		this.preState = preState;
	}

	public int getRole() {
		return role;
	}

	public void setRole( int role ) {
		this.role = role;
	}

	public Set<String> getCompareAtts() {
		return compareAtts;
	}

	public void setCompareAtts( Set<String> defaultAtts ) {
		this.compareAtts = defaultAtts;
	}

	public <T> void addExclusiveAttribute( String key, T value ) {
		addAttribute( key, value );
		this.exclusiveAtts.add( key );
	}

	public void setExclusiveAttribute( String key ) {
		if ( this.attributes.containsKey( key ) )
			this.exclusiveAtts.add( key );
	}

	public Set<String> getExclusiveAtts() {
		return exclusiveAtts;
	}

	public void setExclusiveAtts( Set<String> exclusiveAtts ) {
		this.exclusiveAtts = exclusiveAtts;
	}

	public <T> void addAttribute( String key, T value ) {
		this.attributes.put( key, value );
		if ( Constants.DEVICE_SN.equals( key ) ) {
			this.id = "Device_" + CommonUtils.getHostName() + "_" + value;
		}
	}

	public void removeAttribte( String key ) {
		this.attributes.remove( key );
	}

	@SuppressWarnings( "unchecked" )
	public <T> T getAttribte( String key ) {
		return ( T ) this.attributes.get( key );
	}

	public String getTaskStatus() {
		return taskStatus;
	}

	public void setTaskStatus( String status ) {
		this.taskStatus = status;
	}

	/**
	 * Check if the given device was compatible with requirements.
	 * 
	 * @param dv
	 * @return
	 */
	public boolean match( Device dv ) {
		// Check the exclusive attributes for compared Device.
		for ( String att : dv.getExclusiveAtts() ) {
			Object dvo = dv.getAttribte( att );
			Object co = this.getAttribte( att );
			if ( ( dvo != null && co == null ) || ( dvo != null && !dvo.equals( co ) ) ) {
				return false;
			}
		}
		
		return simpleMatch( dv );
	}
	
	/**
	 * Check if the given device was compatible with requirements.
	 * This different with match, because it won't check exclusive attributes.
	 * So please just use it for query purpose.
	 * 
	 * @param dv
	 * @return
	 */
	public boolean simpleMatch( Device dv ) {
		for ( String att : this.compareAtts ) {
			Object c = this.getAttribte( att );
			Object o = dv.getAttribte( att );
			if ( att.equalsIgnoreCase( "id" ) ) {
				o = dv.getId();
			}
			if ( ( c != null && !c.equals( o ) ) || ( c == null && null != o ) ) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean equals( Object obj ) {
		if ( obj == null )
			return false;
		if ( !( obj.getClass().equals( this.getClass() ) ) ) {
			return false;
		}
		if ( this.id.equals( ( ( BaseObject ) obj ).id ) )
			return true;
		else 
			return false;
	}
}
