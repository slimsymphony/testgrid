package frank.incubator.testgrid.common.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Agent extends BaseObject {
	
	public Agent() {
		
	}
	
	public Agent( String host ) {
		this.id = host; 
		this.host = host;
	}

	public enum AgentStatus{
		NORMAL, MAINTAIN;

		public static AgentStatus parse( String string ) {
			if( string !=null ) {
				if( string.equals( NORMAL.name() ))
					return NORMAL;
				else if( string.equals( MAINTAIN.name() ))
					return MAINTAIN;
			}
			return null;
		}
	}
	
	private String host;
	private String ip;
	private AgentStatus status = AgentStatus.NORMAL;
	private Set<String> tags = new HashSet<String>();
	private List<Device> devices = new ArrayList<Device>();
	private List<Task> runningTasks = new ArrayList<Task>();
	private Map<String,String> properties = new HashMap<String,String>();
	private String desc;
        private int loadPercentage;

	
	public String getHost() {
		this.id = host;
		return host;
	}

	public void setHost( String host ) {
		this.host = host;
	}

	public String getIp() {
		return ip;
	}

	public void setIp( String ip ) {
		this.ip = ip;
	}

	public List<Device> getDevices() {
		return devices;
	}

	public void setDevices( List<Device> devices ) {
		this.devices = devices;
	}

	public List<Task> getRunningTasks() {
		return runningTasks;
	}

	public void setRunningTasks( List<Task> runningTasks ) {
		this.runningTasks = runningTasks;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	public void setProperties( Map<String, String> properties ) {
		this.properties = properties;
	}

	public AgentStatus getStatus() {
		return status;
	}

	public void setStatus( AgentStatus status ) {
		this.status = status;
	}

	public Set<String> getTags() {
		return tags;
	}

	public void setTags( Set<String> tags ) {
		this.tags = tags;
	}
	
	public void addTag( String tag ) {
		if( tag != null )
			tags.add( tag.toLowerCase() );
	}
	
	public void addTags( String... ts ) {
		if( ts != null )
			for( String tag : ts ) {
				tags.add( tag.toLowerCase() );
			}
	}
	
	public void removeTag( String tag ) {
		if( tag != null )
			tags.remove( tag.toLowerCase() );
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc( String desc ) {
		this.desc = desc;
	}

        public int getLoadPercentage() {
            return loadPercentage;
        }

        public void setLoadPercentage(int loadPercentage) {
            this.loadPercentage = loadPercentage;
        }

}
