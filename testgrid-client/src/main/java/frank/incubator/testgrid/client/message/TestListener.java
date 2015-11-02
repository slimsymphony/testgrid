package frank.incubator.testgrid.client.message;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Message;
import javax.jms.TextMessage;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.PropertyMessageFilter;
import frank.incubator.testgrid.common.model.Test;

/**
 * This class is defined for listening the test execution status. It will keep
 * monitoring the test state and keep information of the execution.
 * 
 * @author Wang Frank
 * 
 */
public class TestListener extends PropertyMessageFilter {

	public TestListener(final Test test, String host, OutputStream tracker) {
		this(test, host, tracker, true);
	}

	@SuppressWarnings("serial")
	public TestListener(final Test test, String host, OutputStream tracker, boolean needLog) {
		this(test, host, new HashMap<String, Object>() {
			{
				this.put(Constants.MSG_HEAD_TESTID, test.getId());
				this.put(Constants.MSG_HEAD_CONTENT, Constants.MSG_CONTENT_TEXT);
			}
		}, tracker, needLog);
	}

	public TestListener(Test test, String host, Map<String, Object> critiera, OutputStream tracker, boolean needLog) {
		super(critiera);
		this.test = test;
		this.start = System.currentTimeMillis();
		this.hostAgent = host;
		this.tracker = tracker;
		if (needLog)
			this.log = LogUtils.get("TestListener[" + test.getTaskID() +"_"  + test.getId() + "]");
	}

	private long start;
	private String hostAgent;
	private Test test;
	private OutputStream tracker;
	private LogConnector log;

	public Test getTest() {
		return test;
	}

	public OutputStream getTracker() {
		return tracker;
	}

	public LogConnector getLog() {
		return log;
	}

	@Override
	public void handle(Message message) {
		try {
			if (message instanceof TextMessage) {
				String text = ((TextMessage) message).getText();
				StringBuilder sb = new StringBuilder();
				if (text != null && text.indexOf("\n") >= 0) {
					boolean first = true;
					for (String s : text.split("\n")) {
						if (first) {
							sb.append(CommonUtils.getTime()).append(test.getTaskID()).append("[").append(test.getId()).append(" from:" + hostAgent + "]>  ")
									.append(text).append("\n");
							tracker.write(sb.toString().getBytes("UTF-8"));
							tracker.flush();
							log.info(sb.toString());
							first = false;
						} else {
							tracker.write(s.getBytes("UTF-8"));
							tracker.write('\n');
							tracker.flush();
							log.info(s);
						}
					}
				} else {
					sb.append(CommonUtils.getTime()).append(test.getTaskID()).append("[").append(test.getId()).append(" from:" + hostAgent + "]>  ")
							.append(text).append("\n");
					tracker.write(sb.toString().getBytes("UTF-8"));
					tracker.flush();
					log.info(sb.toString());
				}
			}
		} catch (Exception e) {
			log.error("Handle incoming message failed.", e);
		}
	}

	public long getStart() {
		return start;
	}

	public void setStart(long newStart) {
		this.start = newStart;
	}

	public String getHostAgent() {
		return hostAgent;
	}

	@Override
	public void dispose() {
		super.dispose();
		LogUtils.dispose(log);
	}

}
