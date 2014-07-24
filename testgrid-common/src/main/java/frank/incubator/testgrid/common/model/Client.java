package frank.incubator.testgrid.common.model;

import frank.incubator.testgrid.common.CommonUtils;

public class Client extends BaseObject {
	
	private String host;
	
	private String taskId;
        
        Status status;
        
        public Client(){
            this.id = "Client_"+System.currentTimeMillis()+"_"+CommonUtils.generateToken( 5 );
        }
	
	public String getHost() {
		return host;
	}

	public void setHost( String host ) {
		this.host = host;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId( String taskId ) {
		this.taskId = taskId;
	}
        
	public Status getStatus() {
		return status;
	}

	public void setStatus( Status status ) {
		if( status != this.status )
			this.setChanged();
		this.status = status;
		this.notifyObservers();
	}

	public enum Status{
		INUSE,IDLE
	}
}
