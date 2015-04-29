package frank.incubator.testgrid.client.message;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;

import javax.jms.Message;

import frank.incubator.testgrid.client.TaskClient;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.model.Test;

public class NotificationHandler extends MessageListenerAdapter {
	private TaskClient client;

	public TaskClient getClient() {
		return client;
	}

	public void setClient(TaskClient client) {
		this.client = client;
	}

	public NotificationHandler(TaskClient client) {
		super("ClientNotificationHandler");
		this.client = client;
	}

	@Override
	public void handleMessage(Message msg) {
		// super.handleMessage( msg );
		try {
			String from = getProperty(msg, Constants.MSG_HEAD_FROM, "Unknown");
			log.info("Current Agent received a Notification Message coming from :" + from);
			int mode = getProperty(msg, Constants.MSG_HEAD_NOTIFICATION_TYPE, 0);
			int action = getProperty(msg, Constants.MSG_HEAD_NOTIFICATION_ACTION, 0);
			switch (mode) {
			case Constants.NOTIFICATION_TEST:
				String testId = getProperty(msg, Constants.MSG_HEAD_TESTID, "");
				if (testId == null || testId.isEmpty()) {
					log.warn("Didn't assign a test ID, no need to process. from:" + from);
					break;
				}
				Test test = client.getTestById(testId);
				if (test == null) {
					log.warn("Test["
							+ testId
							+ "] can't be found from current client, maybe it have been finished. no need to process. From:"
							+ from);
					break;
				}
				switch (action) {
				case Constants.ACTION_TEST_CANCEL:
					client.cancelTest(test, "Test been canclled by notification incoming from:" + from);
					break;
				default:
					log.warn("Client Test Notification Handler didn't Support This Action currently:" + action);
				}
				break;
			case Constants.NOTIFICATION_TASK:
				String taskId = getProperty(msg, Constants.MSG_HEAD_TASKID, "");
				if (!client.getTask().getId().equals(taskId)) {
					log.warn("Task[" + taskId + "] didn't belong to current TaskClient. taskId:" + taskId + ", from:"
							+ from);
					break;
				}
				switch (action) {
				case Constants.ACTION_TASK_CANCEL:
					client.cancelTask("Task cancelled by notification incoming from:" + from);
					break;
				default:
					log.warn("Client Task Notification Handler didn't Support This Action currently:" + action);
				}
				break;
			case Constants.NOTIFICATION_SYSTEM:
				switch (action) {
				case Constants.ACTION_CLIENT_EXIT:
					client.cancelTask("Task cancelled by notification incoming from:" + from);
					break;
				default:
					log.warn("Client System Notification Handler didn't Support This Action currently:" + action);
				}
				break;
			}
		} catch (Exception ex) {

		}
	}
}
