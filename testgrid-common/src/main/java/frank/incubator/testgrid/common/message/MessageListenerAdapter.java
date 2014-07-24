package frank.incubator.testgrid.common.message;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * A common MessageListener Adapter. With log connector and basic
 * implementation. Provided a secondary filter-chain to filter incoming message.
 * 
 * @author Wang Frank
 * 
 */
public class MessageListenerAdapter implements MessageListener {

	protected LogConnector log;
	private List<MessageFilter> filters = new ArrayList<MessageFilter>();
	private Session session;
	private MessageListener delegate;

	public MessageListenerAdapter() {
		this( null, null, null, null, (MessageFilter)null );
	}
	
	public MessageListenerAdapter( String loggerName ) {
		this( loggerName, null, null, null, (MessageFilter)null );
	}
	
	public MessageListenerAdapter( OutputStream tracker ) {
		this( null, null, tracker, null, (MessageFilter)null );
	}
	
	public MessageListenerAdapter( String loggerName, OutputStream tracker ) {
		this( loggerName, null, tracker,null, (MessageFilter)null );
	}
	
	public MessageListenerAdapter( MessageListener delegate, OutputStream tracker ) {
		this( null, null, tracker, delegate, (MessageFilter)null );
	}
	
	public MessageListenerAdapter( String loggerName, MessageListener delegate, OutputStream tracker ) {
		this( loggerName, null, tracker, delegate, (MessageFilter)null );
	}
	
	public MessageListenerAdapter( Session session, OutputStream tracker ) {
		this( null, session, tracker, null, (MessageFilter)null );
	}
	
	public MessageListenerAdapter( String loggerName, Session session, OutputStream tracker ) {
		this( loggerName, session, tracker, null, (MessageFilter)null );
	}

	public MessageListenerAdapter( String loggerName, Session session, OutputStream tracker, MessageListener delegate, MessageFilter... filters) {
		this.session = session;
		this.delegate = delegate;
		if( loggerName != null )
			log = LogUtils.get( loggerName, tracker );
		else
			log = LogUtils.get( this.getClass().getName(), tracker );
		if ( filters != null )
			for ( MessageFilter f : filters )
				if( f != null )
					this.addFilter( f );
	}

	public void addFilter( MessageFilter filter ) {
		if( filter != null)
			this.filters.add( filter );
	}

	public void removeFilter( MessageFilter filter ) {
		if( filter != null)
			this.filters.remove( filter );
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
	 */
	@Override
	public void onMessage( Message message ) {
		try {
			for ( MessageFilter filter : filters ) {
				try {
					filter.filter( message );
				} catch ( MessageException e ) {
					log.error( "Filter message met exception. filter=" + filter.getClass().getName() + " ,Message="
							+ message, e );
				}
			}
			
			if( delegate !=null )
				delegate.onMessage( message );
			else
				handleMessage( message );
			if( session != null )
				session.commit();
		} catch ( Exception ex ) {
			log.error( "Message handle got exception. message=" + message, ex );
			try {
				if(session != null)
					session.rollback();
			} catch ( JMSException e ) {
			}
		}
	}

	protected void handleMessage( Message message ) {
		log.debug( "received message " + MessageHub.printMessage( message ) + " by handler:" + this.getClass().getName() );
	}

	public void dispose() {
		if ( filters != null ) {
			for( MessageFilter mf : filters) {
				mf.dispose();
			}
			filters.clear();
		}
		LogUtils.dispose( log );
		CommonUtils.closeQuietly( session );
	}

}
