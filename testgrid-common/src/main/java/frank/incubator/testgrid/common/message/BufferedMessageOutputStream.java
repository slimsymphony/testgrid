package frank.incubator.testgrid.common.message;

import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.slf4j.Logger;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * This class is designed for send text message to special Destination in
 * message queue. Such as Queue or Topic.
 * 
 * @author Wang Frank
 * 
 */
public class BufferedMessageOutputStream extends OutputStream {

	private MessageProducer producer;

	private Session session;

	private Logger log;

	private Map<String, Object> criteria = new HashMap<String, Object>();

	private ScheduledExecutorService scheduledExecutorService = null;

	private boolean useBuffer = false;
	/**
	 * String buffer for output text info.
	 */
	private StringBuffer sb = new StringBuffer();
	/**
	 * Default string buffer was 1k chars.
	 */
	private static int MSG_LENGTH = 4096;

	public BufferedMessageOutputStream(Session s, MessageProducer p, String key, Object val) {
		session = s;
		producer = p;
		useBuffer = false;
		criteria.put(key, val);
		log = LogUtils.getLogger("MessageExporter");
	}

	public BufferedMessageOutputStream(Session s, MessageProducer p, String key, Object val, Logger log) {
		session = s;
		producer = p;
		useBuffer = false;
		criteria.put(key, val);
		this.log = log;
	}

	public BufferedMessageOutputStream(Session s, MessageProducer p, Map<String, Object> criteria) {
		this.criteria = criteria;
		session = s;
		producer = p;
		useBuffer = false;
		log = LogUtils.getLogger("MessageExporter");
	}

	public BufferedMessageOutputStream(Session s, MessageProducer p, Map<String, Object> criteria, Logger log) {
		this.criteria = criteria;
		session = s;
		producer = p;
		useBuffer = false;
		this.log = log;
	}

	public BufferedMessageOutputStream(Session s, MessageProducer p, int bufferSize) {
		this(s, p, bufferSize, 3, 15, LogUtils.getLogger("MessageExporter"));
	}

	public BufferedMessageOutputStream(Session s, MessageProducer p, int bufferSize, Logger log) {
		this(s, p, bufferSize, 3, 15, log);
	}

	public BufferedMessageOutputStream(Session s, MessageProducer p, int delayBeforeStart, int interval) {
		this(s, p, MSG_LENGTH, delayBeforeStart, interval, LogUtils.getLogger("MessageExporter"));
	}

	public BufferedMessageOutputStream(Session s, MessageProducer p, int delayBeforeStart, int interval, Logger log) {
		this(s, p, MSG_LENGTH, delayBeforeStart, interval, log);
	}

	public BufferedMessageOutputStream(Session s, MessageProducer p, int bufferSize, int delayBeforeStart,
			int interval, Logger log) {
		session = s;
		producer = p;
		MSG_LENGTH = bufferSize;
		useBuffer = true;
		this.log = log;
		scheduledExecutorService = Executors.newScheduledThreadPool(1);
		scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}, delayBeforeStart, interval, TimeUnit.SECONDS);
	}

	@Override
	public void flush() throws IOException {
		try {
			if (sb.length() > 0 && !sb.toString().trim().isEmpty()) {
				log.info(sb.toString());
				TextMessage msg = session.createTextMessage();
				setProperty(msg, Constants.MSG_HEAD_FROM, Constants.MSG_TARGET_AGENT + CommonUtils.getHostName());
				setProperty(msg, Constants.MSG_HEAD_CONTENT, Constants.MSG_CONTENT_TEXT);
				for (Entry<String, Object> entry : criteria.entrySet()) {
					msg.setObjectProperty(entry.getKey(), entry.getValue());
				}
				msg.setText(sb.toString().trim());
				producer.send(msg);
				sb = new StringBuffer();
			}
		} catch (Exception ex) {
			log.error("export message info failed.", ex);
		}
	}

	@Override
	public void write(int b) throws IOException {
		sb.append((char) (b & 0xFF));
		if (b == '\n') {
			if (useBuffer) {
				if (sb.length() > MSG_LENGTH) {
					flush();
				}
			} else {
				flush();
			}
		}
	}

	@Override
	public synchronized void write(byte b[]) throws IOException {
		String tx = new String(b);
		sb.append(tx);
		if (useBuffer) {
			if (sb.length() > MSG_LENGTH) {
				flush();
			}
		} else {
			flush();
		}
	}

	@Override
	public void close() {
		if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown())
			scheduledExecutorService.shutdown();
		LogUtils.dispose(log);
	}
}
