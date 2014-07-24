package frank.incubator.testgrid.common;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.OutputStream;
import java.util.Enumeration;

import javax.jms.Message;
import javax.jms.Queue;

import com.google.gson.reflect.TypeToken;
import frank.incubator.testgrid.common.message.BrokerDescriptor;
import frank.incubator.testgrid.common.message.MessageBroker;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.message.Pipe;

/**
 * @author Wang Frank
 *
 */
public class JmsTest {

	static class SimpleMessageListener extends MessageListenerAdapter {

		public SimpleMessageListener( int id,MessageBroker broker ) {
			super();
			this.id = id;
			this.broker = broker;
		}
		private int id;
		private MessageBroker broker;
		@Override
		public void onMessage( Message message ) {
			super.onMessage( message );
			try {
				System.out.println(">>>This is listener[" + id +"] receiving....");
				printMessage( message );
				Pipe pp = null;
				if( broker.getId().equals( "localTcp" ))
					pp = broker.getPipe( "testqueue" );
				else
					pp = broker.getPipe( "testqueue2" );
				Message msg = pp.createMessage();
				msg.setJMSReplyTo( pp.getDest() );
				msg.setJMSCorrelationID( message.getJMSMessageID() );
				setProperty( msg, "response", "yes" );
				printMessage( msg );
				pp.send( msg );
			} catch ( Exception e ) {
				e.printStackTrace();
			}
		}
	}
	
	private static void printMessage( Message message ) {
		try {
			System.out.println( "Start==========================" );
			System.out.println("JMSMessageId:" + message.getJMSMessageID() );
			System.out.println("JMSCorrelationID:" + message.getJMSCorrelationID() );
			System.out.println("JMSExpiration:" + message.getJMSExpiration());
			System.out.println("JMSReplyTo:" + message.getJMSReplyTo() );
			System.out.println("JMSTimestamp:" + message.getJMSTimestamp());
			System.out.println("JMSType:" + message.getJMSType() );
			System.out.println("JMSRedelivered:" + message.getJMSRedelivered());
			System.out.println("JMSDeliveryMode:" + message.getJMSDeliveryMode());
			System.out.println("JMSDestination:" + message.getJMSDestination());
			System.out.println("JMSPriority:" + message.getJMSPriority());
			
			@SuppressWarnings( "unchecked" )
			Enumeration<String> eu = message.getPropertyNames();
			while( eu.hasMoreElements() ) {
				String name = eu.nextElement();
				System.out.println( name + " = " + getProperty( message, name, Object.class ) );
			}
			System.out.println( "End==========================" );
		}catch( Exception ex ) {
			ex.printStackTrace();
		}
	}
	
	/**
	 * @param args
	 */
	public static void main( String[] args ) throws Exception {
		String json = "[{'id':'localTcp','uri':'tcp://localhost:61616','mq':'ActiveMQ'},{'id':'localUdp','uri':'udp://localhost:45732','mq':'ActiveMQ'}]";
		BrokerDescriptor[] bds = CommonUtils.fromJson( json, new TypeToken<BrokerDescriptor[]>() {}.getType() );
		MessageHub mh = new MessageHub( "Test", bds );
		Pipe pt1 = mh.bindHandlers( "localTcp", Queue.class, "testqueue", null, new SimpleMessageListener(1,mh.getBroker( "localTcp" )), (OutputStream) null );
		Pipe pt2 = mh.bindHandlers( "localTcp", Queue.class, "testqueue", null, new SimpleMessageListener(2,mh.getBroker( "localTcp" )), (OutputStream) null);
		Pipe pu1 = mh.bindHandlers( "localUdp", Queue.class, "testqueue2", "receiver='bbb'", new SimpleMessageListener(3,mh.getBroker( "localUdp" )), (OutputStream) null );
		Pipe pu2 = mh.bindHandlers( "localUdp", Queue.class, "testqueue2", "receiver='aaa'", new SimpleMessageListener(4,mh.getBroker( "localUdp" )), (OutputStream) null);
		Message msg = pt1.createMessage();
		setProperty( msg, "receiver", "aaa" );
		msg.setJMSReplyTo( pt1.getDest() );
		msg.setJMSCorrelationID( "123" );
		pt1.send( msg );
	}

}
