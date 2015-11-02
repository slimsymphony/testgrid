package frank.incubator.testgrid.agent.message;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.Message;

import frank.incubator.testgrid.agent.AgentNode;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.DeviceCapacity;
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
			case Constants.MSG_TASK_SUBSCRIBE:
				return "MSG_TASK_SUBSCRIBE";
			default:
				return "UNKNOWN";
		}
	}

	/**
	 * Be aware: All the operations here in this method should be non-blocking!
	 */
	@Override
	public void onMessage(final Message message) {
		//super.onMessage(message);
		agent.getPool().execute(new Runnable() {
			@Override
			public void run() {
				try {
					int type = getProperty(message, Constants.MSG_HEAD_TRANSACTION, 0);
					final String from = getProperty(message, Constants.MSG_HEAD_FROM, "Unknown");
					log.info("Received task message: " + type + "-" + convertState(type) + " from " + from);
					String testId = getProperty(message, Constants.MSG_HEAD_TESTID, "");
					String taskId = getProperty(message, Constants.MSG_HEAD_TASKID, "");
					switch (type) {
						case Constants.MSG_TASK_SUBSCRIBE:
							log.info("Got task requirement.");
							String testStr = getProperty(message, Constants.MSG_HEAD_TEST, "");
							if (testStr != null) {
								// Task task = CommonUtils.fromJson(taskStr, Task.class);
								Test test = CommonUtils.fromJson(testStr, Test.class);
								test.deleteObservers();
								testId = test.getId();
								taskId = test.getTaskID();
								log.info("Received a request for Test[{}:::{}] to subscribe.", taskId, testId);
								if(agent.getRunningTestById(testId) != null || agent.getReservedTestById(testId) != null) {
									log.info("Current Agent have already reserved or running the test:{}:::{}", test.getTaskID(), testId);
									return;
								}
								//log.info("Test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + " from:" + from + ", requirement:" + test.getRequirements());
								DeviceCapacity capability = agent.checkCondition(test.getId(), test.getRequirements());
								if (capability.getAvailable() > 0 || capability.getNeedWait() > 0) {
									log.info("Test:"
											+ test.getTaskID()+ Constants.TASK_TEST_SPLITER
											+ test.getId()
											+ " from "
											+ from
											+ " could be executed here, "
											+ ((capability.getAvailable() > 0) ? capability.getAvailable() + " sets of devices could be execute here" : "but you need to wait for device free."));
									try {
										Message reply = agent.getHub().createMessage(Constants.BROKER_TASK);
										setProperty(reply, Constants.MSG_HEAD_TARGET, from);
										setProperty(reply, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_ACCEPT);
										setProperty(reply, Constants.MSG_HEAD_TASKID, taskId);
										setProperty(reply, Constants.MSG_HEAD_TESTID, testId);
										setProperty(reply, Constants.MSG_HEAD_RESPONSE, capability.toString());
										agent.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).send(reply);
									} catch (Exception ex) {
										throw new MessageException("Send MSG_TASK_SUBSCRIBE Response failed.testStr:"+testStr, ex);
									}
								} else {
									log.info("I Didn't have capability to execute Test:{}:::{}", testId, taskId);
								}
							} else {
								log.warn("Incoming Message with Null Task. Message:" + CommonUtils.toJson(message));
							}
							break;
						case Constants.MSG_TASK_RESERVE:
							final Test test = CommonUtils.fromJson(getProperty(message, Constants.MSG_HEAD_TEST, ""), Test.class);
							test.deleteObservers();
							test.addObserver(agent.getNotifier());
							log.info("Got request from:" + from + " to reserve devices for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + ", requirement:" + test.getRequirements());
							if (agent.getRunningTests().containsKey(test) || agent.getReservedDevices().containsKey(test) || agent.getReservedTests().containsKey(test)) {
								log.info("Duplicate test[" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + "] reservation arrive, just ignore");
								break;
							}
							if (agent.reserveForTest(test, test.getRequirements())) {
								List<Device> ds = null;
								ds = (List<Device>) agent.getReservedDevices().get(test);
								if (ds != null && !ds.isEmpty()) {
									log.info("Succ reserve device[" + ds.get(0).getAttribute(Constants.DEVICE_SN) + "] for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId());
									final Message msg = agent.getHub().createMessage(Constants.BROKER_TASK);
									setProperty(msg, Constants.MSG_HEAD_TARGET, from);
									setProperty(msg, Constants.MSG_HEAD_FROM, Constants.MSG_TARGET_AGENT + CommonUtils.getHostName());
									setProperty(msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_CONFIRM);
									setProperty(msg, Constants.MSG_HEAD_TASKID, test.getTaskID());
									setProperty(msg, Constants.MSG_HEAD_TESTID, test.getId());
									setProperty(msg, Constants.MSG_HEAD_RESPONSE, Constants.RESERVE_SUCC);
									Map<String, Integer> dvs = new HashMap<String, Integer>();
									for (Device d : ds) {
										dvs.put(d.getId(), d.getRole());
									}
									setProperty(msg, Constants.MSG_HEAD_RESERVED_DEVICES, CommonUtils.toJson(dvs));
									agent.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).send(msg);
									if (dvs != null)
										dvs.clear();
								} else {
									log.warn("Can't find reserve record for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId());
								}
							} else {
								// RESERVE Failed.
								log.error("Reserve for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + " failed.");
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
							log.info("Get request to start test:" + taskId + Constants.TASK_TEST_SPLITER + testId);
							try {
								agent.startTest(testId, from);
							} catch (Exception e) {
								log.error("Start Test[ " + taskId + Constants.TASK_TEST_SPLITER + testId + " ] met exception.", e);
								Message m = agent.getHub().createMessage(Constants.BROKER_TASK);
								setProperty(m, Constants.MSG_HEAD_TARGET, from);
								setProperty(m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FAIL);
								setProperty(m, Constants.MSG_HEAD_ERROR, e.getMessage() + CommonUtils.getErrorStack(e));
								setProperty(m, Constants.MSG_HEAD_TASKID, getProperty(message, Constants.MSG_HEAD_TASKID, ""));
								agent.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).send(m);
							}
							break;
						case Constants.MSG_TASK_CANCEL:
							log.info("Received a Request to cancel Test["  + taskId + Constants.TASK_TEST_SPLITER + testId + "] from " + from);
							String reason = "Got Cancel request from " + from;
							int result = agent.cancelTest(testId, reason);
							log.info("Cancel Test[{}:::{}] result:{}", taskId, testId, result);
							break;
						case Constants.MSG_TEST_FINISH_CONFIRM:
							boolean succ = getProperty(message, Constants.MSG_HEAD_TEST_SUCC, true);
							log.info("Got message test:" + taskId + Constants.TASK_TEST_SPLITER + testId + " have finished:" + succ);
							agent.cleanUpTest(testId);
							break;
						default:
							log.warn("The message type didn't compatible with Task communicator. Ignore, Message=" + CommonUtils.toJson(message));
					}
				} catch (Exception e) {
					log.error("Handling Message in TaskCommunicator got Exception. Message:" + CommonUtils.toJson(message), e);
				}
			}
		});
	}
}
