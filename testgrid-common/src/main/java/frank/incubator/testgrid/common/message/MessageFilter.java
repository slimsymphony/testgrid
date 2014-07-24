package frank.incubator.testgrid.common.message;

import javax.jms.Message;

/**
 * Interface for filtering all the incoming message.
 * 
 * @author Wang Frank
 *
 */
public interface MessageFilter {

	/**
	 * Actual method to handle the incoming message.
	 * 
	 * @param message
	 */
	public abstract void handle( Message message );

	/**
	 * Subclass should implements this method to create condition to filter out which messages were needed for itself.
	 * 
	 * @param message
	 * @throws MessageException
	 */
	public abstract void filter( Message message ) throws MessageException;
	
	/**
	 * Dispose filter resource.
	 */
	public abstract void dispose();

}