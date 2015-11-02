package frank.incubator.testgrid.agent.test;

import static frank.incubator.testgrid.common.Constants.HUB_AGENT_STATUS;

import javax.jms.Topic;

import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageHub;

public class MessageTest {

	public static void main(String[] args) throws MessageException {
		MessageHub mh = new MessageHub("tcp://ali-80938n.hz.ali.com:61616");
		MessageHub mh2 = new MessageHub("tcp://ali-80938n.hz.ali.com:61616");
		mh.bindHandlers(Constants.BROKER_STATUS, Topic.class, HUB_AGENT_STATUS, null, null);
	}

}
