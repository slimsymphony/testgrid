package frank.incubator.testgrid.common.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.fluent.Request;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * Http based File transfer target implementation.
 * 
 * @author Wang Frank
 *
 */
public final class HttpFileTransferTarget extends Thread implements FileTransferTarget {

	private String url;
	private FileTransferMode mode;
	private LogConnector log;
	private File repoPath;
	private String token;

	public HttpFileTransferTarget(FileTransferMode mode, String url, File repoPath, OutputStream out) {
		this.setName("HttpFileTransferTarget");
		this.mode = mode;
		this.url = url;
		this.repoPath = repoPath;
		log = LogUtils.get("FileTransferTarget", out);
		if (url == null && mode != FileTransferMode.TARGET_HOST) {
			throw new RuntimeException("FileTransferMode[" + mode + "] need provide a HttpFileService URL");
		}
	}

	public String getToken() {
		return token;
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
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferTarget#fecth(java.lang
	 * .String, java.util.Map, java.io.File)
	 */
	public Collection<File> fetch(String token, Map<String, Long> fileList, final File localDestDir) throws Exception {
		this.token = token;
		Collection<File> files = new ArrayList<File>();
		if (mode == FileTransferMode.TARGET_HOST) {
			return accept(token, fileList, localDestDir);
		} else {
			for (String filename : fileList.keySet()) {
				final long fl = fileList.get(filename);
				final String fn = filename;
				File f = Request.Get(url + "&token=" + token + "&filename=" + filename).execute().handleResponse(new ResponseHandler<File>() {
					@Override
					public File handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
						File file = new File(localDestDir, fn);
						FileOutputStream fs = null;
						try {
							if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
								fs = new FileOutputStream(file);
								response.getEntity().writeTo(fs);
								fs.flush();
								if (fl != file.length()) {
									throw new Exception("File[" + fn + "] length is:" + file.length() + ", not equal to expected length:" + fl);
								}
							}
						} catch (Exception ex) {
							log.error("Download file:" + fn + " got exception.", ex);
						} finally {
							CommonUtils.closeQuietly(fs);
						}
						return file;
					}
				});
				if (f != null && f.exists() && f.length() == fl) {
					files.add(f);
				} else {
					throw new Exception("File[" + fn + "] download failed.");
				}
			}
		}
		return files;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * frank.incubator.testgrid.common.file.FileTransferTarget#accept(java.lang
	 * .String, java.util.Map, java.io.File)
	 */
	@Override
	public Collection<File> accept(String token, Map<String, Long> fileList, File localDestDir) throws Exception {
		Collection<File> files = new ArrayList<File>();
		if (mode == FileTransferMode.TARGET_HOST) {
			File folder = new File(repoPath, token);
			if (!folder.exists() || !folder.isDirectory()) {
				throw new Exception("Http Transfer receive failed." + folder.getAbsolutePath() + " could not be accessed.");
			}

			File f = null;
			for (String fn : fileList.keySet()) {
				long fl = fileList.get(fn);
				f = new File(folder, fn);
				if (!f.exists() || f.length() != fl) {
					throw new Exception("Http Transfer receive failed.  Validate File:" + f.getAbsolutePath() + " not equals with assigned principle");
				}
				if (!folder.equals(localDestDir)) {
					FileUtils.copyFileToDirectory(f, localDestDir);
					f.delete();
				}
				files.add(new File(localDestDir, f.getName()));
			}
		} else {
			return fetch(token, fileList, localDestDir);
		}
		return files;
	}
}
