package frank.incubator.testgrid.agent.message;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.jms.Message;

import com.google.gson.reflect.TypeToken;
import frank.incubator.testgrid.agent.AgentNode;
import frank.incubator.testgrid.agent.plugin.AbstractAgentPlugin;
import frank.incubator.testgrid.agent.plugin.PluginManager;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.message.Pipe;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.plugin.TestGridPlugin;

/**
 * This notification handler is receiving notification(administration) message
 * and do corresponding actions.
 * 
 * @author Wang Frank
 * 
 */
public class NotificationHandler extends MessageListenerAdapter {

	private AgentNode agent;

	public NotificationHandler(AgentNode agent) {
		super("AgentNotificationHandler");
		this.agent = agent;
	}

	@Override
	public void handleMessage(Message msg) {
		// super.handleMessage( msg );
		try {
			String from = getProperty(msg, Constants.MSG_HEAD_FROM, "");
			int mode = getProperty(msg, Constants.MSG_HEAD_NOTIFICATION_TYPE, 0);
			int action = getProperty(msg, Constants.MSG_HEAD_NOTIFICATION_ACTION, 0);
			log.info("Current Agent received a Notification Message coming from :" + from + ", Type:" + mode
					+ ", action:" + action);
			switch (mode) {
			case Constants.NOTIFICATION_DEVICE:
				String deviceCondition = getProperty(msg, Constants.MSG_HEAD_DEVICE_CONDITION, "");
				Map<String, Object> conditions = CommonUtils.fromJson(deviceCondition,
						new TypeToken<Map<String, Object>>() {
						}.getType());
				Collection<Device> devices = agent.getDm().queryDevices(conditions);
				if (devices == null || devices.isEmpty()) {
					log.warn("Didn't find any devices compatible with the condition:" + deviceCondition);
					return;
				}
				switch (action) {
				case Constants.ACTION_DEVICE_RELEASE:
					log.info("Begin to release devices for because received a release device notification message.");
					agent.getDm().releaseDevices(devices);
					break;
				case Constants.ACTION_DEVICE_RESERVE:
					String testId = getProperty(msg, Constants.MSG_HEAD_TESTID, "");
					for (Device device : devices) {
						device.setRole(Device.ROLE_MAIN);
						device.setPreState(device.getState());
						device.setState(Device.DEVICE_RESERVED);
						device.setTaskStatus(testId);
						log.info("Device[" + device.toString() + "] have been reserved for:" + testId);
					}
					break;
				case Constants.ACTION_DEVICE_OFFLINE:
					for (Device device : devices) {
						device.setRole(Device.ROLE_MAIN);
						device.setPreState(device.getState());
						device.setState(Device.DEVICE_LOST);
						log.info("Device[" + device.toString() + "] have been set offline.");
					}
					break;
				case Constants.ACTION_DEVICE_REMOVE:
					for (Device device : devices) {
						agent.getDm().removeDevice(device);
						log.info("Device[" + device.toString() + "] have been removed from this agent.");
					}
				default:
					log.warn("Unsupported Device Action:" + action);
				}
				break;
			case Constants.NOTIFICATION_TEST:
				String testId = getProperty(msg, Constants.MSG_HEAD_TESTID, "");
				if (testId == null || testId.isEmpty()) {
					log.warn("Didn't provide testId. ignored.");
					return;
				}
				switch (action) {
				case Constants.ACTION_TEST_CHECK:
					log.info("Check test:" + testId + " status.");
					boolean found = false;
					String state = "";
					for (Test t : agent.getReservedTests().keySet()) {
						if (t.getId().equals(testId)) {
							found = true;
							state = "Reserved";
							break;
						}
					}
					if (!found)
						for (Test t : agent.getRunningTests().keySet()) {
							if (t.getId().equals(testId)) {
								found = true;
								state = "Running";
								break;
							}
						}
					Pipe pipe = agent.getHub().getPipe(Constants.HUB_AGENT_NOTIFICATION);
					Message reply = pipe.createMessage(Constants.MSG_TARGET_AGENT);
					setProperty(reply, Constants.MSG_HEAD_TARGET, from);
					setProperty(reply, Constants.MSG_HEAD_TESTID, testId);
					setProperty(reply, Constants.MSG_HEAD_RESPONSE, found);
					setProperty(reply, Constants.MSG_HEAD_RESPONSE_DETAIL, state);
					pipe.send(reply);
					break;
				case Constants.ACTION_TEST_CANCEL:
					int result = agent.cancelTest(testId, "Cancelled by Notification from:" + from);
					log.info("Cancel Test result:" + result);
					break;
				default:
					log.warn("Unsupported Test Action:" + action);
				}
				break;
			case Constants.NOTIFICATION_SYSTEM:
				switch (action) {
				case Constants.ACTION_AGENT_EXIT:
					agent.shutdown("Stop by Notification from : " + from);
					break;
				case Constants.ACTION_AGENT_MAINTAIN:
					log.warn("Switch to maintain mode by Notification from : " + from);
					agent.setMaintainMode(true);
					break;
				case Constants.ACTION_AGENT_NORMAL:
					log.info("Switch to normal working mode by Notification from : " + from);
					agent.setMaintainMode(false);
					break;
				case Constants.ACTION_AGENT_CONFIG_UPDATE:
					log.info("Change agent configuration by Notification from : " + from);
					Properties props = CommonUtils.fromJson(getProperty(msg, Constants.MSG_HEAD_CONFIG_CHANGES, ""),
							Properties.class);
					for (Object key : props.keySet()) {
						String val = (String) props.get(key);
						log.info("Update the Agent config[" + key + "]=" + val);
						agent.getConfig().setProperty((String) key, val);
					}
				case Constants.ACTION_AGENT_ENABLE_HTTPSERVER:
					log.info("Going to enable agent http service by notification from :" + from);
					agent.startHttpService(CommonUtils.availablePort(5451));
					break;
				case Constants.ACTION_AGENT_DISABLE_HTTPSERVER:
					log.info("Going to diable agent http service by notification from :" + from);
					agent.stopHttpService();
					break;
				case Constants.ACTION_AGENT_START_PLUGIN:
					String pluginName = getProperty(msg, Constants.MSG_HEAD_PLUGIN_NAME, "");
					log.info("Going to enable plugin[" + pluginName + "].");
					TestGridPlugin<?> plugin = PluginManager.getPlugin(pluginName);
					if (plugin != null) {
						switch (plugin.getState()) {
						case TestGridPlugin.IDLE:
							plugin.start();
							break;
						case TestGridPlugin.STOPPED:
							PluginManager.initialize(agent, plugin.getName());
							break;
						}
					} else
						PluginManager.initialize(agent, pluginName);
					break;
				case Constants.ACTION_AGENT_SUSPEND_PLUGIN:
					pluginName = getProperty(msg, Constants.MSG_HEAD_PLUGIN_NAME, "");
					log.info("Going to suspend plugin[" + pluginName + "].");
					plugin = PluginManager.getPlugin(pluginName);
					if (plugin != null) {
						switch (plugin.getState()) {
						case TestGridPlugin.STARTED:
							plugin.suspend();
							break;
						}
					} else
						log.warn("Didn't found plugin[" + pluginName + "]. disable invalid.");
					break;
				case Constants.ACTION_AGENT_DEACTIVE_PLUGIN:
					pluginName = getProperty(msg, Constants.MSG_HEAD_PLUGIN_NAME, "");
					log.info("Going to deactive plugin[" + pluginName + "].");
					plugin = PluginManager.getPlugin(pluginName);
					if (plugin != null) {
						plugin.deactive();
					}
					break;
				case Constants.ACTION_AGENT_PLUGIN_CONFIG:
					pluginName = getProperty(msg, Constants.MSG_HEAD_PLUGIN_NAME, "");
					log.info("Going to config plugin[" + pluginName + "].");
					plugin = PluginManager.getPlugin(pluginName);
					if (plugin != null) {
						if (msg.propertyExists(Constants.MSG_HEAD_PLUGIN_DELAY)) {
							long delay = getProperty(msg, Constants.MSG_HEAD_PLUGIN_DELAY, Long.class);
							((AbstractAgentPlugin<?>) plugin).changeSchedule(delay, TimeUnit.SECONDS);
						}
						if (msg.propertyExists(Constants.MSG_HEAD_PLUGIN_ATTRIBUTES)) {
							Map<String, Object> atts = CommonUtils.fromJson(
									getProperty(msg, Constants.MSG_HEAD_PLUGIN_ATTRIBUTES, ""),
									new TypeToken<Map<String, Object>>() {
									}.getType());
							for (String att : atts.keySet()) {
								((AbstractAgentPlugin<?>) plugin).getAttributes().put(att, atts.get(att));
							}
						}
					} else
						log.warn("Didn't found plugin[" + pluginName + "]. config plugin invalid.");
					break;
				default:
					log.warn("Unsupported System Action:" + action);
				}
				break;
			default:
				log.warn("Didn't support this Notification Type:" + mode + " coming from " + from);
			}
		} catch (Exception ex) {
			log.error("Exception catched.", ex);
		}
	}
}
