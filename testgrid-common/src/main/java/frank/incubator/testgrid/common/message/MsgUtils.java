package frank.incubator.testgrid.common.message;

import static frank.incubator.testgrid.common.CommonUtils.closeQuietly;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.BlobMessage;

public class MsgUtils {

	public static Queue createQueue(String uri, String queueName) throws JMSException {
		QueueConnectionFactory connectionFactory = null;
		QueueConnection connection = null;
		QueueSession session = null;
		Queue queue = null;
		try {
			connectionFactory = new ActiveMQConnectionFactory(uri);
			connection = connectionFactory.createQueueConnection();
			connection.start();
			session = connection.createQueueSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
			queue = session.createQueue(queueName);
			session.commit();
		} finally {
			closeQuietly(session);
			closeQuietly(connection);
		}
		return queue;
	}

	public static Topic createTopic(String uri, String topicName) throws JMSException {
		TopicConnectionFactory connectionFactory = null;
		TopicConnection connection = null;
		TopicSession session = null;
		Topic topic = null;
		try {
			connectionFactory = new ActiveMQConnectionFactory(uri);
			connection = connectionFactory.createTopicConnection();
			connection.start();
			session = connection.createTopicSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
			topic = session.createTopic(topicName);
			session.commit();
		} finally {
			closeQuietly(session);
			closeQuietly(connection);
		}
		return topic;
	}

	/**
	 * Product message for assigned queue.
	 * 
	 * @param uri
	 *            e.g.: tcp://3CNL12096:61616
	 * @param queueName
	 *            name of queue
	 * @throws JMSException
	 */
	public static void produceTextMsg2Queue(String uri, String queueName, String text) throws JMSException {
		QueueConnectionFactory connectionFactory = null;
		QueueConnection connection = null;
		QueueSession session = null;
		MessageProducer mp = null;
		try {
			connectionFactory = new ActiveMQConnectionFactory(uri);
			connection = connectionFactory.createQueueConnection();
			connection.start();
			session = connection.createQueueSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
			mp = session.createProducer(session.createQueue(queueName));
			mp.setDeliveryMode(DeliveryMode.PERSISTENT);
			mp.send(session.createTextMessage(text));
			session.commit();
		} finally {
			closeQuietly(mp);
			closeQuietly(session);
			closeQuietly(connection);
		}
	}

	/**
	 * Product message for assigned topic.
	 * 
	 * @param uri
	 *            e.g.: tcp://3CNL12096:61616
	 * @param queueName
	 *            name of queue
	 * @throws JMSException
	 */
	public static void publishTextMsg2Topic(String uri, String topicName, String text) throws JMSException {
		TopicConnectionFactory connectionFactory = null;
		TopicConnection connection = null;
		TopicSession session = null;
		TopicPublisher tp = null;
		try {
			connectionFactory = new ActiveMQConnectionFactory(uri);
			connection = connectionFactory.createTopicConnection();
			connection.start();
			session = connection.createTopicSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
			tp = session.createPublisher(session.createTopic(topicName));
			tp.setDeliveryMode(DeliveryMode.PERSISTENT);
			tp.publish(session.createTextMessage(text));
			session.commit();
		} finally {
			closeQuietly(tp);
			closeQuietly(session);
			closeQuietly(connection);
		}
	}

	public static void sendTextToDest(String uri, Destination dest, String text,
			Map<String, ? extends Object> properties) throws JMSException {
		ConnectionFactory connectionFactory = null;
		Connection conn = null;
		Session session = null;
		MessageProducer producer = null;
		try {
			connectionFactory = new ActiveMQConnectionFactory(uri);
			conn = connectionFactory.createConnection();
			session = conn.createSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
			producer = session.createProducer(dest);
			TextMessage msg = session.createTextMessage(text);
			if (properties != null) {
				for (String key : properties.keySet()) {
					msg.setObjectProperty(key, properties.get(key));
				}
			}
			producer.send(msg);
			session.commit();
		} finally {

		}
	}

	public static String consumeTextMsg4Queue(String uri, String queueName) throws JMSException {
		QueueConnectionFactory connectionFactory = null;
		QueueConnection connection = null;
		QueueSession session = null;
		TextMessage msg = null;
		MessageConsumer consumer = null;
		try {
			connectionFactory = new ActiveMQConnectionFactory(uri);
			connection = connectionFactory.createQueueConnection();
			connection.start();
			session = connection.createQueueSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
			consumer = session.createConsumer(session.createQueue(queueName));
			msg = (TextMessage) consumer.receive();
			session.commit();
		} finally {
			closeQuietly(consumer);
			closeQuietly(session);
			closeQuietly(connection);
		}
		if (msg == null)
			return null;
		else
			return msg.getText();
	}

	public static TopicSubscriber subscribeTopic(TopicSession session, String topicName, String subscriberName,
			boolean durable, MessageListener listener) throws JMSException {
		TopicSubscriber ts = null;
		if (durable)
			ts = session.createDurableSubscriber(session.createTopic(topicName), subscriberName);
		else
			ts = session.createSubscriber(session.createTopic(topicName));
		ts.setMessageListener(listener);
		session.commit();
		return ts;
	}

	public static void sendFileViaQueue(String uri, String queueName, File file) throws JMSException {
		ConnectionFactory connectionFactory = null;
		Connection connection = null;
		Session session = null;
		BlobMessage blobMsg = null;
		MessageProducer producer = null;
		try {
			connectionFactory = new ActiveMQConnectionFactory(uri);
			connection = connectionFactory.createConnection();
			connection.start();
			session = connection.createSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
			producer = session.createProducer(session.createQueue(queueName));
			blobMsg = ((ActiveMQSession) session).createBlobMessage(file);
			blobMsg.setStringProperty("FILE.NAME", file.getName());
			blobMsg.setLongProperty("FILE.SIZE", file.length());
			producer.send(blobMsg);
			session.commit();
		} finally {
			closeQuietly(producer);
			closeQuietly(session);
			closeQuietly(connection);
		}
	}

	public static BlobMessage receiveFileViaQueue(String uri, String queueName) throws JMSException, IOException {
		ConnectionFactory connectionFactory = null;
		Connection connection = null;
		Session session = null;
		BlobMessage msg = null;
		MessageConsumer consumer = null;
		try {
			connectionFactory = new ActiveMQConnectionFactory(uri);
			connection = connectionFactory.createConnection();
			connection.start();
			session = connection.createSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
			consumer = session.createConsumer(session.createQueue(queueName));
			msg = (BlobMessage) consumer.receive();
			session.commit();
		} finally {
			closeQuietly(consumer);
			closeQuietly(session);
			closeQuietly(connection);
		}
		if (msg == null)
			return null;
		else
			return msg;
	}

	public static void subscribeTopic(String url, String topic, String subscriberName, boolean durable,
			MessageHub listener) {
		// TODO Auto-generated method stub

	}
}
