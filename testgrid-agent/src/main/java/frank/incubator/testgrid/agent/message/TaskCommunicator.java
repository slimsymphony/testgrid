package frank.incubator.testgrid.agent.message;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Message;

import frank.incubator.testgrid.agent.AgentNode;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.Test;

/**
 * Task Communicator in Agent Node side. It will responsible for get
 * Confirmation of client's test request. Response to queue
 * "HUB_TASK_COMMUNICATION".
 * 
 * @author Wang Frank
 * 
 */
public class TaskCommunicator extends MessageListenerAdapter {

	public TaskCommunicator(AgentNode agentNode) {
		super("AgentTaskCommunicator");
		this.agent = agentNode;
	}

	private AgentNode agent;

	private String convertState(int state) {
		switch (state) {
		case Constants.MSG_TASK_ACCEPT:
			return "MSG_TASK_ACCEPT";
		case Constants.MSG_TASK_CONFIRM:
			return "MSG_TASK_CONFIRM";
		case Constants.MSG_TEST_READY:
			return "MSG_TEST_READY";
		case Constants.MSG_TEST_FAIL:
			return "MSG_TEST_FAIL";
		case Constants.MSG_TEST_FINISHED:
			return "MSG_TEST_FINISHED";
		case Constants.MSG_TASK_RESERVE:
			return "MSG_TASK_RESERVE";
		case Constants.MSG_TASK_START:
			return "MSG_TASK_START";
		case Constants.MSG_START_FT_FETCH:
			return "MSG_START_FT_FETCH";
		case Constants.MSG_TEST_FINISH_CONFIRM:
			return "MSG_TEST_FINISH_CONFIRM";
		case Constants.MSG_TASK_CANCEL:
			return "MSG_TASK_CANCEL";
		default:
			return "UNKNOWN";
		}
	}

	/**
	 * Be aware: All the operations here in this method should be non-blocking!
	 */
	@Override
	public void onMessage(final Message message) {
		try {
			super.onMessage(message);
			int type = getProperty(message, Constants.MSG_HEAD_TRANSACTION, 0);
			final String from = getProperty(message, Constants.MSG_HEAD_FROM, "Unknown");
			log.info("Received task message: " + type + "-" + convertState(type) + " from " + from);
			switch (type) {
			case Constants.MSG_TASK_RESERVE:
				final Test test = CommonUtils.fromJson(getProperty(message, Constants.MSG_HEAD_TEST, ""), Test.class);
				test.deleteObservers();
				test.addObserver(agent.getNotifier());
				log.info("Got request from:" + from + " to reserve devices for test:" + test.getId() + ", requirement:"
						+ test.getRequirements());
				if (agent.reserveForTest(test, test.getRequirements())) {
					final Message msg = agent.getHub().createMessage(Constants.BROKER_TASK);
					setProperty(msg, Constants.MSG_HEAD_TARGET, from);
					setProperty(msg, Constants.MSG_HEAD_FROM, Constants.MSG_TARGET_AGENT + CommonUtils.getHostName());
					setProperty(msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_CONFIRM);
					setProperty(msg, Constants.MSG_HEAD_TASKID, test.getTaskID());
					setProperty(msg, Constants.MSG_HEAD_TESTID, test.getId());
					setProperty(msg, Constants.MSG_HEAD_RESPONSE, Constants.RESERVE_SUCC);
					Collection<Device> devices = agent.getReservedDevices().get(test);
					Map<String, Integer> dvs = new HashMap<String, Integer>();
					for (Device d : devices) {
						dvs.put(d.getId(), d.getRole());
					}
					setProperty(msg, Constants.MSG_HEAD_RESERVED_DEVICES, CommonUtils.toJson(dvs));
					agent.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).send(msg);
					if (dvs != null)
						dvs.clear();
				} else {
					// RESERVE Failed.
					log.error("Reserve for test:" + test.getId() + " failed.");
					Message msg = agent.getHub().createMessage(Constants.BROKER_TASK);
					setProperty(msg, Constants.MSG_HEAD_TARGET, from);
					setProperty(msg, Constants.MSG_HEAD_TASKID, test.getTaskID());
					setProperty(msg, Constants.MSG_HEAD_TESTID, test.getId());
					setProperty(msg, Constants.MSG_HEAD_FROM, Constants.MSG_TARGET_AGENT + CommonUtils.getHostName());
					setProperty(msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_CONFIRM);
					setProperty(msg, Constants.MSG_HEAD_RESPONSE, Constants.RESERVE_FAILED);
					agent.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).send(msg);
				}
				break;
			case Constants.MSG_TASK_START:
				String testId = getProperty(message, Constants.MSG_HEAD_TESTID, "");
				log.info("Get request to start test:" + testId);
				try {
					agent.startTest(testId, from);
				} catch (Exception e) {
					log.error("Start Test[ " + testId + " ] met exception.", e);
					Message m = agent.getHub().createMessage(Constants.BROKER_TASK);
					setProperty(m, Constants.MSG_HEAD_TARGET, from);
					setProperty(m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FAIL);
					setProperty(m, Constants.MSG_HEAD_ERROR, e.getMessage() + CommonUtils.getErrorStack(e));
					setProperty(m, Constants.MSG_HEAD_TASKID, getProperty(message, Constants.MSG_HEAD_TASKID, ""));
					agent.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).send(m);
				}
				break;
			case Constants.MSG_TASK_CANCEL:
				String testID = getProperty(message, Constants.MSG_HEAD_TESTID, "");
				log.info("Received a Request to cancel Test[" + testID + "] from " + from);
				String reason = "Got Cancel request from " + from;
				int result = agent.cancelTest(testID, reason);
				log.info("Cancel Test result:" + result);
				break;
			case Constants.MSG_TEST_FINISH_CONFIRM:
				String tId = getProperty(message, Constants.MSG_HEAD_TESTID, "");
				boolean succ = getProperty(message, Constants.MSG_HEAD_TEST_SUCC, true);
				log.info("Got message test:" + tId + " have finished:" + succ);
				agent.cleanUpTest(tId);
				break;
			default:
				log.warn("The message type didn't compatible with Task communicator. Ignore, Message="
						+ CommonUtils.toJson(message));
			}
		} catch (Exception e) {
			log.error("Handling Message in TaskCommunicator got Exception. Message:" + CommonUtils.toJson(message), e);
		}
	}
}
