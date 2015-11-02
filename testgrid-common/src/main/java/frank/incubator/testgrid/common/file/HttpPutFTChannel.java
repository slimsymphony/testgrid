package frank.incubator.testgrid.common.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;

/**
 * HTTP PUT file transfer Channel.
 * 
 * @author Wang Frank
 *
 */
public class HttpPutFTChannel extends FileTransferChannel {

	public HttpPutFTChannel() {
		this.id = "HTTPPUT";
		this.setPriority(4);
	}

	public HttpPutFTChannel(String postUrl, File repoPath) {
		this.id = "HTTPPUT";
		this.setPriority(4);
		properties.put("valid", true);
		properties.put("postUrl", postUrl);
		properties.put("repoPath", File.class);
	}

	/**
	 * Take it always true , cos only agent side support it. And this validation
	 * should be set explicitly depends on agent configuration.
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#validate()
	 */
	@Override
	public boolean validate(LogConnector log) {
		return getProperty("valid", false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferChannel#apply()
	 */
	@Override
	public boolean apply(LogConnector log) {
		boolean result = false;
		CloseableHttpResponse response = null;
		InputStream in = null;
		CloseableHttpClient client = null;
		File f = new File("testupload" + CommonUtils.generateToken(3) + ".tmp");
		try {
			if (!f.exists())
				f.createNewFile();
			client = HttpClientBuilder.create().build();
			HttpPost post = new HttpPost(getProperty("postUrl", ""));
			String token = "testupload";
			MultipartEntityBuilder builder = MultipartEntityBuilder.create().addTextBody("token", token);
			in = new FileInputStream(f);
			builder.addPart(f.getName(), new InputStreamBody(in, f.getName()));
			HttpEntity entity = builder.build();
			post.setEntity(entity);
			response = client.execute(post);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == HttpStatus.SC_OK)
				result = true;
			else {
				if (log != null)
					log.error("Apply Pushing files to " + getProperty("postUrl", "") + " failed. Post return code:" + statusCode);
				else
					System.err.println("Apply Pushing files to " + getProperty("postUrl", "") + " failed. Post return code:" + statusCode);
			}
		} catch (Exception ex) {
			if (log != null)
				log.error("Apply HttpPutChannel failed.", ex);
		} finally {
			CommonUtils.closeQuietly(response);
			CommonUtils.closeQuietly(in);
			CommonUtils.closeQuietly(client);
			if (f != null)
				f.delete();
		}
		return result;
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
		log.info("HttpPutChannel begin sending files. token=" + token);
		HttpFileTransferSource source = new HttpFileTransferSource(FileTransferMode.TARGET_HOST, getProperty("postUrl", ""), log.getOs());
		try {
			source.push(token, fileList);
		} catch (Exception e) {
			log.error("Sending Files via HttpGet failed. token=" + token, e);
			return false;
		}
		log.info("HttpPutChannel finished receiving files. token=" + token);
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
		log.info("HttpPutChannel begin receiving files. token=" + token);
		HttpFileTransferTarget target = new HttpFileTransferTarget(FileTransferMode.TARGET_HOST, getProperty("postUrl", ""),
				getProperty("repoPath", File.class), log.getOs());
		try {
			target.accept(token, fileList, localDestDir);
		} catch (Exception e) {
			log.error("Receiving Files via HttpGet failed. token=" + token, e);
			return false;
		}
		log.info("HttpPutChannel finished receiving files. token=" + token);
		return true;
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub
	}

}
