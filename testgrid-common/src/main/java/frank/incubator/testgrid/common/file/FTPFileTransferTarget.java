package frank.incubator.testgrid.common.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

public final class FTPFileTransferTarget implements FileTransferTarget {

	public FTPFileTransferTarget(String host, int port, String userName, String passwd, OutputStream out) {
		this.host = host;
		this.port = port;
		this.userName = userName;
		this.password = passwd;
		this.log = LogUtils.get("FileTransferTarget", out);
		ftp = new FTPClient();
		ftp.setBufferSize(8192 * 1024);
		try {
			ftp.setReceiveBufferSize(8192 * 1024);
		} catch (SocketException e) {
			log.error("Set Receive Buffer size to 1024*1024 failed.", e);
		}
	}

	private String host;
	private int port;
	private String userName;
	private String password;
	private String token;
	private FTPClient ftp;
	private LogConnector log;

	@Override
	public Collection<File> fetch(String token, Map<String, Long> fileList, File localDestDir) throws Exception {
		this.token = token;
		try {
			if (connectToServer(host, port, userName, password)) {
				ArrayList<File> files = new ArrayList<File>();
				ftp.setFileType(FTP.BINARY_FILE_TYPE);
				ftp.changeWorkingDirectory(token);
				log.info("Current directory is {}", ftp.printWorkingDirectory());
				if (!localDestDir.exists()) {
					localDestDir.mkdirs();
				}
				// start to check file size
				for (String fileName : fileList.keySet()) {
					long fileSize = fileList.get(fileName);
					File file = new File(localDestDir.getPath(), fileName);
					if (file.exists()) {
						log.warn("The file {} already exists there, deleted.", file.getAbsolutePath());
						file.delete();
						file.createNewFile();
					}
					log.info("fileName is {}", fileName);
					OutputStream output = null;
					try {
						output = new FileOutputStream(file);
						boolean result = ftp.retrieveFile(fileName, output);
						if (result && validate(file, fileName, fileSize)) {
							log.info("Succeed to download file {}", file.getName());
							files.add(file);
						} else {
							log.info("Failed to download file {}", file.getName());
							throw new Exception("Download File:" + fileName + " failed.");
						}
						output.flush();
					} finally {
						CommonUtils.closeQuietly(output);
					}
				}
				return files;
			} else {
				log.error("fetch:Connect to server failed.");
				throw new Exception("Connect to FileTransfer FTP server failed. host=" + host + ", port=" + port + ", username=" + userName + ",passwd="
						+ password);
			}
		} finally {
			ftp.disconnect();
		}
	}

	private boolean validate(File file, String fn, long size) throws IOException {
		boolean validate = false;
		if (file.getName().equals(fn) && file.length() == size)
			validate = true;
		return validate;
	}

	@Override
	public Collection<File> accept(String token, Map<String, Long> fileList, File localDestDir) throws Exception {
		return fetch(token, fileList, localDestDir);
	}

	public boolean connectToServer(String host, int port, String userName, String password) {
		boolean result = true;
		try {
			if (!ftp.isConnected()) {
				ftp.connect(host, port);
				log.info("Connected to {}:{}", host, port);
				ftp.login(userName, password);
				int reply = ftp.getReplyCode();
				log.info("Reply Code:{}", reply);
				if (!FTPReply.isPositiveCompletion(reply)) {
					ftp.disconnect();
					log.error("FTP server refused connection.");
					result = false;
				}
			}
		} catch (SocketException e) {
			result = false;
			log.error("Connect to FTP server timeout", e);
		} catch (IOException e) {
			result = false;
			log.error("Connect to FTP server failed ", e);
		}
		return result;
	}

	@Override
	public void dispose() {
		if (connectToServer(host, port, userName, password)) {
			try {
				deleteFiles(token);
			} catch (Exception e) {
				log.error("Dispose failed.", e);
			} finally {
				try {
					ftp.disconnect();
				} catch (Exception e) {
					log.error("Close ftp connection got exception.", e);
				}
			}
		}
	}

	public boolean deleteFiles(String directoyNamePath) throws Exception {
		boolean result = true;
		// ftp.changeToParentDirectory();
		String targetPath = ftp.printWorkingDirectory() + "/" + directoyNamePath;
		targetPath = targetPath.replaceAll("//", "/");
		FTPFile[] fileList = ftp.listFiles(targetPath);
		for (FTPFile file : fileList) {
			if (file.isDirectory()) {
				log.info("Delete sub directory {}", file.getName());
				String subDirectoryNamePath = directoyNamePath + "/" + file.getName();
				deleteFiles(subDirectoryNamePath);
			}
			if (file.isFile()) {
				log.info("Delete file {}", file.getName());
				try {
					ftp.deleteFile(targetPath + "/" + file.getName());
				} catch (IOException e) {
					log.error("Delete File failed from:" + directoyNamePath, e);
				}
			}
		}
		log.info("Delete empty directory after {}", directoyNamePath);
		ftp.removeDirectory(targetPath);
		return result;
	}

	@Override
	public FileTransferMode getTransferMode() {
		return FileTransferMode.THIRDPARTY_HOST;
	}

}
