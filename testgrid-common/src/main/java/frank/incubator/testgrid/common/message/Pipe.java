package frank.incubator.testgrid.common.message;

import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.util.HashMap;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;

/**
 * Pipe of Hub, store the information of one test destination and related consumer, producer and listener.
 * 
 * @author Wang Frank
 *
 */
public class Pipe {
	
	public Pipe( String name ) {
		this.name = name;
	}
	private String name;
	private Destination dest;
	private MessageConsumer consumer;
	private MessageListener listener;
	private MessageProducer producer;
	private MessageBroker parentBroker;
	boolean transactional;
	private String hostType = "";
	private String messageSelector;
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getHostType() {
		return hostType;
	}

	/**
	 * Create a simple Message instance without any predefined properties.
	 * 
	 * @return
	 * @throws MessageException
	 */
	public Message createMessage() throws MessageException {
		try {
			Message msg = this.parentBroker.getSession().createMessage();
			if( hostType != null && !hostType.isEmpty() ) {
				setProperty( msg, Constants.MSG_HEAD_FROM, hostType+CommonUtils.getHostName() );
			}
			return msg;
		} catch ( JMSException e ) {
			parentBroker.onException( e );
			throw new MessageException( "Create Messge met exception.", e );
		}
	}
	
	/**
	 * Create a simple Message instance with Constants.MSG_HEAD_FROM String properties.
	 * 
	 * @param hostType  Constants.MSG_TARGET_CLIENT or Constants.MSG_TARGET_AGENT
	 * @return
	 * @throws MessageException
	 */
	public Message createMessage( String hostType ) throws MessageException {
		try {
			Message msg = parentBroker.getSession().createMessage();
			setProperty( msg, Constants.MSG_HEAD_FROM, hostType+CommonUtils.getHostName() );
			return msg;
		} catch ( JMSException e ) {
			parentBroker.onException( e );
			throw new MessageException( "Create Messge met exception.", e );
		}
	}
	
	public Destination getDest() {
		return dest;
	}

	public void setDest( Destination dest ) {
		this.dest = dest;
	}

	public MessageConsumer getConsumer() {
		return consumer;
	}

	public void setConsumer( MessageConsumer consumer ) {
		this.consumer = consumer;
	}

	public MessageListener getListener() {
		return listener;
	}

	public void setListener( MessageListener listener ) {
		this.listener = listener;
	}

	public MessageProducer getProducer() {
		return producer;
	}

	public void setProducer( MessageProducer producer ) {
		this.producer = producer;
	}

	public boolean isTransactional() {
		return transactional;
	}

	public void setTransactional( boolean transactional ) {
		this.transactional = transactional;
	}

	public MessageBroker getParentBroker() {
		return parentBroker;
	}

	public void setParentBroker( MessageBroker parentBroker ) {
		this.parentBroker = parentBroker;
	}
	
	public void setHostType( String hostType ) {
		this.hostType = hostType;
	}

	public String getMessageSelector() {
		return messageSelector;
	}

	public void setMessageSelector(String messageSelector) {
		this.messageSelector = messageSelector;
	}

	public void send( Message message ) throws MessageException {
		if( message == null )
			throw new NullPointerException( "Message tobe send is NUll." );
		try {
			this.producer.send( message );
		} catch ( JMSException e ) {
			parentBroker.onException( e );
			throw new MessageException( "Send message via pipe["+this.dest+"] failed. Message:" + message, e );
		}
	}
	
	/**
	 * Send message to assigned pipe. If content is null, then a flat Message obj will be sent.
	 * 
	 * @param content
	 * @param properties
	 * @throws MessageException
	 */
	public void send( String content, HashMap<String, Object> properties ) throws MessageException {
		send( content, properties, "" );
	}
	
	/**
	 * Send message to assigned pipe. If content is null, then a flat Message obj will be sent.
	 * 
	 * @param content
	 * @param properties
	 * @param hostType
	 * @throws MessageException
	 */
	public void send( String content, HashMap<String, Object> properties, String hostType ) throws MessageException {
		Message msg = null;
		try{
			if( content != null ) {
				msg = parentBroker.getSession().createTextMessage();
				( (TextMessage) msg ).setText( content );
				setProperty( msg, Constants.MSG_HEAD_FROM, hostType + CommonUtils.getHostName() );
			} else {
				msg = createMessage( hostType );
			}
			if( properties != null )
				for( String key : properties.keySet() )
					setProperty( msg, key, properties.get( key ) );
			send( msg );
		}catch( Exception ex ) {
			throw new MessageException( "Send message fail,content=" + content + ",properties=" + CommonUtils.toJson(properties), ex );
		}
	}
}
