package frank.incubator.testgrid.common.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * Use HTTP to publish or push File to target URL.
 * 
 * @author Wang Frank
 * 
 */
public final class HttpFileTransferSource implements FileTransferSource {
	private String url;
	private FileTransferMode mode;
	private LogConnector log;
	private CloseableHttpClient client;

	public HttpFileTransferSource(FileTransferMode mode, String url, OutputStream out) {
		this.mode = mode;
		this.url = url;
		log = LogUtils.get("FileTransferSource", out);
		if (url == null && mode != FileTransferMode.SOURCE_HOST)
			throw new RuntimeException("FileTransferMode[" + mode + "] need provide a HttpFileService URL");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferResource#dispose()
	 */
	@Override
	public void dispose() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferResource#getTransferMode
	 * ()
	 */
	@Override
	public FileTransferMode getTransferMode() {
		return mode;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.common.file.FileTransferSource#publish(java
	 * .lang.String, java.util.Collection)
	 */
	@Override
	public void publish(String token, Collection<File> fileList) throws Exception {
		File f = validate(fileList);
		if (f != null)
			throw new Exception("Validate File[ " + f.getAbsolutePath() + "] failed.");
	}

	private File validate(Collection<File> fileList) {
		for (File f : fileList) {
			if (!f.exists() || !f.isFile()) {
				log.error("File[" + f.getAbsolutePath() + "] didn't exist or it's not a file. Validation failed.");
				return f;
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferSource#push(java.lang
	 * .String, java.util.Collection)
	 */
	@Override
	public void push(String token, Collection<File> fileList) throws Exception {
		File file = validate(fileList);
		if (file != null)
			throw new Exception("Validate File[ " + file.getAbsolutePath() + "] failed.");
		CloseableHttpResponse response = null;
		InputStream in = null;
		List<InputStream> ins = new ArrayList<InputStream>();
		try {
			client = HttpClientBuilder.create().build();
			HttpPost post = new HttpPost(url);
			MultipartEntityBuilder builder = MultipartEntityBuilder.create().addTextBody("token", token);
			for (File f : fileList) {
				if (!f.exists())
					throw new FileNotFoundException("File:" + f.getAbsolutePath() + " didn't exits. Stop upload.");
				in = new FileInputStream(f);
				ins.add(in);
				builder.addPart(f.getName(), new InputStreamBody(in, f.getName()));
				// builder.addBinaryBody( f.getName(), in );
			}
			HttpEntity entity = builder.build();
			post.setEntity(entity);
			response = client.execute(post);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != HttpStatus.SC_OK)
				throw new Exception("Pushing files failed. Post return code:" + statusCode);
		} finally {
			CommonUtils.closeQuietly(response);
			for (InputStream i : ins) {
				CommonUtils.closeQuietly(i);
			}
			CommonUtils.closeQuietly(client);
		}

	}
}
