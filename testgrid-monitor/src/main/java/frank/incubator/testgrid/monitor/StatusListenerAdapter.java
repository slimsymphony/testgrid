package frank.incubator.testgrid.monitor;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;

import javax.jms.Message;

import org.slf4j.Logger;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.model.Agent;
import frank.incubator.testgrid.common.model.Client;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;

public class StatusListenerAdapter extends MessageListenerAdapter {
	
	private Logger log = LogUtils.getLogger( "StatusListener" );
        private MonitorCache monitorCache;
	
	private int statusType = -1;
	
	public StatusListenerAdapter( int type, MonitorCache monitorCache ) {
		super( "StatusMonitor", null );
		this.statusType = type;
                this.monitorCache = monitorCache;
	}
	
	@Override
	public void onMessage( Message message ) {
		try {
			super.onMessage( message );
			String agentHost = getProperty( message, Constants.MSG_HEAD_FROM, "Unknown" );
			switch( statusType ) {
				case Constants.MSG_STATUS_AGENT:
					Agent agent = CommonUtils.fromJson( getProperty( message, Constants.MSG_HEAD_AGENTINFO, "" ), Agent.class);
                                        log.info( "Got a agent info:" + agent );
					monitorCache.updateAgent(agent);
					break;
				case Constants.MSG_STATUS_CLIENT:
					Client client = CommonUtils.fromJson( getProperty( message, Constants.MSG_HEAD_CLIENTINFO, "" ), Client.class);
                                        log.info( "Got a client info:" + client );
					monitorCache.updateClient(client);
					break;
				case Constants.MSG_STATUS_DEVICE:
					Device device = CommonUtils.fromJson( getProperty( message, Constants.MSG_HEAD_DEVICE_INFO, "" ), Device.class);
                                        log.info( "Got a device info:" + device );
					monitorCache.updateDevice(device);
					break;
				case Constants.MSG_STATUS_TASK:
					Task task = CommonUtils.fromJson( getProperty( message, Constants.MSG_HEAD_TASKSTATE, "" ), Task.class);
					log.info( "Got a task info:" + task );
					monitorCache.updateTask(task);
					break;
				case Constants.MSG_STATUS_TEST:
					String testId = getProperty( message, Constants.MSG_HEAD_TESTID, "" );
					//message.getLongProperty( Constants.MSG_HEAD_RESERVE_TIME );
					//message.getLongProperty( Constants.MSG_HEAD_RUNNING_TIME );
					Test test = CommonUtils.fromJson( getProperty( message, Constants.MSG_HEAD_TEST_INFO, "" ), Test.class);
					log.info( "Got a test info:" + test );
					if( test != null  )
						monitorCache.updateTest(test);
					break;
                                case Constants.MSG_STATUS_MONITOR:
                                        log.info("Monitor loop-back heartbeat received, ignore it.");
                                        break;
			}
                        
                        monitorCache.calcAgentLoad();
			
		} catch ( Exception ex ) {
			log.error( "Handle Received BoardCast Status Messgae met exception, msg=" + message, ex );
		}
	}

}
