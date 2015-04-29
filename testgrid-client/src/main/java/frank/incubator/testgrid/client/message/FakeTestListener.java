package frank.incubator.testgrid.client.message;

import javax.jms.Message;

import frank.incubator.testgrid.common.model.Test;

/**
 * As the name, it's just a fake test listener and do nothing.
 * 
 * @author Wang Frank
 *
 */
public class FakeTestListener extends TestListener {

	private boolean serializeWaiting = false;
	
	public FakeTestListener(Test test) {
		super(test, null, null);
	}
	
	public FakeTestListener(Test test, boolean serializeWaiting) {
		super(test, null, null);
		this.serializeWaiting = serializeWaiting;
	}
	
	public boolean isSerializeWaiting() {
		return this.serializeWaiting;
	}

	public void setSerializeWaiting(boolean sw) {
		this.serializeWaiting = sw;
	}
	
	@Override
	public void handle(Message message) {

	}
}
