package frank.incubator.testgrid.common.message;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.BaseObject;

/**
 * Represent a MessageQueue Broker. It's a channel with represent a unique
 * port/way to communicate with MessageQueue service.
 * 
 * @author Wang Frank
 *
 */
public class MessageBroker extends BaseObject implements ExceptionListener {
	private String uri;
	private Connection conn;
	private ConnectionFactory connectionFactory;
	private Session session;
	private Map<String, Pipe> pipes = new HashMap<String, Pipe>();
	private LogConnector log;
	private String hostType;
	private MqBuilder factoryType;

	public MessageBroker(String id, String uri, String mqType) throws MessageException {
		this(id, uri, mqType, "");
	}

	public MessageBroker(String id, String uri, MqBuilder factoryType, String hostType) throws MessageException {
		this.id = id;
		this.uri = uri;
		this.factoryType = factoryType;
		this.hostType = hostType;
		this.log = LogUtils.get("Broker-" + id);
		establishConnection();
	}

	public MessageBroker(String name, String uri, String mqType, String hostType) throws MessageException {
		this(name, uri, MqBuilder.parse(mqType), hostType);
	}

	public LogConnector getLog() {
		return log;
	}

	public void setLog(LogConnector log) {
		this.log = log;
	}

	public String getUri() {
		return uri;
	}

	public Connection getConn() {
		return conn;
	}

	public ConnectionFactory getConnectionFactory() {
		return connectionFactory;
	}

	public Session getSession() {
		return session;
	}

	public Map<String, Pipe> getPipes() {
		return pipes;
	}

	public String getHostType() {
		return hostType;
	}

	public MqBuilder getFactoryType() {
		return factoryType;
	}

	public void establishConnection() throws MessageException {
		int retryTimes = 0;
		while (true) {
			try {
				if (connectionFactory == null)
					connectionFactory = MqBuilder.getFactoryInstance(factoryType, uri);
				if (connectionFactory == null)
					throw new NullPointerException("Connection Factory is Null for broker[" + id + ":" + factoryType
							+ "]!");
				conn = connectionFactory.createConnection();
				conn.setExceptionListener(this);
				conn.start();
				session = conn.createSession(Boolean.FALSE, Session.AUTO_ACKNOWLEDGE);
				for (Pipe hn : pipes.values()) {
					rebindPipe(hn);
				}
				break;
			} catch (Exception e) {
				log.error("Establish Connection to Message Service got exception. broker[" + id + ":" + factoryType
						+ "]", e);
				retryTimes++;
				try {
					long sleepSeconds = retryTimes * 10;
					if (sleepSeconds > 60)
						sleepSeconds = 60;
					TimeUnit.SECONDS.sleep(60);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}

				log.error("Start retry establishing connection to Message Service[" + id + ":" + factoryType + "] the "
						+ retryTimes + " times.");
				if (retryTimes == 10) {
					throw new MessageException("Create MessageBroker failed. Can't connect to Message Service[" + uri
							+ "].", e);
				}
			}
		}
	}

	/**
	 * Bind handler to special Queue or Topic.
	 * 
	 * @param type
	 * @param name
	 * @param messageSelector
	 * @param listener
	 * @param filters
	 * @return
	 * @throws MessageException
	 */
	public Pipe bindHandlers(Type type, String name, String messageSelector, MessageListener listener,
			MessageFilter... filters) throws MessageException {
		return bindHandlers(type, name, messageSelector, listener, null, filters);
	}

	/**
	 * Bind handler to special Queue or Topic. With output supported.
	 * 
	 * @param type
	 * @param name
	 * @param messageSelector
	 * @param listener
	 * @param tracker
	 * @param filters
	 * @return
	 * @throws MessageException
	 */
	public Pipe bindHandlers(Type type, String name, String messageSelector, MessageListener listener,
			OutputStream tracker, MessageFilter... filters) throws MessageException {
		if (type == null || name == null)
			throw new MessageException("Unqualified param provided for binding.type:" + type + ",name=" + name);
		Pipe hn = new Pipe(name);
		hn.setHostType(hostType);
		MessageProducer producer = null;
		try {
			hn.setTransactional(false);
			hn.setParentBroker(this);
			hn.setMessageSelector(messageSelector);
			if (type.equals(javax.jms.Topic.class)) {
				Topic topic = null;
				topic = session.createTopic(name);
				hn.setDest(topic);
				producer = session.createProducer(hn.getDest());
				producer.setTimeToLive(Constants.ONE_MINUTE * 5);
				hn.setProducer(producer);
				TopicSubscriber ts = null;
				if (listener != null || filters != null) {
					if (messageSelector != null && !messageSelector.trim().isEmpty())
						ts = ((TopicSession) session).createSubscriber(topic, messageSelector, false);
					else
						ts = ((TopicSession) session).createSubscriber(topic);
					hn.setConsumer(ts);
				}

				if (listener != null) {
					MessageListenerAdapter utml = new MessageListenerAdapter(name + "-listener", null, tracker,
							listener, filters);
					ts.setMessageListener(utml);
					hn.setListener(utml);
				}

			} else if (type.equals(javax.jms.Queue.class)) {
				Queue q = null;
				q = session.createQueue(name);
				hn.setDest(q);
				producer = session.createProducer(hn.getDest());
				producer.setTimeToLive(Constants.ONE_MINUTE * 5);
				hn.setProducer(producer);
				QueueReceiver qr = null;
				if (listener != null || (filters != null && filters.length > 0)) {
					if (messageSelector != null && !messageSelector.trim().isEmpty())
						qr = ((QueueSession) session).createReceiver(q, messageSelector);
					else
						qr = ((QueueSession) session).createReceiver(q);
					hn.setConsumer(qr);
				}
				if (listener != null) {
					MessageListenerAdapter utml = new MessageListenerAdapter(name + "-listener", null, tracker,
							listener, filters);
					qr.setMessageListener(utml);
					hn.setListener(utml);
				}
			}

			pipes.put(name, hn);
			log.info("Bind success, type:" + type + ",name:" + name + ",listener:" + listener);
			return hn;
		} catch (JMSException ex) {
			onException(ex);
			throw new MessageException("Bind message handler failed. type:" + type + ",name=" + name + ",listener:"
					+ listener, ex);
		}
	}

	public void rebindPipe(Pipe pipe) throws JMSException {
		if (pipe == null) {
			log.warn("Try to rebind a null pointer pipe.");
			return;
		}
		MessageProducer producer = null;
		if (pipe.getDest() instanceof Topic) {
			pipe.setDest(session.createTopic(((Topic) pipe.getDest()).getTopicName()));
			if (pipe.getConsumer() != null) {
				TopicSubscriber ts = null;
				if (pipe.getMessageSelector() != null && !pipe.getMessageSelector().trim().isEmpty())
					ts = ((TopicSession) session).createSubscriber((Topic) pipe.getDest(), pipe.getMessageSelector(),
							false);
				else
					ts = ((TopicSession) session).createSubscriber((Topic) pipe.getDest());
				pipe.setConsumer(ts);
				pipe.getConsumer().setMessageListener(pipe.getListener());
			}
			producer = session.createProducer(pipe.getDest());
			producer.setTimeToLive(Constants.ONE_MINUTE * 5);
			pipe.setProducer(producer);
		} else if (pipe.getDest() instanceof Queue) {
			String qn = ((Queue) pipe.getDest()).getQueueName();
			pipe.setDest(session.createQueue(qn));
			if (pipe.getConsumer() != null) {
				QueueReceiver qr = null;
				if (pipe.getMessageSelector() != null && !pipe.getMessageSelector().trim().isEmpty())
					qr = ((QueueSession) session).createReceiver((Queue) pipe.getDest(), pipe.getMessageSelector());
				else
					qr = ((QueueSession) session).createReceiver((Queue) pipe.getDest());
				pipe.setConsumer(qr);
				pipe.getConsumer().setMessageListener(pipe.getListener());
			}
			producer = session.createProducer(pipe.getDest());
			producer.setTimeToLive(Constants.ONE_MINUTE * 5);
			pipe.setProducer(producer);
		}
	}

	public Pipe getPipe(String pipeName) {
		return pipes.get(pipeName);
	}

	/**
	 * Dispose all the resources related with the MessageHub.
	 */
	public void dispose() {
		for (Pipe hn : pipes.values()) {
			try {
				if (hn.getConsumer() != null) {
					MessageListener ml = hn.getConsumer().getMessageListener();
					if (ml instanceof MessageListenerAdapter)
						((MessageListenerAdapter) ml).dispose();
					hn.getConsumer().setMessageListener(null);
				}
			} catch (Exception ex) {
				log.error("Unset Message Listener for " + hn.getConsumer() + " failed.", ex);
			} finally {
				CommonUtils.closeQuietly(hn.getConsumer());
				CommonUtils.closeQuietly(hn.getProducer());
			}
		}
		pipes.clear();
		CommonUtils.closeQuietly(session);
		CommonUtils.closeQuietly(conn);
		LogUtils.dispose(log);
	}

	/**
	 * Dispose all the resources related with the MessageHub.
	 */
	public void dispose4Reconnect() {
		for (Pipe hn : pipes.values()) {
			try {
				if (hn.getConsumer() != null) {
					MessageListener ml = hn.getConsumer().getMessageListener();
					if (ml instanceof MessageListenerAdapter)
						((MessageListenerAdapter) ml).dispose();
					hn.getConsumer().setMessageListener(null);
				}
			} catch (Exception ex) {
				log.error("Unset Message Listener for " + hn.getConsumer() + " failed.", ex);
			} finally {
				CommonUtils.closeQuietly(hn.getConsumer());
				CommonUtils.closeQuietly(hn.getProducer());
			}
		}
		CommonUtils.closeQuietly(session);
		CommonUtils.closeQuietly(conn);
		LogUtils.dispose(log);
	}

	@Override
	public synchronized void onException(JMSException exception) {
		log.error("Message Hub met Exception.", exception);
		boolean connectionOk = verifyConnection();
		if (!connectionOk) {
			log.error("Try to recreate connection and register all the consumer/producer.");
			dispose4Reconnect();
			try {
				establishConnection();
			} catch (Exception ex) {
				log.error("Estabilish Connection failed broker[" + id + "].");
			}
			log.error("Recover connection success!");
		}
	}

	/**
	 * Verify whether the JMS connection was still validate.
	 * 
	 * @return result
	 */
	private boolean verifyConnection() {
		boolean result = false;
		TemporaryQueue tq = null;
		MessageProducer producer = null;
		MessageConsumer consumer = null;
		try {
			tq = this.session.createTemporaryQueue();
			producer = this.session.createProducer(tq);
			producer.setTimeToLive(Constants.ONE_MINUTE * 5);
			consumer = this.session.createConsumer(tq);
			String token = CommonUtils.generateToken();
			Message msg = this.session.createMessage();
			setProperty(msg, "heartbeat", token);
			producer.send(msg);
			Message msg2 = consumer.receive(Constants.ONE_SECOND * 30);
			if (msg2 != null && token.equals(getProperty(msg2, "heartbeat", "")))
				result = true;
		} catch (Exception e) {
			log.error("Verify Message Connection failed.", e);
		} finally {
			CommonUtils.closeQuietly(consumer);
			CommonUtils.closeQuietly(producer);
			String tqn = null;
			try {
				if (tq != null) {
					tqn = tq.getQueueName();
					tq.delete();
				}
			} catch (Exception ex) {
				log.error("Delete temporary queue[" + tqn + "] failed.", ex);
			}
		}
		return result;
	}
}
