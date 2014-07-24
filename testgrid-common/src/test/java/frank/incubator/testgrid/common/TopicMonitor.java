package frank.incubator.testgrid.common;

import javax.jms.Message;
import javax.jms.TextMessage;
import javax.jms.Topic;

import com.google.gson.reflect.TypeToken;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.BrokerDescriptor;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.message.MqBuilder;
import frank.incubator.testgrid.common.model.Test;

/**
 * @author Wang Frank
 *
 */
public class TopicMonitor {
	
	static class StatusListener extends MessageListenerAdapter {
		private LogConnector log;
		public StatusListener( String testId, String broker ) {
			this.testId = testId;
			log = LogUtils.get( "listener", System.out );//System.out //LogConnector.no
			log.addTags( broker );
		}
		
		String testId;
		
		@Override
		protected void handleMessage( Message m ) {
			MessageHub.printMessage( m, log );
			/*try {
				String testId = MessageHub.getProperty( m, Constants.MSG_HEAD_TESTID, "Unknown" );
				if( testId.equals( "Unknown" ) ) {
					testId = MessageHub.getProperty( m, Constants.MSG_HEAD_TEST_INFO, "Unknown" );
					if( !testId.equals( "Unknown" ) ) {
						Test t = CommonUtils.fromJson( testId, Test.class );
						if( t != null )
							testId = t.getId();
					}
				}
				log.info("===========" + testId +"===========");
				if( this.testId != null && ( testId.equals( "Unknown" ) || testId.equals( this.testId ) ) ) {
					MessageHub.printMessage( m, log );
					if( m instanceof TextMessage ) {
						log.info( ((TextMessage)m).getText() );
					}
				}
			} catch ( Exception e ) {
				log.error( e.getMessage(), e );
			}*/
		}
	}
	
	/**
	 * @param args
	 * @throws MessageException 
	 */
	public static void main( String[] args ) throws MessageException {
		BrokerDescriptor bd = new BrokerDescriptor();
		bd.setId( "testenv" );
		bd.setMq( MqBuilder.ActiveMQ );
		bd.setUri( "tcp://10.220.120.10:61616" );
		BrokerDescriptor bd2 = new BrokerDescriptor();
		bd2.setId( "officialenv" );
		bd2.setMq( MqBuilder.ActiveMQ );
		bd2.setUri( "failover:(tcp://10.220.116.240:61616,tcp://10.220.116.242:61616)" );
		//MessageHub hub = new MessageHub( "tcp://10.220.120.10:61616", "Monitor" );//,{'id':'BROKER_STATUS','uri':'udp://localhost:45732','mq':'ActiveMQ'},{'id':'BROKER_FT','uri':'nio://localhost:61616','mq':'ActiveMQ'},{'id':'BROKER_NOTIFICATION','uri':'udp://localhost:45732','mq':'ActiveMQ'}
		BrokerDescriptor[] bd3 = CommonUtils.fromJson( "[{'id':'BROKER_TASK','uri':'nio://localhost:61616','mq':'ActiveMQ'}]", new TypeToken<BrokerDescriptor[]>(){}.getType( ));
		MessageHub hub = new MessageHub( "Monitor", bd3 );
		//hub.bindHandlers( "testenv", Topic.class, Constants.HUB_TEST_STATUS, null, new StatusListener("Test_icase-38755", "testenv" ) );
		//hub.bindHandlers( "officialenv", Topic.class, Constants.HUB_TEST_STATUS, null, new StatusListener( null, "officialenv" ) );
		hub.bindHandlers( "BROKER_TASK", Topic.class, Constants.HUB_TASK_PUBLISH, null, new StatusListener( null, "BROKER_TASK" ) );
		while(true) {
			try {
				Thread.sleep( 10000 );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
			}
		}
	}

}
