package frank.incubator.testgrid.common.message;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;

/**
 * MessageHub, which provide a basic functions for message in and out.
 * 
 * @author Wang Frank
 * 
 */
public class MessageHub {

	protected Map<String, MessageBroker> brokers = new HashMap<String, MessageBroker>();
	protected String hostType = "";
	final public static String DEFAULT_BROKER = "DEFAULT_BROKER";

	public MessageHub(BrokerDescriptor... descs) throws MessageException {
		this("", descs);
	}

	public MessageHub(String hostType, BrokerDescriptor... descs) throws MessageException {
		for (BrokerDescriptor desc : descs) {
			if (desc != null) {
				brokers.put(desc.getId(), new MessageBroker(desc.getId(), desc.getUri(), desc.getMq(), hostType));
			}
		}
		this.hostType = hostType;
	}

	public MessageHub(String uri) throws MessageException {
		this(uri, "");
	}

	public MessageHub(String uri, String hostType) throws MessageException {
		this.hostType = hostType;
		brokers.put(DEFAULT_BROKER, new MessageBroker(DEFAULT_BROKER, uri, MqBuilder.ActiveMQ, hostType));
	}

	public String getMqUrl() {
		String url = null;
		if(brokers.containsKey(DEFAULT_BROKER)) {
			url = brokers.get(DEFAULT_BROKER).getUri();
		}else {
			url = brokers.values().iterator().next().getUri();
		}
		return url;
	}
	
	public String getHostType() {
		return hostType;
	}

	public void addBroker(String brokerName, String uri, MqBuilder mft) throws MessageException {
		brokers.put(brokerName, new MessageBroker(brokerName, uri, mft, hostType));
	}

	public MessageBroker getBroker(String brokerName) {
		if (brokers.containsKey(brokerName))
			return brokers.get(brokerName);
		else if (brokers.containsKey(DEFAULT_BROKER))
			return brokers.get(DEFAULT_BROKER);
		return null;
	}

	public Pipe getPipe(String pipeName) {
		Pipe pipe = null;
		for (MessageBroker broker : brokers.values()) {
			pipe = broker.getPipe(pipeName);
			if (pipe != null)
				return pipe;
		}
		return pipe;
	}

	/**
	 * Create a simple Message instance with Constants.MSG_HEAD_FROM String
	 * properties.
	 * 
	 * @param hostType
	 *            Constants.MSG_TARGET_CLIENT or Constants.MSG_TARGET_AGENT
	 * @return
	 * @throws MessageException
	 */
	public Message createMessage(String brokerName, String hostType) throws MessageException {
		try {
			Message msg = this.getBroker(brokerName).getSession().createMessage();
			setProperty(msg, Constants.MSG_HEAD_FROM, hostType + CommonUtils.getHostName());
			return msg;
		} catch (JMSException e) {
			this.getBroker(brokerName).onException(e);
			throw new MessageException(e);
		}
	}

	/**
	 * Create a simple Message instance without any predefined properties.
	 * 
	 * @return
	 * @throws MessageException
	 */
	public Message createMessage(String brokerName) throws MessageException {
		try {
			Message msg = this.getBroker(brokerName).getSession().createMessage();
			if (hostType != null && !hostType.trim().isEmpty()) {
				setProperty(msg, Constants.MSG_HEAD_FROM, hostType + CommonUtils.getHostName());
			}
			return msg;
		} catch (JMSException e) {
			this.getBroker(brokerName).onException(e);
			throw new MessageException("Create Messge met exception.", e);
		}
	}

	/**
	 * Get Property from a message. The return value Type depends on the given
	 * default value Class type.
	 * 
	 * @param message
	 *            message to be fetched.
	 * @param propertyName
	 *            property name.
	 * @param defaultValue
	 *            the default value, if left null, the fetch object class will
	 *            be taken as Object.
	 * @return
	 * @throws MessageException
	 */
	@SuppressWarnings("unchecked")
	public static <V> V getProperty(Message message, String propertyName, V defaultValue) throws MessageException {
		if (propertyName == null)
			throw new MessageException("Given PropertyName is NULL.");
		if (defaultValue == null)
			defaultValue = (V) new Object();
		try {
			if (message.propertyExists(propertyName))
				switch (defaultValue.getClass().getSimpleName()) {
				case "String":
					return (V) message.getStringProperty(propertyName);
				case "Integer":
					return (V) new Integer(message.getIntProperty(propertyName));
				case "Long":
					return (V) new Long(message.getLongProperty(propertyName));
				case "Boolean":
					return (V) new Boolean(message.getBooleanProperty(propertyName));
				case "Float":
					return (V) new Float(message.getFloatProperty(propertyName));
				case "Double":
					return (V) new Double(message.getDoubleProperty(propertyName));
				case "Byte":
					return (V) new Byte(message.getByteProperty(propertyName));
				case "Short":
					return (V) new Short(message.getShortProperty(propertyName));
				default:
					return (V) message.getObjectProperty(propertyName);
				}
			else {
				return defaultValue;
			}
		} catch (Exception ex) {
			throw new MessageException("Get Property[" + propertyName + "] as "
					+ defaultValue.getClass().getSimpleName() + " from message failed.Default value:" + defaultValue,
					ex);
		}
	}

	/**
	 * Get Property from a message. The return value Type depends on the given
	 * Class type.
	 * 
	 * @param message
	 *            message message to be fetched.
	 * @param propertyName
	 *            property name.
	 * @param clazz
	 *            Give fetch property Class type.
	 * @return
	 * @throws MessageException
	 */
	@SuppressWarnings("unchecked")
	public static <V> V getProperty(Message message, String propertyName, Class<V> clazz) throws MessageException {
		if (propertyName == null)
			throw new MessageException("Given PropertyName is NULL.");
		if (clazz == null)
			clazz = (Class<V>) Object.class;
		try {
			if (message.propertyExists(propertyName))
				switch (clazz.getSimpleName()) {
				case "String":
					return (V) message.getStringProperty(propertyName);
				case "Integer":
					return (V) new Integer(message.getIntProperty(propertyName));
				case "Long":
					return (V) new Long(message.getLongProperty(propertyName));
				case "Boolean":
					return (V) new Boolean(message.getBooleanProperty(propertyName));
				case "Float":
					return (V) new Float(message.getFloatProperty(propertyName));
				case "Double":
					return (V) new Double(message.getDoubleProperty(propertyName));
				case "Byte":
					return (V) new Byte(message.getByteProperty(propertyName));
				case "Short":
					return (V) new Short(message.getShortProperty(propertyName));
				default:
					return (V) message.getObjectProperty(propertyName);
				}
		} catch (Exception ex) {
			throw new MessageException("Get Property[" + propertyName + "] as " + clazz.getSimpleName()
					+ " from message failed.", ex);
		}
		return (V) null;
	}

	/**
	 * Set property value to assigned message. Depends on what value given,
	 * invoke different api for message.
	 * 
	 * @param key
	 *            Property Name
	 * @param value
	 *            Property Value
	 */
	public static <V> void setProperty(Message message, String key, V value) throws MessageException {
		if (key == null)
			throw new MessageException("Given PropertyName is NULL.");
		if (value == null)
			return;
		try {
			switch (value.getClass().getSimpleName()) {
			case "String":
				message.setStringProperty(key, (String) value);
				break;
			case "Integer":
				message.setIntProperty(key, (Integer) value);
				break;
			case "Long":
				message.setLongProperty(key, (Long) value);
				break;
			case "Boolean":
				message.setBooleanProperty(key, (Boolean) value);
				break;
			case "Float":
				message.setFloatProperty(key, (Float) value);
				break;
			case "Double":
				message.setDoubleProperty(key, (Double) value);
				break;
			case "Byte":
				message.setByteProperty(key, (Byte) value);
				break;
			case "Short":
				message.setShortProperty(key, (Short) value);
				break;
			default:
				message.setObjectProperty(key, value);
			}
		} catch (Exception ex) {
			throw new MessageException("Set Message property failed. Name:" + key + ", value:" + value, ex);
		}
	}

	/**
	 * Bind handler to special Queue or Topic on special Broker.
	 * 
	 * @param brokerName
	 * @param type
	 * @param name
	 * @param messageSelector
	 * @param listener
	 * @param filters
	 * @return
	 * @throws MessageException
	 */
	public Pipe bindHandlers(String brokerName, Type type, String name, String messageSelector,
			MessageListener listener, MessageFilter... filters) throws MessageException {
		return bindHandlers(brokerName, type, name, messageSelector, listener, null, filters);
	}

	/**
	 * Bind handler to special Queue or Topic on special Broker, with output
	 * supported.
	 * 
	 * @param brokerName
	 * @param type
	 * @param name
	 * @param messageSelector
	 * @param listener
	 * @param tracker
	 * @param filters
	 * @return
	 * @throws MessageException
	 */
	public Pipe bindHandlers(String brokerName, Type type, String name, String messageSelector,
			MessageListener listener, OutputStream tracker, MessageFilter... filters) throws MessageException {
		return getBroker(brokerName).bindHandlers(type, name, messageSelector, listener, tracker, filters);
	}

	/**
	 * Dispose the whole MessageHub. Remove and clean all the brokers and
	 * related resources.
	 */
	public void dispose() {
		for (MessageBroker broker : brokers.values()) {
			broker.dispose();
		}
		this.brokers.clear();
	}

	/**
	 * Send Message to assigned pipe.
	 * 
	 * @param brokerName
	 * @param pipeName
	 * @param content
	 * @param properties
	 */
	public void send(String brokerName, String pipeName, String content, HashMap<String, Object> properties)
			throws MessageException {
		try {
			MessageBroker broker = getBroker(brokerName);
			if (broker != null) {
				Pipe pipe = broker.getPipe(pipeName);
				if (pipe != null) {
					Message msg = null;
					if (content != null) {
						msg = broker.getSession().createTextMessage();
						((TextMessage) msg).setText(content);
					} else {
						msg = createMessage(brokerName);
					}
					if (properties != null)
						for (String key : properties.keySet())
							setProperty(msg, key, properties.get(key));
					pipe.send(msg);
				}
			}
		} catch (Exception ex) {
			throw new MessageException("Send message fail,broker=" + brokerName + ",pipe=" + pipeName + ",content="
					+ content + ",properties=" + CommonUtils.toJson(properties), ex);
		}
	}

	/**
	 * Send message to assigned pipe, will automatically find all the broker
	 * which own the pipes with same pipeName.
	 * 
	 * @param pipeName
	 * @param content
	 * @param properties
	 * @throws MessageException
	 */
	public void send(String pipeName, String content, HashMap<String, Object> properties) throws MessageException {
		try {
			Pipe pipe = null;
			for (MessageBroker broker : brokers.values()) {
				pipe = broker.getPipe(pipeName);
				if (pipe != null)
					send(broker.getId(), pipeName, content, properties);
			}
		} catch (Exception ex) {
			throw new MessageException("Send message fail,pipe=" + pipeName + ",content=" + content + ",properties="
					+ CommonUtils.toJson(properties), ex);
		}
	}

	public static String printMessage(Message message) {
		StringBuilder sb = new StringBuilder(50);
		try {
			sb.append("Message {  ").append("\n");
			sb.append("  JMSMessageId:").append(message.getJMSMessageID()).append("\n");
			sb.append("  JMSCorrelationID:").append(message.getJMSCorrelationID()).append("\n");
			sb.append("  JMSExpiration:").append(message.getJMSExpiration()).append("\n");
			sb.append("  JMSReplyTo:").append(message.getJMSReplyTo()).append("\n");
			sb.append("  JMSTimestamp:").append(message.getJMSTimestamp()).append("\n");
			sb.append("  JMSType:").append(message.getJMSType()).append("\n");
			sb.append("  JMSRedelivered:").append(message.getJMSRedelivered()).append("\n");
			sb.append("  JMSDeliveryMode:").append(message.getJMSDeliveryMode()).append("\n");
			sb.append("  JMSDestination:").append(message.getJMSDestination()).append("\n");
			sb.append("  JMSPriority:").append(message.getJMSPriority()).append("\n");

			@SuppressWarnings("unchecked")
			Enumeration<String> eu = message.getPropertyNames();
			while (eu.hasMoreElements()) {
				String name = eu.nextElement();
				sb.append("  ").append(name).append(":").append(getProperty(message, name, Object.class)).append("\n");
			}
			sb.append("} ");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return sb.toString();
	}

	public static void printMessage(Message message, LogConnector log) {
		try {
			log.info("Message {  ");
			log.info("  JMSMessageId:" + message.getJMSMessageID());
			log.info("  JMSCorrelationID:" + message.getJMSCorrelationID());
			log.info("  JMSExpiration:" + message.getJMSExpiration());
			log.info("  JMSReplyTo:" + message.getJMSReplyTo());
			log.info("  JMSTimestamp:" + message.getJMSTimestamp());
			log.info("  JMSType:" + message.getJMSType());
			log.info("  JMSRedelivered:" + message.getJMSRedelivered());
			log.info("  JMSDeliveryMode:" + message.getJMSDeliveryMode());
			log.info("  JMSDestination:" + message.getJMSDestination());
			log.info("  JMSPriority:" + message.getJMSPriority());

			@SuppressWarnings("unchecked")
			Enumeration<String> eu = message.getPropertyNames();
			while (eu.hasMoreElements()) {
				String name = eu.nextElement();
				log.info("  " + name + ":" + getProperty(message, name, Object.class));
			}
			log.info("} ");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void printMessage(Message message, OutputStream os) {
		try {
			os.write(printMessage(message).getBytes("UTF-8"));
		} catch (Exception e) {
			e.printStackTrace(new PrintStream(os));
		}
	}

	public static void printMessage(Message message, Logger log) {
		try {
			log.info("Message {  ");
			log.info("  JMSMessageId:" + message.getJMSMessageID());
			log.info("  JMSCorrelationID:" + message.getJMSCorrelationID());
			log.info("  JMSExpiration:" + message.getJMSExpiration());
			log.info("  JMSReplyTo:" + message.getJMSReplyTo());
			log.info("  JMSTimestamp:" + message.getJMSTimestamp());
			log.info("  JMSType:" + message.getJMSType());
			log.info("  JMSRedelivered:" + message.getJMSRedelivered());
			log.info("  JMSDeliveryMode:" + message.getJMSDeliveryMode());
			log.info("  JMSDestination:" + message.getJMSDestination());
			log.info("  JMSPriority:" + message.getJMSPriority());

			@SuppressWarnings("unchecked")
			Enumeration<String> eu = message.getPropertyNames();
			while (eu.hasMoreElements()) {
				String name = eu.nextElement();
				log.info("  " + name + ":" + getProperty(message, name, Object.class));
			}
			log.info("} ");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
