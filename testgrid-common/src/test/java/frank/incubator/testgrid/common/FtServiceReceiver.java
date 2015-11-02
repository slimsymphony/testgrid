package frank.incubator.testgrid.common;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;

import javax.jms.Queue;

import org.apache.commons.io.IOUtils;

import com.google.gson.reflect.TypeToken;
import frank.incubator.testgrid.common.file.FileTransferChannel;
import frank.incubator.testgrid.common.file.FileTransferDescriptor;
import frank.incubator.testgrid.common.file.FileTransferService;
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
public class FtServiceReceiver extends MessageListenerAdapter {

	public FtServiceReceiver() {
		super("FtServiceReceiver", System.out);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		InputStream in = CommonUtils.loadResources("../testgrid-agent/" + Constants.MQ_CONFIG_FILE, true);
		StringWriter sw = new StringWriter();
		IOUtils.copy(in, sw);
		BrokerDescriptor[] bds = CommonUtils.fromJson(sw.toString(), new TypeToken<BrokerDescriptor[]>() {
		}.getType());
		CommonUtils.closeQuietly(in);
		MessageHub hub = new MessageHub(Constants.MSG_TARGET_AGENT, bds);
		FileTransferDescriptor descriptor = new FileTransferDescriptor();
		FileTransferChannel channel = new NfsFTChannel("c:/temp/receiver/", "c:/temp/sender/");
		descriptor.addChannel(channel);
		channel = new FtpFTChannel("10.220.120.16", "ftp", "ftp", 21);
		descriptor.addChannel(channel);
		FileTransferService fts = new FileTransferService(hub, descriptor, new File("c:/temp/workspace/"), System.out);
		hub.bindHandlers(Constants.BROKER_FT, Queue.class, Constants.HUB_FILE_TRANSFER, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_AGENT
				+ CommonUtils.getHostName() + "'", fts, System.out);
		hub.bindHandlers(Constants.BROKER_TASK, Queue.class, Constants.HUB_TASK_COMMUNICATION, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_AGENT
				+ CommonUtils.getHostName() + "'", fts, System.out);

		Thread t = new Thread() {
			public void run() {
				while (true) {
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		t.start();
	}

}
