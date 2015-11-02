package frank.incubator.testgrid.common;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;

import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TextMessage;

import org.apache.commons.io.IOUtils;

import com.google.gson.reflect.TypeToken;
import frank.incubator.testgrid.common.file.FileTransferChannel;
import frank.incubator.testgrid.common.file.FileTransferDescriptor;
import frank.incubator.testgrid.common.file.FileTransferService;
import frank.incubator.testgrid.common.file.FileTransferTask;
import frank.incubator.testgrid.common.file.FtpFTChannel;
import frank.incubator.testgrid.common.file.NfsFTChannel;
import frank.incubator.testgrid.common.message.BrokerDescriptor;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.message.MqBuilder;

/**
 * @author Wang Frank
 *
 */
public class FtServiceSender extends MessageListenerAdapter {

	static class SimpleMessageListener extends MessageListenerAdapter {
		private FileTransferService fts;

		public SimpleMessageListener(FileTransferService fts) {
			this.fts = fts;
		}

		@Override
		public void onMessage(Message message) {
			super.onMessage(message);
			try {
				MessageHub.printMessage(message, System.out);
				if (message instanceof TextMessage) {
					System.out.println("Text Content:" + ((TextMessage) message).getText());
				}
				fts.dispose();
				fts.getHub().dispose();
				// System.exit( 0 );
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public FtServiceSender() {
		super("FtServiceSender", System.out);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		Collection<File> files = new ArrayList<File>();
		File source = new File("c:/temp/test");
		for (File f : source.listFiles()) {
			files.add(f);
		}
		FileTransferTask ftTask = new FileTransferTask(Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName(), Constants.MSG_TARGET_AGENT
				+ CommonUtils.getHostName(), "task1", "test1", files);
		InputStream in = CommonUtils.loadResources("../testgrid-agent/" + Constants.MQ_CONFIG_FILE, true);
		StringWriter sw = new StringWriter();
		IOUtils.copy(in, sw);
		BrokerDescriptor[] bds = CommonUtils.fromJson(sw.toString(), new TypeToken<BrokerDescriptor[]>() {
		}.getType());
		CommonUtils.closeQuietly(in);
		MessageHub hub = new MessageHub(Constants.MSG_TARGET_CLIENT, bds);
		FileTransferDescriptor descriptor = new FileTransferDescriptor();
		FileTransferChannel channel = new NfsFTChannel("c:/temp/sender/", "c:/temp/receiver1/");
		descriptor.addChannel(channel);
		channel = new FtpFTChannel("10.220.120.16", "ftp", "ftp", 21);
		descriptor.addChannel(channel);
		FileTransferService fts = new FileTransferService(hub, descriptor, new File("c:/temp/workspace/"), System.out);
		hub.bindHandlers(Constants.BROKER_FT, Queue.class, Constants.HUB_FILE_TRANSFER, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_CLIENT
				+ CommonUtils.getHostName() + "' AND " + Constants.MSG_HEAD_TASKID + "=" + "'" + ftTask.getTaskId() + "'", fts, System.out);
		hub.bindHandlers(Constants.BROKER_TASK, Queue.class, Constants.HUB_TASK_COMMUNICATION, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_CLIENT
				+ CommonUtils.getHostName() + "'", new SimpleMessageListener(fts), System.out);
		fts.sendTo(ftTask);
	}

	protected void handleMessage(Message message) {
		super.handleMessage(message);
	}

}
