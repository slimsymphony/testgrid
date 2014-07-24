package frank.incubator.testgrid.client.message;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.Message;

import frank.incubator.testgrid.client.TaskClient;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.message.Pipe;
import frank.incubator.testgrid.common.model.Test;

/**
 * This class is going to run during task being published and finished.
 * For the sake that the agent side have crashed after it already begin to run a test.
 * This class will keep checking if the test still valid in remote agent.
 * 
 * @author Wang Frank
 * 
 */
public class TestExecutionChecker extends MessageListenerAdapter implements Runnable {

	public TestExecutionChecker( TaskClient client, OutputStream tracker, boolean stopAbnromalTest ) {
		super( "TestExecutionChecker", tracker );
		this.client = client;
		this.stopAbnromalTest = stopAbnromalTest;
		log.info( "TestExecutionChecker start to work for Task[" + client.getTask().getId() + "]! HeartbeatCheck status:" + stopAbnromalTest );
	}
	
	private TaskClient client;
	
	private long timeout = Constants.ONE_MINUTE * 20;
	
	private boolean runningFlag = true;
	
	private boolean stopAbnromalTest = false;
	
	private Map<String, Long> testIdx = new HashMap<String, Long>();
	
	protected void handleMessage( Message message ) {
		log.info( "TestExecutionChecker received a response." );
		try {
			String testId = getProperty( message, Constants.MSG_HEAD_TESTID, "" );
			long timestamp = message.getJMSTimestamp();
			testIdx.put( testId, timestamp );
			boolean isRunning = getProperty( message, Constants.MSG_HEAD_RESPONSE, true );
			String status = getProperty( message, Constants.MSG_HEAD_RESPONSE_DETAIL, "" );
			String from = getProperty( message, Constants.MSG_HEAD_FROM, "Unknown" );
			log.info( "Get a response from " + from + " at " + CommonUtils.parseTimestamp( timestamp ) 
					+ " give response that the test[" + testId +"]'s validation state is " + isRunning 
					+", and status is:" + status );
			if( !isRunning ) {
				if( stopAbnromalTest )
					client.testFail( testId, "Agent[" + from + "] side can't find alive test executor exists for this test[" + testId + "]" );
				else
					log.warn( "Agent[" + from + "] side can't find alive test executor exists for this test[" + testId + "]" );
			}
		} catch ( Exception e ) {
			log.error( "TestExecution check failed. original message is :" + message );
		}

	}
	
	public void stop() {
		String taskId = "";
		if( client != null )
			if( client.getTask() != null )
				taskId = client.getTask().getId();
		log.info( "TestExecutionChecker stop working for Task:[" + taskId +"]" );
		this.client = null;
		this.runningFlag = false;
		LogUtils.dispose( log );
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		while( runningFlag ) {
			if( client != null ) {
				try {
					TimeUnit.SECONDS.sleep( 20 );
					Pipe pipe = client.getHub().getPipe( Constants.HUB_AGENT_NOTIFICATION );
					TestListener tl = null;
					String host = null;
					long current = System.currentTimeMillis();
					for( Test t : client.getTestSlots().keySet() ) {
						// should be a running test
						tl = client.getTestSlots().get( t );
						if( tl != null && !( tl instanceof FakeTestListener ) ) {
							if( ! testIdx.containsKey( t.getId() ) )
								testIdx.put( t.getId(), current );
							else { 
								long lastTime = testIdx.get( t.getId() );
								if( ( current - lastTime ) > timeout ) {
									if( stopAbnromalTest )
										client.testFail( t.getId(), "The Test[" + t.getId() + "] didn't get heartbeat feedback over " + CommonUtils.convert( timeout ) +" for test[" + t.getId() + "], failed suspected, going to stop it." );
									else
										log.warn( "The Test[" + t.getId() + "] didn't get heartbeat feedback over " + CommonUtils.convert( timeout ) +" for test[" + t.getId() + "], failed suspected, going to stop it." );
									continue;
								}
							}
						
							log.info( "Sending check msg for test:" + t.getId() );
							host = tl.getHostAgent();
							Message msg = pipe.createMessage( Constants.MSG_TARGET_CLIENT );
							setProperty( msg, Constants.MSG_HEAD_TESTID, t.getId() );
							setProperty( msg, Constants.MSG_HEAD_TARGET, host );
							setProperty( msg, Constants.MSG_HEAD_NOTIFICATION_TYPE, Constants.NOTIFICATION_TEST );
							setProperty( msg, Constants.MSG_HEAD_NOTIFICATION_ACTION, Constants.ACTION_TEST_CHECK );
							pipe.send( msg );
						}
					}
					TimeUnit.SECONDS.sleep( 20 );
				}catch( Exception ex ) {
					log.error( "Checking Test execution met exception.", ex );
				}
			}
		}
	}
}
