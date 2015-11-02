package frank.incubator.testgrid.common.file;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import frank.incubator.testgrid.common.log.LogConnector;

/**
 * @author Wang Frank
 * 
 */
public class NfsFTChannel extends FileTransferChannel {

	public NfsFTChannel() {
		this.id = "NFS";
		this.setPriority(1);
	}

	public NfsFTChannel(String sourceBase, String targetBase) {
		this.id = "NFS";
		properties.put("sourceBase", sourceBase);
		properties.put("targetBase", targetBase);
		this.setPriority(1);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#validate()
	 */
	@Override
	public boolean validate(LogConnector log) {
		File f = new File(getProperty("sourceBase", "notexists"));
		if (f.exists() && f.isDirectory() && f.canRead())
			return true;
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#apply()
	 */
	@Override
	public boolean apply(LogConnector log) {
		File f = new File(getProperty("targetBase", "notexists"));
		if (f.exists() && f.isDirectory() && f.canRead())
			return true;
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#send(java.
	 * lang.String, java.util.Collection)
	 */
	@Override
	public boolean send(String token, Collection<File> fileList, LogConnector log) {
		if (log != null)
			log.info("NfsChannel begin sending files. token=" + token);
		LocalFileTransferSource source = new LocalFileTransferSource(FileTransferMode.THIRDPARTY_HOST, new File(getProperty("sourceBase", "")), new File(
				getProperty("targetBase", "")), log.getOs());
		try {
			source.publish(token, fileList);
		} catch (Exception e) {
			if (log != null)
				log.error("Sending Files via Nfs failed. token=" + token, e);
			return false;
		}
		if (log != null)
			log.info("NfsChannel finished sending files. token=" + token);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferChannel#receive(java
	 * .lang.String, java.util.Map, java.io.File)
	 */
	@Override
	public boolean receive(String token, Map<String, Long> fileList, File localDestDir, LogConnector log) {
		if (log != null)
			log.info("NfsChannel begin receiving files. token=" + token);
		LocalFileTransferTarget target = new LocalFileTransferTarget(FileTransferMode.THIRDPARTY_HOST, new File(getProperty("sourceBase", "")), new File(
				getProperty("targetBase", "")), log.getOs());
		try {
			target.fetch(token, fileList, localDestDir);
		} catch (Exception e) {
			if (log != null)
				log.error("Receiving Files via Nfs failed. token=" + token, e);
			return false;
		}
		if (log != null)
			log.info("NfsChannel finished receiving files. token=" + token);
		target.dispose();
		return true;
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub
	}

}
