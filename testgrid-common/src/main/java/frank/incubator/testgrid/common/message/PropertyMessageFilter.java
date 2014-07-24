package frank.incubator.testgrid.common.message;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Message;

/**
 * Filter class for filter assigned key/value property.
 * 
 * @author Wang Frank
 *
 */
public abstract class PropertyMessageFilter implements MessageFilter {
	
	public PropertyMessageFilter( Map<String,Object> criteria ) {
		if( criteria == null || criteria.isEmpty() )
			throw new NullPointerException( "Criteria cannot be empty for PropertyMessageFilter." );
		this.criteria = criteria;
	}
	
	public PropertyMessageFilter( String key, Object val ) {
		this.criteria.put( key, val ); 
	}
	
	private Map<String,Object> criteria = new HashMap<String,Object>();
	
	/* (non-Javadoc)
	 * @see frank.incubator.testgrid.common.MessageFilter#handle(javax.jms.Message)
	 */
	@Override
	public abstract void handle( Message message );

	/* (non-Javadoc)
	 * @see frank.incubator.testgrid.common.MessageFilter#filter(javax.jms.Message)
	 */
	@Override
	public void filter( Message message ) throws MessageException {
		boolean accept = true;
		for( String key : criteria.keySet() ) {
			Object v = criteria.get( key );
			Object t = MessageHub.getProperty( message, key, Object.class );
			if ( t == null || !v.equals( t ) ) {
				accept = false;
				break;
			}
		}
		if( accept )
			handle( message );
	}
	
	/*
	 * (non-Javadoc)
	 * @see frank.incubator.testgrid.common.message.MessageFilter#dispose()
	 */
	@Override
	public void dispose() {
		if( criteria != null )
			criteria.clear();
	}
}
