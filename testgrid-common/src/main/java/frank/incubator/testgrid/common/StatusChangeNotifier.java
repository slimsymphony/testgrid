package frank.incubator.testgrid.common;

import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.jms.Message;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.MessageBroker;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.Pipe;
import frank.incubator.testgrid.common.model.BaseObject;
import frank.incubator.testgrid.common.plugin.TestGridPlugin;

/**
 * This class is representing broadcast status change notifications to
 * corresponding Message pipeline.
 * 
 * @author Wang Frank
 *
 */
@SuppressWarnings("rawtypes")
public class StatusChangeNotifier implements Observer {

	public StatusChangeNotifier(MessageHub hub, String targetMode) {
		this.hub = hub;
		this.targetMode = targetMode;
	}

	private transient MessageHub hub;

	private transient String targetMode;

	private transient LogConnector log = LogUtils.get("StatusChangeNotifier");

	private transient static Map<String, String[]> mapping = new HashMap<String, String[]>();
	static {
		mapping.put("Device", new String[] { Constants.BROKER_STATUS, Constants.HUB_DEVICE_STATUS });
		mapping.put("Test", new String[] { Constants.BROKER_STATUS, Constants.HUB_TEST_STATUS });
		mapping.put("Task", new String[] { Constants.BROKER_STATUS, Constants.HUB_TASK_STATUS });
		mapping.put("Agent", new String[] { Constants.BROKER_STATUS, Constants.HUB_AGENT_STATUS });
		mapping.put("Client", new String[] { Constants.BROKER_STATUS, Constants.HUB_CLIENT_STATUS });
	}

	private transient Map<String, Collection<Callable>> eventListeners = new HashMap<String, Collection<Callable>>();

	private transient ListeningExecutorService pluginPool = MoreExecutors.listeningDecorator(Executors
			.newCachedThreadPool());

	public Map<String, Collection<Callable>> getEventListeners() {
		return eventListeners;
	}

	public void addListener(String listenType, Callable runnable) {
		if (listenType != null && runnable != null) {
			Collection<Callable> listeners = null;
			listeners = eventListeners.get(listenType);
			if (listeners == null) {
				listeners = new ArrayList<Callable>();
				eventListeners.put(listenType, listeners);
			}
			listeners.add(runnable);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void update(Observable object, Object arg) {
		try {
			if (object instanceof BaseObject) {
				BaseObject obj = (BaseObject) object;
				Message msg = null;
				MessageBroker broker = null;
				Pipe pipe = null;
				String brokerName = null;
				String pipeName = null;
				long current = System.currentTimeMillis();
				String cName = obj.getClass().getSimpleName();
				if ("AndroidDevice".equals(cName))
					cName = "Device";
				log.info("Incoming status change source is :" + cName + ", observable:" + obj + " ,argument :" + arg);
				switch (cName) {
				case "Device":
					brokerName = mapping.get("Device")[0];
					pipeName = mapping.get("Device")[1];
					broker = hub.getBroker(brokerName);
					if (broker == null) {
						log.error("Mapping Broker not exist for Device Status Change Notifier.brokerName=" + broker);
						break;
					}
					pipe = broker.getPipe(pipeName);
					if (pipe == null) {
						log.error("Current Hub didn't including pipe[" + mapping.get("Device") + "]. Observable:" + obj
								+ ", arg:" + arg);
					} else {
						msg = pipe.createMessage();
						setProperty(msg, Constants.MSG_HEAD_DEVICE_EVENT, (Integer) arg);
						setProperty(msg, Constants.MSG_HEAD_UPDATETIME, current);
						setProperty(msg, Constants.MSG_HEAD_DEVICE_INFO, obj.toString());
						pipe.send(msg);
					}
					break;
				case "Agent":
					brokerName = mapping.get(cName)[0];
					pipeName = mapping.get(cName)[1];
					broker = hub.getBroker(brokerName);
					if (broker == null) {
						log.error("Mapping Broker not exist for " + cName + " Status Change Notifier.brokerName="
								+ broker);
						break;
					}
					pipe = broker.getPipe(pipeName);
					if (pipe == null) {
						log.error("Current Hub didn't including pipe[" + mapping.get("Agent") + "]. Observable:" + obj
								+ ", arg:" + arg);
					} else {
						msg = pipe.createMessage();
						setProperty(msg, Constants.MSG_HEAD_AGENTINFO, obj.toString());
						pipe.send(msg);
					}
					break;
				case "Test":
					brokerName = mapping.get(cName)[0];
					pipeName = mapping.get(cName)[1];
					broker = hub.getBroker(brokerName);
					if (broker == null) {
						log.error("Mapping Broker not exist for " + cName + " Status Change Notifier.brokerName="
								+ broker);
						break;
					}
					pipe = broker.getPipe(pipeName);
					if (pipe == null) {
						log.error("Current Hub didn't including pipe[" + mapping.get("Test") + "]. Observable:" + obj
								+ ", arg:" + arg);
					} else {
						long time = (Long) arg;
						msg = pipe.createMessage();
						setProperty(msg, Constants.MSG_HEAD_TEST_INFO, obj.toString());
						setProperty(msg, Constants.MSG_HEAD_RESERVE_TIME, time);
						setProperty(msg, Constants.MSG_HEAD_RUNNING_TIME, (current - time));
						pipe.send(msg);
					}
					break;
				case "Task":
					brokerName = mapping.get(cName)[0];
					pipeName = mapping.get(cName)[1];
					broker = hub.getBroker(brokerName);
					if (broker == null) {
						log.error("Mapping Broker not exist for " + cName + " Status Change Notifier.brokerName="
								+ broker);
						break;
					}
					pipe = broker.getPipe(pipeName);
					if (pipe == null) {
						log.error("Current Hub didn't including pipe[" + mapping.get("Task") + "]. Observable:" + obj
								+ ", arg:" + arg);
					} else {
						msg = pipe.createMessage();
						setProperty(msg, Constants.MSG_HEAD_TASKSTATE, obj.toString());
						pipe.send(msg);
					}
					break;
				case "Client":
					brokerName = mapping.get(cName)[0];
					pipeName = mapping.get(cName)[1];
					broker = hub.getBroker(brokerName);
					if (broker == null) {
						log.error("Mapping Broker not exist for " + cName + " Status Change Notifier.brokerName="
								+ broker);
						break;
					}
					pipe = broker.getPipe(pipeName);
					if (pipe == null) {
						log.error("Current Hub didn't including pipe[" + mapping.get("Client") + "]. Observable:" + obj
								+ ", arg:" + arg);
					} else {
						msg = pipe.createMessage();
						setProperty(msg, Constants.MSG_HEAD_CLIENTINFO, obj.toString());
						pipe.send(msg);
					}
					break;
				default:
					log.warn("Didn't support this type of object status change:" + obj.getClass().getCanonicalName());
				}

				if (eventListeners.get(cName) != null) {
					for (Callable callable : eventListeners.get(cName)) {
						if (obj.hasChanged()) {
							if (callable instanceof TestGridPlugin) {
								TestGridPlugin plugin = (TestGridPlugin) callable;
								plugin.setResult(pluginPool.submit(callable));
								Futures.addCallback(plugin.getResult(), plugin);
								log.info("Trigger plugin[" + plugin.getName() + "]");
							} else {
								pluginPool.submit(callable);
							}
						}
					}
				}
			} else {
				log.warn("Incoming object didn't supported now. Object=" + object + " , argments=" + arg);
			}
		} catch (Exception ex) {
			log.error("Update Model status failed. Obj=" + object + " , argments=" + arg, ex);
		}
	}

}
