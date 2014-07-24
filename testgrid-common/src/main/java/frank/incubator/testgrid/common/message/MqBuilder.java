package frank.incubator.testgrid.common.message;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Build MessageConnectionFactory. And also recording all the differences of diverse MessageQueue providers.
 * Provide instance of connectionFactory.
 * @author Wang Frank
 *
 */
public enum MqBuilder {
	
	ActiveMQ;
	
	public static MqBuilder parse( String name ) {
		for( MqBuilder f : MqBuilder.values() ) {
			if( f.name().equals( name ) )
				return f;
		}
		return null;
	}
	
	public static ConnectionFactory getFactoryInstance( MqBuilder mf, String uri ) {
		if( mf == null )
			return null;
		switch( mf ) {
			case ActiveMQ:
				return new ActiveMQConnectionFactory( uri );
			default:
				return null;
		}
	}
	
	public static ConnectionFactory getFactoryInstance( String name, String uri ) {
		return getFactoryInstance( parse( name ), uri );
	}
}
