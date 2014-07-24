package frank.incubator.testgrid.agent.message;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import javax.jms.Message;
import javax.jms.MessageListener;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import frank.incubator.testgrid.agent.AgentNode;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.DeviceCapacity;
import frank.incubator.testgrid.common.model.Task;

/**
 * A Notifier for keep checking if current could fulfill the waiting tasks. If
 * yes, send message to client (task publisher) to notify. It also keep
 * listening the Task Finish status Message, once some of them were in current
 * waiting list, remove them.
 * 
 * @author Wang Frank
 * 
 */
public class WaitTaskNotifier extends Thread implements MessageListener {

	public static class WaitTask {
		public WaitTask( Task t, String f, long c ) {
			task = t;
			from = f;
			receiveTime = c;
		}

		Task task;
		String from;
		long receiveTime;

		@Override
		public boolean equals( Object o ) {
			if ( o == null || !o.getClass().equals( this.getClass() ) )
				return false;
			WaitTask w = ( WaitTask ) o;
			if ( this.task.equals( w.task ) && this.from.equals( w.from ) )
				return true;
			return false;
		}
	}

	private long monitorTimeout = Constants.ONE_MINUTE * 15;
	private EventBus eventBus;
	private EventBus deviceBusyEventBus;
	private Collection<WaitTask> waitlist = new ConcurrentLinkedQueue<WaitTask>();
	private AgentNode agent;
	private boolean running = true;
	private LogConnector log;

	public long getMonitorTimeout() {
		return monitorTimeout;
	}

	public void setMonitorTimeout( long monitorTimeout ) {
		this.monitorTimeout = monitorTimeout;
	}

	public Collection<WaitTask> getWaitlist() {
		return waitlist;
	}

	public void setWaitlist( Collection<WaitTask> waitlist ) {
		this.waitlist = waitlist;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning( boolean running ) {
		this.running = running;
	}

	public void setEventBus( EventBus eventBus ) {
		this.eventBus = eventBus;
	}

	public WaitTaskNotifier( AgentNode agent, EventBus eventBus, EventBus deviceBusyEventBus ) {
		this.setName( "WaitTaskNotifier" );
		this.agent = agent;
		this.eventBus = eventBus;
		this.deviceBusyEventBus = deviceBusyEventBus;
		log = LogUtils.get( "WaitTaskNotifier" );
		this.deviceBusyEventBus.register( this );
	}

	/**
	 * Subscribe to EventBus( deviceBusyEventBus ), when any published task have
	 * busy devices, add to this notifier.
	 * 
	 * @param args
	 *            Object Array, first element represent Task object, second
	 *            element represent host which sent the request.
	 */
	@Subscribe
	public void addWaitTask( Object[] args ) {
		Task task = ( Task ) args[0];
		String from = ( String ) args[1];
		if ( task != null && from != null ) {
			long t = System.currentTimeMillis();
			waitlist.add( new WaitTask( task, from, t ) );
			log.info( "New Waiting task from " + from + " have been added to waiting list: " + task + " at "
					+ CommonUtils.getTime() );
		}
	}

	@Override
	public void run() {
		Iterator<WaitTask> it = null;
		while ( running ) {
			try {
				TimeUnit.MINUTES.sleep( 1 );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
			}

			it = waitlist.iterator();
			while ( it.hasNext() ) {
				WaitTask t = it.next();
				if ( ( System.currentTimeMillis() - t.receiveTime ) > this.monitorTimeout ) {
					log.info( "Task [" + t.task + "] from " + t.from + " begin at "
							+ CommonUtils.convert( t.receiveTime ) + " got expriration." );
					it.remove();
					continue;
				}
				log.debug( "Start to recheck task " );
				DeviceCapacity capacity = agent.checkCondition( t.task.getRequirements() );
				if ( capacity.getAvailable() > 0 ) {
					eventBus.post( new Object[] { t.task, t.from, capacity } );
					log.info( "Task:[" + t.task + "] from " + t.from + " could be executed now, notification sent." );
				} else {
					log.debug( "Task:[" + t.task + "] from " + t.from + " still couldn't be executed now." );
				}
			}
		}
	}

	/**
	 * Handle incoming task status change notifications.
	 * 
	 * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
	 */
	@Override
	public void onMessage( Message message ) {
		log.debug( "WaitTaskNotifier Received a Task messge:" + CommonUtils.toJson( message ) );
		try {
			Task task = CommonUtils.fromJson( getProperty( message, Constants.MSG_HEAD_TASK, "" ), Task.class );
			task.deleteObservers();
			String from = getProperty( message, Constants.MSG_HEAD_FROM, "Unknown" );
			WaitTask w = new WaitTask( task, from, System.currentTimeMillis() );
			if ( this.waitlist.contains( w ) ) {
				int taskState = getProperty( message, Constants.MSG_HEAD_TASKSTATE, 0 );
				switch ( taskState ) {
					case Constants.TASK_STATUS_FINISHED:
					case Constants.TASK_STATUS_FAILED:
					case Constants.TASK_STATUS_STARTED:
						waitlist.remove( w );
						log.info( "Task:[" + task + "] from " + from
								+ " have already been exeucted, no need to monitor anymore." );
						break;
				}
			}
		} catch ( Exception ex ) {
			log.error( "Handle received Task status update met exception.message: " + CommonUtils.toJson( message ), ex );
		}
	}
}
