package frank.incubator.testgrid.common.file;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import org.apache.http.HttpStatus;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;

/**
 * HTTP Get file transfer Channel.
 * 
 * @author Wang Frank
 *
 */
public class HttpGetFTChannel extends FileTransferChannel {

	public HttpGetFTChannel() {
		this.id = "HTTPGET";
		this.setPriority(2);
	}

	public HttpGetFTChannel(String getBase) {
		this.id = "HTTPGET";
		this.setPriority(2);
		properties.put("baseUrl", getBase);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#validate()
	 */
	@Override
	public boolean validate(LogConnector log) {
		if (log != null)
			log.info("Begin to validate HttpGetChannel.props:{}", CommonUtils.toJson(this.getProperties()));
		int statusCode = CommonUtils.httpGet(getProperty("baseUrl", ""), (LogConnector) null);
		if (log != null)
			log.info("valid httpgetChannel return code:{}", statusCode);
		return statusCode == HttpStatus.SC_OK;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#apply()
	 */
	@Override
	public boolean apply(LogConnector log) {
		return validate(log);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferChannel#send(java.lang
	 * .String, java.util.Collection)
	 */
	@Override
	public boolean send(String token, Collection<File> fileList, LogConnector log) {
		log.info("HttpGetChannel begin sending files. token=" + token);
		HttpFileTransferSource source = new HttpFileTransferSource(FileTransferMode.SOURCE_HOST, getProperty("baseUrl", ""), log.getOs());
		try {
			source.publish(token, fileList);
		} catch (Exception e) {
			if (log != null)
				log.error("Sending Files via HttpGet failed. token=" + token, e);
			return false;
		}
		if (log != null)
			log.info("HttpGetChannel finished sending files. token=" + token);
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
			log.info("HttpGetChannel begin receiving files. token=" + token);
		HttpFileTransferTarget target = new HttpFileTransferTarget(FileTransferMode.SOURCE_HOST, getProperty("baseUrl", ""), null, log.getOs());
		try {
			target.fetch(token, fileList, localDestDir);
		} catch (Exception e) {
			if (log != null)
				log.error("Receiving Files via HttpGet failed. token=" + token, e);
			return false;
		}
		if (log != null)
			log.info("HttpGetChannel finished receiving files. token=" + token);
		return true;
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub
	}

}
