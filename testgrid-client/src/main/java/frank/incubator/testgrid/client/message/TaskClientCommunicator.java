package frank.incubator.testgrid.client.message;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Semaphore;

import javax.jms.Message;

import com.google.gson.reflect.TypeToken;
import frank.incubator.testgrid.client.TaskClient;
import frank.incubator.testgrid.client.TaskStatus;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.file.FileTransferTask;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.model.DeviceCapacity;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.Test.Phase;

/**
 * Handler of "HUB_TASK_COMMUNICATION" Queue. Which including Task confirmation.
 * 
 * @author Wang Frank
 * 
 */
public class TaskClientCommunicator extends MessageListenerAdapter {

	public TaskClientCommunicator( TaskClient client, Semaphore lock, OutputStream tracker ) {
		super( "TaskClientCommunicator", tracker );
		this.client = client;
		this.lock = lock;
	}

	private TaskClient client;

	private Semaphore lock;

	public TaskClient getClient() {
		return client;
	}

	public void setClient( TaskClient client ) {
		this.client = client;
	}

	private String convertState( int state ) {
		switch( state ) {
			case Constants.MSG_TASK_ACCEPT:
				return "MSG_TASK_ACCEPT";
			case Constants.MSG_TASK_CONFIRM:
				return "MSG_TASK_CONFIRM";
			case Constants.MSG_TEST_READY:
				return "MSG_TEST_READY";
			case Constants.MSG_TEST_FAIL:
				return "MSG_TEST_FAIL";
			case Constants.MSG_TEST_FINISHED:
				return "MSG_TEST_FINISHED";
			case Constants.MSG_TASK_RESERVE:
				return "MSG_TASK_RESERVE";
			case Constants.MSG_TASK_START:
				return "MSG_TASK_START";
			case Constants.MSG_START_FT_FETCH:
				return "MSG_START_FT_FETCH";
			case Constants.MSG_TEST_FINISH_CONFIRM:
				return "MSG_TEST_FINISH_CONFIRM";
			case Constants.MSG_TASK_CANCEL:
				return "MSG_TASK_CANCEL";
			default:
				return "UNKNOWN";
		}
	}
	
	@Override
	public void onMessage( Message message ) {
		//super.onMessage( message );
		try {
			lock.acquire();
			String from = getProperty( message, Constants.MSG_HEAD_FROM, "Unknown" );
			String taskID = getProperty( message, Constants.MSG_HEAD_TASKID, "" );
			String testId = null;
			Test test = null;
			if ( client.getTask().getId().equals( taskID ) ) {
				int state = getProperty( message, Constants.MSG_HEAD_TRANSACTION, 0 );
				log.info( "Received task message: " + state + "-" +convertState( state ) + " from "+from );
				switch ( state ) {
					case Constants.MSG_TASK_ACCEPT:
						if ( client.getStatus() == TaskStatus.PUBLISHED ) {
							DeviceCapacity capacity = CommonUtils.fromJson(
									getProperty( message, Constants.MSG_HEAD_RESPONSE, "" ), DeviceCapacity.class );
							log.info( "[" + from + "] give response, capacity:" + capacity );
							// check capacity and compare with test size.
							if ( capacity.getAvailable() > 0 || capacity.getNeedWait() > 0 ) {
								log.info( "New candidate coming up from " + from );
								client.getAgentCandidates().put( from, capacity );
								client.handleNewAccpetance();
							} else {
								client.getAgentCandidates().remove( from );
								log.info( "[" + from + "] didn't have device resources needed for current test." );
							}
						}
						break;
					case Constants.MSG_TASK_CONFIRM:
						int reply = getProperty( message, Constants.MSG_HEAD_RESPONSE, 0 );
						if ( reply == Constants.RESERVE_SUCC ) {
							String dvs = getProperty( message, Constants.MSG_HEAD_RESERVED_DEVICES, "" );
							Map<String,Integer> devices = CommonUtils.fromJson( dvs, new TypeToken<Map<String,Integer>>(){}.getType() );
							client.setStatus( TaskStatus.RESERVED );
							testId = getProperty( message, Constants.MSG_HEAD_TESTID, "" );
							log.info( "Agent [" + from + "] have already reserved the device for this test[" +testId+"]."  );
							for( String id : devices.keySet() ) {
								log.info( id + " have been reserved as Role[" + devices.get( id )+"]" );
							}
							test = client.getTestById( testId );
							Collection<File> files = new ArrayList<File>();
							File af = null;
							for( String fn : test.getArtifacts().keySet() ) {
								af = new File( client.getWorkspace(), fn );
								if( af.exists() && af.isFile() && af.length() == test.getArtifacts().get( fn ) ) {
									files.add( af );
								}else {
									String reason = "File[" + af.getAbsolutePath() + "] invalid, maybe not exists or not a file, or length[" + af.length() + "] not equals to planned transfer size:" + test.getArtifacts().get( fn );
									log.warn( reason );
									Message msg = client.getHub().createMessage( Constants.BROKER_TASK );
									setProperty( msg, Constants.MSG_HEAD_TARGET, from );
									setProperty( msg, Constants.MSG_HEAD_TESTID, testId );
									setProperty( msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_CANCEL );
									setProperty( msg, Constants.MSG_HEAD_ERROR, reason );
									client.getHub().getPipe( Constants.HUB_TASK_COMMUNICATION ).send( msg );
									return;
								}
							}
									
							FileTransferTask ftTask = new FileTransferTask( Constants.MSG_TARGET_CLIENT+CommonUtils.getHostName(), from, taskID, testId, files );
							client.getFts().sendTo( ftTask );
							this.client.getTask().setPhase(Task.Phase.STARTED);
							
						} else if ( reply == Constants.RESERVE_FAILED ) {
							// TODO: reserve failed. just forget it, remove the
							// listener from testslots.
							testId = getProperty( message, Constants.MSG_HEAD_TESTID, "" );
							log.error( "Reserved device failed for Test[" + testId + "]." );
							client.getTestSlots().put( client.getTestById( testId ), new FakeTestListener(client.getTestById( testId )) );
						} else {
							log.warn( "Illegal Message:" + CommonUtils.toJson( message ) );
						}
						break;
					case Constants.MSG_TEST_READY:
						testId = getProperty( message, Constants.MSG_HEAD_TESTID, "" );
						test = client.getTestById( testId );
						test.setPhase( Phase.STARTED );
						Message msg = client.getHub().createMessage( Constants.BROKER_TASK );
						setProperty( msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_START );
						setProperty( msg, Constants.MSG_HEAD_TARGET, from );
						setProperty( msg, Constants.MSG_HEAD_TASKID, test.getTaskID() );
						setProperty( msg, Constants.MSG_HEAD_TESTID, testId );
						client.getHub().getPipe( Constants.HUB_TASK_COMMUNICATION ).getProducer().send( msg );
						break;
					case Constants.MSG_TEST_FAIL:
						testId = getProperty( message, Constants.MSG_HEAD_TESTID, "" );
						String reason = getProperty( message, Constants.MSG_HEAD_ERROR, "" );
						log.error( "Test[" + testId +"] failed in agent side for:" + reason );
						client.testFail( testId, reason );
						break;
					case Constants.MSG_TEST_FINISHED:
						testId = getProperty( message, Constants.MSG_HEAD_TESTID, "" );
						final int result = getProperty( message, Constants.MSG_HEAD_TEST_RESULT, 0 );
						final Test itest = client.getTestById( testId );
						String errorMsg = getProperty( message, Constants.MSG_HEAD_ERROR, "Unknown" );
						if( result == Constants.MSG_TEST_SUCC ) {
							Message msgi = client.getHub().createMessage( Constants.BROKER_TASK );
							setProperty( msgi, Constants.MSG_HEAD_TESTID, testId );
							setProperty( msgi, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FINISH_CONFIRM );
							setProperty( msgi, Constants.MSG_HEAD_TARGET, from );
							setProperty( msgi, Constants.MSG_HEAD_TEST_SUCC, true );
							client.getHub().getPipe( Constants.HUB_TASK_COMMUNICATION ).send( msgi );
							client.testFinished( itest );
						} else
							client.testFail( testId, errorMsg );
						break;
					default:
						log.warn( "Can't handle this transction Item: " + state );
				}
			} else {
				log.warn( "Received a mismatched message belong to task:" + taskID );
			}
		} catch ( Exception e ) {
			log.error( "Handling Message in TaskCommunicator got Exception. Message:" + CommonUtils.toJson( message ), e );
		} finally {
			lock.release();
		}
	}

}
