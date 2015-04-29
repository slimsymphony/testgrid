package frank.incubator.testgrid.agent.message;

import static frank.incubator.testgrid.common.Constants.HUB_TASK_COMMUNICATION;
import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.util.concurrent.TimeUnit;

import javax.jms.Message;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import frank.incubator.testgrid.agent.AgentNode;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.message.Pipe;
import frank.incubator.testgrid.common.model.DeviceCapacity;
import frank.incubator.testgrid.common.model.Test;

/**
 * Task publish listener. Response to "HUB_TASK_PUBLISH" topic.
 * 
 * @author Wang Frank
 * 
 */
public class TaskSubscriber extends MessageListenerAdapter {

	private AgentNode agent;

	private EventBus deviceBusyEventBus;

	private Pipe pipe;

	public TaskSubscriber(AgentNode agent, EventBus eventBus, EventBus deviceBusyEventBus) {
		super("AgentTaskSubscriber");
		this.agent = agent;
		eventBus.register(this);
		this.deviceBusyEventBus = deviceBusyEventBus;
		pipe = agent.getHub().getPipe(HUB_TASK_COMMUNICATION);
	}

	@Subscribe
	public void sendAccept(Object[] args) {
		Test test = (Test) args[0];
		String from = (String) args[1];
		DeviceCapacity capacity = (DeviceCapacity) args[2];
		try {
			sendResponse(test, from, capacity, false);
		} catch (MessageException e) {
			log.error("Send accpet message to " + from + " for test:" + test + " failed", e);
		}
	}

	public void sendResponse(Test test, String clientHost, DeviceCapacity capacity, boolean withDelay)
			throws MessageException {
		log.info("Test:" + test + " from " + clientHost
				+ " could be executed here, and some of them may need wait for device free.");
		try {
			Message reply = pipe.createMessage(Constants.MSG_TARGET_AGENT);
			setProperty(reply, Constants.MSG_HEAD_TARGET, clientHost);
			setProperty(reply, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_ACCEPT);
			setProperty(reply, Constants.MSG_HEAD_TASKID, test.getTaskID());
			setProperty(reply, Constants.MSG_HEAD_TESTID, test.getId());
			setProperty(reply, Constants.MSG_HEAD_RESPONSE, capacity.toString());
			if (withDelay)
				try {
					TimeUnit.SECONDS.sleep(agent.getAgentInfo(AgentNode.TASK_INCLUDE).getRunningTasks().size());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			pipe.getProducer().send(reply);
		} catch (Exception ex) {
			throw new MessageException("Send Response failed.", ex);
		}
	}

	@Override
	public void onMessage(Message message) {
		try {
			super.onMessage(message);
			log.info("Got task requirement.");
			String testStr = getProperty(message, Constants.MSG_HEAD_TEST, "");
			if (testStr != null) {
				//Task task = CommonUtils.fromJson(taskStr, Task.class);
				Test test = CommonUtils.fromJson(testStr, Test.class);
				test.deleteObservers();
				//int requireDeviceSets = task.getTestsuite().getTests().size();
				String clientHost = getProperty(message, Constants.MSG_HEAD_FROM, "Unknown");
				log.info("Task:" + test.getTaskID() + ", Test:" + test.getId()+ " from:" + clientHost + ", requirement:" + test.getRequirements());
				DeviceCapacity capability = agent.checkCondition(test.getId(), test.getRequirements());
				if (capability.getAvailable() > 0 || capability.getNeedWait() > 0) {
					sendResponse(test, clientHost, capability, true);
					/*
					// if require devices more than current node can provided
					// and still have busy device could be provided to task,
					// monitor these devices and when they free, notify the
					// client.
					if (requireDeviceSets > capability.getAvailable() && capability.getNeedWait() > 0)
						deviceBusyEventBus.post(new Object[] { task, clientHost });*/
				} else {
					log.info("I Didn't have capability to execute Test:" + test);
				}
			} else {
				log.warn("Incoming Message with Null Task. Message:" + CommonUtils.toJson(message));
			}
		} catch (Exception e) {
			log.error("Handling Message in TaskSubscriber got Exception. Message:" + CommonUtils.toJson(message), e);
		}
	}

}
