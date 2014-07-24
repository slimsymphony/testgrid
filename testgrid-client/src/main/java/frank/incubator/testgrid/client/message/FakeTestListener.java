package frank.incubator.testgrid.client.message;

import javax.jms.Message;

import frank.incubator.testgrid.common.model.Test;

/**
 * As the name, it's just a fake test listener and do nothing.
 * @author Wang Frank
 *
 */
public class FakeTestListener extends TestListener {

	public FakeTestListener( Test test ) {
		super( test, null, null );
	}
	
	@Override
	public void handle( Message message ) {
		
	}
}
