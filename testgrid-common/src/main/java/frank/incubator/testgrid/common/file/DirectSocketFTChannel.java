package frank.incubator.testgrid.common.file;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collection;
import java.util.Map;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;

/**
 * @author Wang Frank
 * 
 */
public class DirectSocketFTChannel extends FileTransferChannel {

	public DirectSocketFTChannel() {
		this.id = "SOCKET";
		this.setPriority(0);
		this.getProperties().put("host", CommonUtils.getHostName());
	}

	transient private DirectSocketTransferSource dts;

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#validate()
	 */
	@Override
	public synchronized boolean validate(LogConnector log) {
		int servicePort = CommonUtils.availablePort(10000);
		dts = new DirectSocketTransferSource(null, servicePort, null);
		dts.start();
		this.setProperty("servicePort", servicePort);
		if (log != null)
			log.info("Begin to start service Port:{}", servicePort);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#apply()
	 */
	@Override
	public boolean apply(LogConnector log) {
		Socket socket = null;
		try {
			if (!this.getProperties().containsKey("host")) {
				if (log != null)
					log.info("Properties missing Host info.");
				this.getProperties().put("host", CommonUtils.getHostName());
			}
			if (!this.getProperties().containsKey("servicePort")) {
				if (log != null)
					log.info("Properties missing servicePort");
				return false;
			}
			socket = new Socket();
			socket.connect(new InetSocketAddress(this.getProperty("host", "localhost"), this.getProperty("servicePort", Integer.class)));
			if (log != null)
				log.info("success Apply Socket channel from {} to {}:{}", CommonUtils.getHostName(), this.getProperty("host", "localhost"),
						this.getProperty("servicePort", Integer.class));
		} catch (Exception ex) {
			return false;
		} finally {
			CommonUtils.closeQuietly(socket);
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#send(java.
	 * lang.String, java.util.Collection,
	 * frank.incubator.testgrid.common.log.LogConnector)
	 */
	@Override
	public boolean send(String token, Collection<File> fileList, LogConnector log) {
		if (dts == null) {
			if (log != null)
				log.error("DirectSocketTransferSource is NULL, can't continue sending files.");
			return false;
		}
		try {
			dts.publish(token, fileList);
		} catch (Exception e) {
			if (log != null)
				log.error("Send Files via DirectSocket failed. Token=" + token, e);
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferChannel#receive(java
	 * .lang.String, java.util.Map, java.io.File,
	 * frank.incubator.testgrid.common.log.LogConnector)
	 */
	@Override
	public boolean receive(String token, Map<String, Long> fileList, File localDestDir, LogConnector log) {
		DirectSocketTransferTarget dtt = new DirectSocketTransferTarget(this.getProperty("host", String.class), this.getProperty("servicePort", Integer.class),
				log.getOs());
		try {
			dtt.fetch(token, fileList, localDestDir);
		} catch (Exception e) {
			if (log != null)
				log.error("Receiving files via DirectSocket failed.Token=" + token, e);
			return false;
		}
		return true;
	}

	@Override
	public void dispose() {
		if (this.dts != null)
			dts.dispose();
	}

}
