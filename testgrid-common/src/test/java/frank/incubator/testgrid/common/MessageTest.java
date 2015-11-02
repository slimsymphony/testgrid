package frank.incubator.testgrid.common;

import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;

import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TextMessage;
import javax.jms.Topic;

import com.google.gson.reflect.TypeToken;
import frank.incubator.testgrid.common.message.BrokerDescriptor;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;

/**
 * @author Wang Frank
 *
 */
public class MessageTest {

	static class SimpleMessageListener extends MessageListenerAdapter {

		public SimpleMessageListener(int id) {
			super();
			this.id = id;
		}

		private int id;

		@Override
		public void onMessage(Message message) {
			super.onMessage(message);
			try {
				MessageHub.printMessage(message, System.out);
				if (message instanceof TextMessage) {
					System.out.println("Real handler[" + id + "]:" + ((TextMessage) message).getText());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void amqTestQueue() throws Exception {
		String json = "[{'id':'localTcp','uri':'tcp://localhost:61616','mq':'ActiveMQ'},{'id':'localUdp','uri':'udp://localhost:45732','mq':'ActiveMQ'}]";
		BrokerDescriptor[] bds = CommonUtils.fromJson(json, new TypeToken<BrokerDescriptor[]>() {
		}.getType());
		MessageHub mh = new MessageHub(bds);
		mh.bindHandlers("localTcp", Queue.class, "testqueue", "receiver='bbb'", new SimpleMessageListener(1), (OutputStream) null);
		mh.bindHandlers("localTcp", Queue.class, "testqueue", "receiver='aaa'", new SimpleMessageListener(2), (OutputStream) null);
		mh.bindHandlers("localUdp", Queue.class, "testqueue2", "receiver='bbb'", new SimpleMessageListener(1), (OutputStream) null);
		mh.bindHandlers("localUdp", Queue.class, "testqueue2", "receiver='aaa'", new SimpleMessageListener(2), (OutputStream) null);
		Date t = new Date();
		for (int i = 0; i < 5; i++) {
			mh.send("localTcp",
					"testqueue",
					"Hello,world(" + i + ") @" + t.getYear() + "-" + t.getMonth() + "-" + t.getDate() + " " + t.getHours() + ":" + t.getMinutes() + ":"
							+ t.getSeconds(), new HashMap<String, Object>() {
						{
							this.put("receiver", "aaa");
							this.put("int", 1);
							this.put("long", 2312321321L);
							this.put("float", 3.04f);
							this.put("double", 3493423.5454d);
							this.put("bool", true);
							this.put("byte", Byte.parseByte("32"));
							this.put("short", Short.parseShort("33"));
							this.put("object", new HashMap<String, String>() {
								{
									this.put("aaa", "bbb");
								}
							});
						}
					});
			mh.send("testqueue2",
					"No Hello,world(" + i + ") @" + t.getYear() + "-" + t.getMonth() + "-" + t.getDate() + " " + t.getHours() + ":" + t.getMinutes() + ":"
							+ t.getSeconds(), new HashMap<String, Object>() {
						{
							this.put("receiver", "bbb");
						}
					});
		}
		Thread.sleep(5000);
		mh.dispose();
	}

	private static void amqTestTopic() throws Exception {
		String json = "[{'id':'localTcp','uri':'tcp://localhost:61616','mq':'ActiveMQ'},{'id':'localUdp','uri':'udp://localhost:45732','mq':'ActiveMQ'}]";
		BrokerDescriptor[] bds = CommonUtils.fromJson(json, new TypeToken<BrokerDescriptor[]>() {
		}.getType());
		MessageHub mh = new MessageHub(bds);
		mh.bindHandlers("localTcp", Topic.class, "testtopic", "receiver='bbb'", new SimpleMessageListener(3), (OutputStream) null);
		mh.bindHandlers("localTcp", Topic.class, "testtopic", "receiver='aaa'", new SimpleMessageListener(4), (OutputStream) null);
		mh.bindHandlers("localTcp", Topic.class, "testtopic", "", new SimpleMessageListener(5), (OutputStream) null);
		Date t = new Date();
		for (int i = 0; i < 5; i++) {
			mh.send("testtopic", "Hello,world(" + i + ") @" + t.getYear() + "-" + t.getMonth() + "-" + t.getDate() + " " + t.getHours() + ":" + t.getMinutes()
					+ ":" + t.getSeconds(), new HashMap<String, Object>() {
				{
					this.put("receiver", "aaa");
				}
			});
			mh.send("testtopic",
					"No Hello,world(" + i + ") @" + t.getYear() + "-" + t.getMonth() + "-" + t.getDate() + " " + t.getHours() + ":" + t.getMinutes() + ":"
							+ t.getSeconds(), new HashMap<String, Object>() {
						{
							this.put("receiver", "bbb");
						}
					});
		}
		Thread.sleep(5000);
		mh.dispose();
	}

	public static void main(String[] args) throws Exception {
		// amqTestQueue();
		amqTestTopic();
	}

}
