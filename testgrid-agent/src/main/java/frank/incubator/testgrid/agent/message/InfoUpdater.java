package frank.incubator.testgrid.agent.message;

import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import frank.incubator.testgrid.agent.AgentNode;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.model.Test;

/**
 * Task Information Updater. Which update the tasks running on current agentNode
 * information by schedule.
 * 
 * @author Wang Frank
 * 
 */
public class InfoUpdater extends Thread {

	public InfoUpdater( AgentNode agent ) {
		this.agent = agent;
		this.setName( "InfoUpdater" );
		log = LogUtils.get( "TaskInfoUpdater" );
	}

	private AgentNode agent;

	private LogConnector log;

	private boolean running = true;

	private long schedule = Constants.ONE_MINUTE;

	public boolean isRunning() {
		return running;
	}

	public void setRunning( boolean running ) {
		this.running = running;
	}

	public long getSchedule() {
		return schedule;
	}

	public void setSchedule( long schedule ) {
		this.schedule = schedule;
	}

	@Override
	public void run() {
		while ( running ) {
			try {
				agentInfoUpdate();
				//testInfoUpdate();
				TimeUnit.SECONDS.sleep( 30 );
			} catch ( Exception e ) {
				log.error( "Met exception while update taskInfo.", e );
			}
		}
	}

	@SuppressWarnings( "unused" )
	private void testInfoUpdate() throws MessageException {
		try {
			Message msg = null;
			long time = 0;
			MessageProducer producer = agent.getHub().getPipe( Constants.HUB_TEST_STATUS ).getProducer();
			for ( Test t : agent.getReservedTests().keySet() ) {
				time = agent.getReservedTests().get( t );
				msg = agent.getHub().createMessage( Constants.BROKER_STATUS );
				setProperty( msg, Constants.MSG_HEAD_STATUS_TYPE, Constants.MSG_STATUS_TEST );
				setProperty( msg, Constants.MSG_HEAD_TESTID, t.getId() );
				setProperty( msg, Constants.MSG_HEAD_TEST_INFO, t.toString() );
				setProperty( msg, Constants.MSG_HEAD_RESERVE_TIME, time );
				producer.send( msg );
			}

			for ( Test t : agent.getRunningTests().keySet() ) {
				msg = agent.getHub().createMessage( Constants.BROKER_STATUS );
				setProperty( msg, Constants.MSG_HEAD_FROM, CommonUtils.getHostName() );
				setProperty( msg, Constants.MSG_HEAD_STATUS_TYPE, Constants.MSG_STATUS_TEST );
				setProperty( msg, Constants.MSG_HEAD_TESTID, t.getId() );
				setProperty( msg, Constants.MSG_HEAD_TEST_INFO, t.toString() );
				setProperty( msg, Constants.MSG_HEAD_RUNNING_TIME, ( System.currentTimeMillis() - time ) );
				producer.send( msg );
			}

		} catch ( JMSException e ) {
			throw new MessageException( e );
		}
	}

	public void agentInfoUpdate() throws MessageException {
		try {
			Message msg = agent.getHub().createMessage( Constants.BROKER_STATUS );
			setProperty( msg, Constants.MSG_HEAD_AGENTINFO, agent.getAgentInfo( AgentNode.DEVICE_INCLUDE ).toString() );
			agent.getHub().getPipe( Constants.HUB_AGENT_STATUS ).send( msg );
		}catch( Exception ex ) {
			throw new MessageException( "AgentInfoUpdater send message failed.", ex );
		}
	}
}
