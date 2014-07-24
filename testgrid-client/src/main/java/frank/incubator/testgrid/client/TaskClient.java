package frank.incubator.testgrid.client;

import static frank.incubator.testgrid.common.Constants.HUB_TASK_COMMUNICATION;
import static frank.incubator.testgrid.common.Constants.HUB_TASK_PUBLISH;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;

import frank.incubator.testgrid.client.message.FakeTestListener;
import frank.incubator.testgrid.client.message.NotificationHandler;
import frank.incubator.testgrid.client.message.TaskClientCommunicator;
import frank.incubator.testgrid.client.message.TestExecutionChecker;
import frank.incubator.testgrid.client.message.TestListener;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.StatusChangeNotifier;
import frank.incubator.testgrid.common.file.FileTransferDescriptor;
import frank.incubator.testgrid.common.file.FileTransferResource;
import frank.incubator.testgrid.common.file.FileTransferService;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.BrokerDescriptor;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.message.Pipe;
import frank.incubator.testgrid.common.model.Agent;
import frank.incubator.testgrid.common.model.Client;
import frank.incubator.testgrid.common.model.DeviceCapacity;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.Test.Phase;

/**
 * Task Client, intelligent client to publish a task and keep tracking the task process.
 * Who is controlling the overall task life-cycle working with message responser and other components.
 * 
 * @author Wang Frank
 *
 */
public class TaskClient extends Thread {

	private LogConnector log;

	private Client client;

	private Task task;

	private TaskStatus status;

	private String taskID;

	private String owner;

	private long start;

	private long end;
	
	private long lastPublishTime;

	private MessageHub hub;

	private Map<String, DeviceCapacity> agentCandidates;

	private Map<Test, TestListener> testSlots;
	
	private Map<String, Integer> failCounter;

	private Agent executeAgent;

	private TestStrategy strategy;

	private long publishTimeout = Constants.ONE_MINUTE * 10;

	private long reserveTimeout = Constants.ONE_MINUTE * 30;

	private long taskTimeout = Constants.ONE_HOUR * 8;

	private int maxRetryTimes = 2;

	private Semaphore lock = new Semaphore( 1 );

	private File workspace;

	private OutputStream tracker;

	private ExecutorService pool = Executors.newCachedThreadPool();

	private boolean running = true;
	
	private StatusChangeNotifier notifier;
	
	private TestExecutionChecker checker;
	
	private FileTransferService fts;
	
	public TaskClient( String uri, Task task, TestStrategy strategy, File workspace, OutputStream tracker, FileTransferDescriptor ftDescriptor, boolean activeHeartbeatCheck, BrokerDescriptor ... descs ) {
		this.setName( "TaskClient" );
		status = TaskStatus.INIT;
		this.task = task;
		this.strategy = strategy;
		if ( !workspace.exists() )
			workspace.mkdirs();
		this.workspace = workspace;
		this.tracker = tracker;
		log = LogUtils.get( "TaskClient", tracker );
		start = System.currentTimeMillis();
		checker = new TestExecutionChecker( this, tracker, activeHeartbeatCheck );
		fts = new FileTransferService( hub, ftDescriptor, workspace, tracker );
		try {
			if( descs != null && descs.length > 0 )
				initMessageHub( descs );
			else if( uri != null && !uri.isEmpty() )
				initMessageHub( uri );
			else
				throw new RuntimeException( "Initialize MessageHub met exception. Both URI and BrokerDescriptor are Empty.");
		} catch ( MessageException e ) {
			throw new RuntimeException( "Initialize MessageHub met exception.", e );
		}
		notifier = new StatusChangeNotifier( hub, Constants.MSG_TARGET_CLIENT );
		agentCandidates = new ConcurrentHashMap<String,DeviceCapacity>();
		failCounter = new ConcurrentHashMap<String, Integer>();
		testSlots = new ConcurrentHashMap<Test, TestListener>();
		long current = System.currentTimeMillis();
		task.setStartTime( current );
		for ( Test t : task.getTestsuite().getTests() ) {
			t.addObserver( notifier );
			testSlots.put( t, new FakeTestListener( t ) );
			failCounter.put( t.getId(), 0 );
		}
		this.client = new Client();
		this.client.addObserver(notifier);
		this.client.setTaskId(this.task.getId());
		this.client.setHost(CommonUtils.getHostName());
		this.client.setStatus(Client.Status.INUSE);
		this.task.addObserver(notifier);
		log.info( "Test slots loaded, there are " + testSlots.size() +" test under executing." );
		log.info( "Current Task have " + task.getTestsuite().getTests().size() + " tests to be executed." );
		pool.execute( checker );
	}
	
	private void initMessageHub( String uri ) throws MessageException {
		hub = new MessageHub( uri, Constants.MSG_TARGET_CLIENT );
		hub.bindHandlers( Constants.BROKER_TASK, Topic.class, HUB_TASK_PUBLISH, null, null );
		hub.bindHandlers( Constants.BROKER_TASK, Queue.class, HUB_TASK_COMMUNICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "' AND " + Constants.MSG_HEAD_TASKID + "='"
				+ task.getId() + "'", new TaskClientCommunicator( this, this.lock, tracker ) );
		hub.bindHandlers( Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_CLIENT_NOTIFICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "'", new NotificationHandler( this ) );
		hub.bindHandlers( Constants.BROKER_STATUS, Topic.class, Constants.HUB_TEST_STATUS, null, new MessageListenerAdapter(
				"ClientTestStatusListener", tracker ));
		hub.bindHandlers( Constants.BROKER_STATUS, Topic.class, Constants.HUB_CLIENT_STATUS, null, null );
		hub.bindHandlers( Constants.BROKER_STATUS, Topic.class, Constants.HUB_TASK_STATUS, null, null );
		hub.bindHandlers( Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_AGENT_NOTIFICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "'", checker );
		hub.bindHandlers( Constants.BROKER_FT, Queue.class, Constants.HUB_FILE_TRANSFER, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "' AND " + Constants.MSG_HEAD_TASKID + "='"
				+ task.getId() + "'", fts );
		fts.setHub( hub );
	}

	private void initMessageHub( BrokerDescriptor[] descs ) throws MessageException {
		hub = new MessageHub( Constants.MSG_TARGET_CLIENT, descs );
		hub.bindHandlers( Constants.BROKER_TASK, Topic.class, HUB_TASK_PUBLISH, null, null );
		hub.bindHandlers( Constants.BROKER_TASK, Queue.class, HUB_TASK_COMMUNICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "' AND " + Constants.MSG_HEAD_TASKID + "='"
				+ task.getId() + "'", new TaskClientCommunicator( this, this.lock, tracker ) );
		hub.bindHandlers( Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_CLIENT_NOTIFICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "'", new NotificationHandler( this ) );
		hub.bindHandlers( Constants.BROKER_STATUS, Topic.class, Constants.HUB_TEST_STATUS, null, new MessageListenerAdapter(
				"ClientTestStatusListener", tracker ) );
		hub.bindHandlers( Constants.BROKER_STATUS, Topic.class, Constants.HUB_CLIENT_STATUS, null, null );
		hub.bindHandlers( Constants.BROKER_STATUS, Topic.class, Constants.HUB_TASK_STATUS, null, null );
		hub.bindHandlers( Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_AGENT_NOTIFICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "'", checker );
		hub.bindHandlers( Constants.BROKER_FT, Queue.class, Constants.HUB_FILE_TRANSFER, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "' AND " + Constants.MSG_HEAD_TASKID + "='"
				+ task.getId() + "'", fts );
		fts.setHub( hub );
	}

	public Map<String,DeviceCapacity> getAgentCandidates() {
		return agentCandidates;
	}

	public Map<Test, TestListener> getTestSlots() {
		return testSlots;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public void setStatus( TaskStatus status ) {
		this.status = status;
	}

	public Agent getExecuteAgent() {
		return executeAgent;
	}

	public void setExecuteAgent( Agent executeAgent ) {
		this.executeAgent = executeAgent;
	}

	public int getMaxRetryTimes() {
		return maxRetryTimes;
	}

	public void setMaxRetryTimes( int maxRetryTimes ) {
		this.maxRetryTimes = maxRetryTimes;
	}

	public long getPublishTimeout() {
		return publishTimeout;
	}

	public void setPublishTimeout( long publishTimeout ) {
		this.publishTimeout = publishTimeout;
	}

	public long getReserveTimeout() {
		return reserveTimeout;
	}

	public void setReserveTimeout( long reserveTimeout ) {
		this.reserveTimeout = reserveTimeout;
	}

	public long getTaskTimeout() {
		return taskTimeout;
	}

	public void setTaskTimeout( long taskTimeout ) {
		this.taskTimeout = taskTimeout;
	}

	public MessageHub getHub() {
		return hub;
	}

	public Task getTask() {
		return task;
	}

	public void setTask( Task task ) {
		this.task = task;
	}

	public String getTaskId() {
		return taskID;
	}

	public void setTaskId( String id ) {
		this.taskID = id;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner( String owner ) {
		this.owner = owner;
	}

	public long getStart() {
		return start;
	}

	public void setStart( long start ) {
		this.start = start;
	}

	public long getEnd() {
		return end;
	}

	public void setEnd( long end ) {
		this.end = end;
	}

	public TestStrategy getStrategy() {
		return strategy;
	}

	public File getWorkspace() {
		return workspace;
	}

	public ExecutorService getPool() {
		return pool;
	}

	public StatusChangeNotifier getNotifier() {
		return notifier;
	}
	
	public FileTransferService getFts() {
		return fts;
	}

	@Override
	public void run() {
		while ( running ) {
			try {
				checkPublish();
				checkHangTests();
				timeoutCheck();
				TimeUnit.SECONDS.sleep( 30 );
			} catch ( Exception e ) {
				log.error( "Check task progress met exception.", e );
			}
		}
	}

	private void checkPublish() {
		long timeout = System.currentTimeMillis() - start;
		if( this.getWaitingTestsNumber() == this.testSlots.size() && timeout >= this.publishTimeout && agentCandidates.isEmpty() ) {
			this.cancelTask( "Can't find capable devices during publish timeout interval[" + CommonUtils.convert( timeout ) );
		}
		
		if ( !isAllTestStart() ) {
			log.warn( "Still have " + getWaitingTestsNumber() + "tests to be executed!" );
			for ( Test t : this.testSlots.keySet() ) {
				if ( testSlots.get( t ) instanceof FakeTestListener )
					log.warn( "Test: " + t.getId() + " is waiting." );
			}
			if( ( System.currentTimeMillis() - lastPublishTime ) >= Constants.ONE_SECOND * 30 ) {
				log.info( "Not all the sub tests started, still need to publish task once again!" );
				try {
					this.publishTask( task );
				} catch ( MessageException e ) {
					log.error( "Trying to republishing task failed. task:"+task, e );
				}
			}
		}

	}

	private void timeoutCheck() {
		log.info( "Checking timeout ..." );
		if ( ( System.currentTimeMillis() - start ) > taskTimeout ) {
			log.warn( "Timeout ! Stop the task." );
			//taskFinished();
			cancelTask( "Task timeout reach :" + CommonUtils.convert( taskTimeout ) );
			this.end = System.currentTimeMillis();
		}
	}

	/**
	 * Try to check the test phase and based on the test timeout find if there
	 * are hang tests exist.
	 */
	private void checkHangTests() {
		log.info( "checking hang tests..." );
		for ( Test test : testSlots.keySet() ) {
			TestListener tl = testSlots.get( test );
			if( tl instanceof FakeTestListener )
				continue;
			long interval = System.currentTimeMillis() - tl.getStart();
			if ( interval > test.getTimeout() ) {
				log.warn( "Going to cancel suspect test execution timeout, test=" + test + ", " + "start point:"
						+ CommonUtils.parseTimestamp( tl.getStart() ) + ", current duration : "
						+ CommonUtils.convert( interval ) );
				cancelTest( test, "Execute timeout. Start from " + CommonUtils.parseTimestamp( tl.getStart() )
						+ ", timeout duration:" + CommonUtils.convert( interval ) );
			}
		}

		if ( testSlots.isEmpty() ) {
			log.warn( " all test finished and no hang test. Could end this task client." );
			taskFinished();
		}
	}

	/**
	 * Cancel assigned running test.
	 * 
	 * @param test
	 */
	public void cancelTest( Test test, String reason ) {
		if( reason == null )
			reason = "Unknown";
		log.warn( "Going to cancel test[" + test.getId() + "] for reason:" + reason );
		try {
			TestListener tl = testSlots.get( test );
			if( ! ( tl instanceof FakeTestListener ) ) {
				Message msg = hub.createMessage( Constants.BROKER_TASK );
				setProperty( msg, Constants.MSG_HEAD_TARGET, tl.getHostAgent() );
				setProperty( msg, Constants.MSG_HEAD_TESTID, test.getId() );
				setProperty( msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_CANCEL );
				setProperty( msg, Constants.MSG_HEAD_ERROR, reason );
				hub.getPipe( Constants.HUB_TASK_COMMUNICATION ).send( msg );
				((MessageListenerAdapter)hub.getPipe( Constants.HUB_TEST_STATUS ).getListener()).removeFilter( tl );
			}
			testSlots.remove( test );
		} catch ( Exception ex ) {
			log.error( "Cancel test failed. test=" + test, ex );
		}
	}

	/**
	 * Steps for task client.
	 * <ol>
	 * <li>Allocate test requirements.</li>
	 * <li>Publish Task.</li>
	 * <li>Select candidates.</li>
	 * <li>Send reservation.</li>
	 * <li>Connect to agent node.</li>
	 * <li>Send artifacts.</li>
	 * <li>waiting for task execution.</li>
	 * <li>Monitoring the execution notification.</li>
	 * <li>Receiving results.</li>
	 * <li>Done!</li>
	 * </ol>
	 */
	public void begin() {
		log.info( "Task Client start working for task[" + task.getId() + "]" );
		long startpoint = System.currentTimeMillis();
		int retryTimes = 0;
		// first, publish the task.
		while ( status == TaskStatus.INIT ) {
			try {
				lastPublishTime = System.currentTimeMillis();
				publishTask( task );
				break;
			} catch ( MessageException e ) {
				log.error( "Met Exception while publishing task:" + task + ", try to republish[" + retryTimes
						+ "] it again.", e );
				retryTimes++;
				if ( ( System.currentTimeMillis() - startpoint ) >= publishTimeout || retryTimes >= maxRetryTimes ) {
					log.error( "Can't successfully pushlish the task from " + CommonUtils.parse( startpoint ) + " to " + CommonUtils.parse( System.currentTimeMillis() ) 
						+ " during " + retryTimes + " times retry, Task[" + task.getId() + "]" );
					this.cancelTask( "Publish task reach maxium timeout[" + CommonUtils.convert( publishTimeout ) + "]" );
					break;
				}
			}
			try {
				TimeUnit.SECONDS.sleep( 10 * ( retryTimes + 1 ) );
			} catch ( InterruptedException e1 ) {
				e1.printStackTrace();
			}
		}
		
		this.start();
	}

	/**
	 * Handle new incoming task acceptance. Event handler for candidateBus
	 * Event.
	 */
	public void handleNewAccpetance( ) {
		switch ( strategy ) {
			case EXEC_ASAP:
				if ( remainTest() ) {
					for( String from : this.agentCandidates.keySet() ) {
						DeviceCapacity capacity = this.agentCandidates.get( from );
						log.info( "Agent:[" + from + "] have " + capacity.getAvailable()
								+ " devicesets which can execute the test now." );
						for ( int cnt = 0; cnt < capacity.getAvailable(); cnt++ ) {
							Test t = getNextWaitingTest();
							if( t != null ) {
								t.setPhase( Phase.PENDING );
								TestListener tl = new TestListener( t, from, tracker );
								( ( MessageListenerAdapter ) hub.getPipe( Constants.HUB_TEST_STATUS ).getListener() )
										.addFilter( tl );
								testSlots.put( t, tl );
								try {
									sendReservation( from, t );
								} catch ( MessageException e ) {
									log.error( "Send Reservation failed. Will Try again.", e );
									try {
										TimeUnit.SECONDS.sleep( 5 );
										sendReservation( from, t );
									}catch( Exception ex ) {
										cancelTask( "Can't send reservation to Agents for reason: " + e.getMessage() );
									}
								}
							}
						}
						agentCandidates.remove( from );
					}
				}
				break;
			case SPLIT_AMAP:
				break;
			case MODERATE:
				break;
		}
	}

	/**
	 * Get next test in waiting state.
	 * 
	 * @return
	 */
	public Test getNextWaitingTest() {
		for ( Test t : this.testSlots.keySet() ) {
			if ( testSlots.get( t ) instanceof FakeTestListener )
				return t;
		}
		return null;
	}
	
	/**
	 * Get next test in waiting state.
	 * 
	 * @return
	 */
	public int getWaitingTestsNumber() {
		int cnt = 0;
		for ( Test t : this.testSlots.keySet() ) {
			if ( testSlots.get( t ) instanceof FakeTestListener )
				cnt ++;
		}
		return cnt;
	}

	/**
	 * Check if all the tests are under execution.
	 * 
	 * @return
	 */
	protected boolean isAllTestStart() {
		boolean ret = true;
		for ( Test t : this.testSlots.keySet() ) {
			if( testSlots.get( t ) instanceof FakeTestListener ) {
			//if ( t.getPhase() != Phase.UNKNOWN && t.getPhase() != Phase.PENDING ) {
				ret = false;
				break;
			} else {
				continue;
			}
		}
		return ret;
	}

	/**
	 * Check if there is still test in waiting state.
	 * 
	 * @return
	 */
	protected boolean remainTest() {
		for( TestListener tl : testSlots.values() ) {
			if( tl instanceof FakeTestListener )
				return true;
		}
		return false;
	}

	/**
	 * Client publish task to agent nodes.
	 * 
	 * @param task
	 * @throws JMSException
	 */
	public void publishTask( Task task ) throws MessageException {
		try {
			Pipe pipe = hub.getPipe( Constants.HUB_TASK_PUBLISH );
			Message message = hub.createMessage( Constants.BROKER_TASK );
			setProperty( message, Constants.MSG_HEAD_TASK, task.toString() );
			setProperty( message, Constants.MSG_HEAD_FROM, Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() );
			pipe.getProducer().send( message );
			status = TaskStatus.PUBLISHED;
			task.setPhase(Task.Phase.PUBLISHED);
		} catch ( JMSException e ) {
			throw new MessageException( e );
		}
		log.info( "Task have been published! device requirement is:" + task.getRequirements() );
	}

	/**
	 * Client send reservation to assigned agent node, ask agent to reserve the
	 * specific devices for task.
	 * 
	 * @param target
	 * @param test
	 * @throws JMSException
	 */
	public void sendReservation( String target, Test test ) throws MessageException {
		Pipe taskPipe = hub.getPipe( Constants.HUB_TASK_COMMUNICATION );
		Message msg;
		try {
			msg = hub.createMessage( Constants.BROKER_TASK );
			setProperty( msg, Constants.MSG_HEAD_TASK, task.toString() );
			setProperty( msg, Constants.MSG_HEAD_TEST, test.toString() );
			setProperty( msg, Constants.MSG_HEAD_FROM, Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() );
			setProperty( msg, Constants.MSG_HEAD_TARGET, target );
			setProperty( msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_RESERVE );
			taskPipe.getProducer().send( msg );
		} catch ( JMSException e ) {
			throw new MessageException( e );
		}
		log.info( "Sent reservation message to " + target + " for test:" + test.getId() );
	}

	/**
	 * pick up test object from test slots by testId.
	 * 
	 * @param testId
	 * @return
	 */
	public Test getTestById( String testId ) {
		for ( Test t : this.testSlots.keySet() ) {
			if ( t.getId().equals( testId ) )
				return t;
		}
		return null;
	}

	/**
	 * Clean up all the resources of current Task. Should be called in final
	 * stage.
	 */
	public void cleanUp() {
		try {
			this.checker.stop();
		}catch( Exception ex ) {
			log.error( "Stop Test execution Checker met exception.", ex );
		}
		hub.dispose();
		pool.shutdown();
		log.info( "Task Client resource clean up finished." );
		log.getTags().clear();
		fts.dispose();
		agentCandidates.clear();
	}

	/**
	 * Called when some tests failed during execution.
	 * 
	 * @param testId
	 * @param reason
	 */
	public void testFail( String testId, String reason ) {
		log.error( "Test[" + testId +"] Failed because: "+reason );
		Test test = this.getTestById( testId );
		if( test != null ) {
			int count = failCounter.get( testId );
			if( count >= this.maxRetryTimes ) {
				failCounter.remove( testId );
				test.setPhase( Phase.FAILED );
				cancelTest( test, "Failed over max retry times["+maxRetryTimes+"]." );
				TestListener tl = testSlots.remove( test );
				if( tl != null ) {
					LogUtils.dispose( tl.getLog() );
					((MessageListenerAdapter)hub.getPipe( Constants.HUB_TEST_STATUS ).getListener()).removeFilter( tl );
				}else{
					log.warn( "Test[" + testId + "] can't find relative TestListener in current test slots in final retry round." );
				}
			} else {
				TestListener tl = testSlots.remove( test );
				if( tl != null ) {
					LogUtils.dispose( tl.getLog() );
					((MessageListenerAdapter)hub.getPipe( Constants.HUB_TEST_STATUS ).getListener()).removeFilter( tl );
					testSlots.put( test, new FakeTestListener(test) );
				}else{
					log.warn( "Test[" + testId + "] can't find relative TestListener in current test slots." );
				}
				failCounter.put( testId, (count + 1) );
			}
		}

	}

	/**
	 * Called when some of tests successfully finished.
	 * 
	 * @param testId
	 */
	public void testFinished( Test test ) {
		TestListener tl = this.testSlots.remove( test );
		test.setPhase( Phase.FINISHED );
		((MessageListenerAdapter)hub.getPipe( Constants.HUB_TEST_STATUS ).getListener()).removeFilter( tl );
		if ( testSlots.isEmpty() ) {
			log.warn( "All tests have been finished." );
			taskFinished();
		}
		log.info( "Test[" + test.getId() + "] execution finished." );
	}

	private synchronized void taskFinished() {
		running = false;
		this.getTask().setPhase( Task.Phase.FINISHED );
		client.setStatus( Client.Status.IDLE );
		log.info( "Current Task finally over. Good luck!" );
		this.cleanUp();
	}

	/**
	 * Provided for outside checker.
	 * 
	 * @return if task have been finished.
	 */
	public boolean isFinished() {
		return running;
	}
	
	/**
	 * Cancel whole Task, including all the belongs tests.
	 */
	public void cancelTask( String reason ) {
		log.warn( "Task is going to be cancelled. Reason:" + reason );
		for(Test test : testSlots.keySet()) {
			cancelTest( test, reason );
			try {
				TimeUnit.SECONDS.sleep( 5 );
			} catch ( InterruptedException e ) {
				log.error( "Interrupted when wait for cancel message been sent." );
			}
		}
		taskFinished();
	}

}