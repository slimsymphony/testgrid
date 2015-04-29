package frank.incubator.testgrid.common.message;

import frank.incubator.testgrid.common.model.BaseObject;

/**
 * @author Wang Frank
 *
 */
public class BrokerDescriptor extends BaseObject {
	private String uri;
	private MqBuilder mq;

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public MqBuilder getMq() {
		return mq;
	}

	public void setMq(MqBuilder mqType) {
		this.mq = mqType;
	}
}
