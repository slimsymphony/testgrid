package frank.incubator.testgrid.common.file;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * Very simple implementation of FileTransferTarget via directly socket
 * transferring.
 * 
 * @author Wang Frank
 *
 */
public final class DirectSocketTransferTarget extends Thread implements FileTransferTarget {

	private String host;
	private int port;
	private FileTransferMode mode;
	private LogConnector log;
	private boolean running = true;
	private ServerSocket server;
	private String token;
	private File localDestDir;
	private Map<String, Long> fileList;

	public DirectSocketTransferTarget(String remoteHost, int remotePort, OutputStream out) {
		this.setName("DirectSocketTransferTarget");
		this.host = remoteHost;
		this.port = remotePort;
		log = LogUtils.get("FileTransferTarget", out);
		if (host == null) {
			this.mode = FileTransferMode.TARGET_HOST;
			this.start();
		} else
			this.mode = FileTransferMode.SOURCE_HOST;
	}

	public int getPort() {
		return port;
	}

	@Override
	public void run() {
		try {
			switch (mode) {
				case TARGET_HOST:
					server = new ServerSocket(port);
					log.info("Start waiting for incoming request to push files.");
					while (running) {
						Socket socket = server.accept();
						log.info("Incoming request from:" + socket.getRemoteSocketAddress());
						handle(socket);
					}
					break;
				case SOURCE_HOST:
					// do nothing.
				default:
					break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void dispose() {
		running = false;
		CommonUtils.closeQuietly(server);
	}

	private void handle(Socket socket) {
		DataInputStream in = null;
		DataOutputStream os = null;
		OutputStream fos = null;
		try {
			in = new DataInputStream(socket.getInputStream());
			String incomingToken = in.readUTF();
			log.info("Receiving an incoming token:" + incomingToken);
			if (incomingToken.equals(token)) { // valid request.
				os = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
				os.writeInt(Constants.VALIDATION_SUCC);
				os.flush();
				log.info("validate succ");
				while (true) {
					String request = in.readUTF();
					if (!request.equals(Constants.TRANSFER_ENDED)) {
						if (fileList.containsKey(request)) {
							long size = fileList.get(request);
							log.info("Got request to push file:" + request);
							os.writeInt(Constants.RECEIVE_READY);
							os.flush();
							try {
								File dest = new File(localDestDir, request);
								if (dest.exists()) {
									dest.delete();
								}
								dest.createNewFile();

								fos = new FileOutputStream(dest);
								try {
									IOUtils.copyLarge(in, fos, 0, size);
								} catch (NoSuchMethodError e) {
									log.warn("Dependency library common-io was too old. No copyLarge method found.");
									byte[] buffer = new byte[4096];
									int read = 0;
									while (read < size) {
										int readlen = 4096;
										if ((size - read) < 4096) {
											readlen = (int) (size - read);
										}
										in.read(buffer, 0, readlen);
										fos.write(buffer, 0, readlen);
										read += readlen;
									}
								}
								fos.flush();
							} catch (IOException e) {
								throw new RuntimeException("File[" + request + "] transfer met exception.", e);
							} finally {
								CommonUtils.closeQuietly(fos);
							}
						} else {
							log.error("Invalid incoming file received. Won't receive the file out of receiving list: " + request);
						}
						log.info("Finished receiving file:" + request);
						os.writeInt(Constants.RECEIVE_FINISHED);
						os.flush();
					} else {
						log.info("All file transfer finished.");
						break;
					}
				}
			} else {
				os = new DataOutputStream(socket.getOutputStream());
				os.writeInt(Constants.VALIDATION_FAIL);
				os.flush();
				log.info("validate token failed. current token:{}, incoming token:{}", token, incomingToken);
			}

		} catch (IOException e) {

		} finally {
			CommonUtils.closeQuietly(os);
			CommonUtils.closeQuietly(in);
			CommonUtils.closeQuietly(socket);
		}
	}

	@SuppressWarnings("resource")
	@Override
	public Collection<File> fetch(String token, Map<String, Long> fileList, File localDestDir) throws Exception {
		log.info("Fetching started! for token:{}", token);
		if (!localDestDir.exists()) {
			localDestDir.mkdirs();
		}
		List<File> files = new ArrayList<File>();
		Socket socket = new Socket();
		int countDown = 3;
		while (countDown > 0) {
			try {
				socket.connect(new InetSocketAddress(host, port));
				break;
			} catch (Exception ex) {
				log.error("Connect to Dest Server[" + host + ":" + port + "] failed. token:" + token, ex);
				countDown--;
				TimeUnit.SECONDS.sleep(5 - countDown);
				if (countDown == 0) {
					CommonUtils.closeQuietly(socket);
					throw new Exception("Can't connect to Destination Server for 3 times. token:" + token, ex);
				}
			}
		}
		DataInputStream in = null;
		DataOutputStream os = null;
		OutputStream fos = null;
		try {
			os = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			os.writeUTF(token);
			// os.writeChars(token);
			os.flush();
			int response = in.readInt();
			if (response == Constants.VALIDATION_SUCC) {
				for (String f : fileList.keySet()) {
					long size = fileList.get(f);
					try {
						os.writeUTF(f);
						os.flush();
						log.info("[{}]Target: requesting:{}", token, f);
						File file = new File(localDestDir, f);
						fos = new BufferedOutputStream(new FileOutputStream(file));
						try {
							IOUtils.copyLarge(in, fos, 0, size);
						} catch (NoSuchMethodError e) {
							log.warn("[{}]Dependency library common-io was too old. No copyLarge method found.", token);
							byte[] buffer = new byte[4096];
							int read = 0;
							while (read < size) {
								int readlen = 4096;
								if ((size - read) < 4096) {
									readlen = (int) (size - read);
								}
								in.read(buffer, 0, readlen);
								fos.write(buffer, 0, readlen);
								read += readlen;
							}
						}
						fos.flush();
						files.add(file);
						log.info("[{}]Target: finished receiving:{}", token, f);
					} finally {
						CommonUtils.closeQuietly(fos);
					}
				}
				os.writeUTF(Constants.TRANSFER_ENDED);
				os.flush();
			} else {
				throw new Exception("Invalid token for fetching files from " + host + ":" + port + ", token:" + token + ". VALIDATION_RESPONSE Code:"
						+ response);
			}
		} finally {
			CommonUtils.closeQuietly(os);
			CommonUtils.closeQuietly(in);
			CommonUtils.closeQuietly(socket);
		}
		dispose();
		return files;
	}

	@Override
	public Collection<File> accept(String token, Map<String, Long> fileList, File localDestDir) throws Exception {
		this.token = token;
		this.fileList = fileList;
		Map<File, Boolean> files = new HashMap<File, Boolean>();
		this.localDestDir = localDestDir;
		// this.start();
		while (running) {
			TimeUnit.SECONDS.sleep(2);
		}
		if (!localDestDir.exists()) {
			localDestDir.mkdirs();
		}
		for (String fn : fileList.keySet()) {
			long size = fileList.get(fn);
			File[] fs = localDestDir.listFiles();
			File lf = new File(localDestDir, fn);
			files.put(lf, false);
			for (File f : fs) {
				if (f.getName().equals(fn) && f.length() == size) {
					files.put(lf, true);
					break;
				}
			}
		}

		if (files.values().contains(false)) {
			for (File f : files.keySet()) {
				if (!files.get(f))
					throw new RuntimeException("File:" + f.getName() + " didn't transfer success!");
			}
		}
		Collection<File> ret = new HashSet<File>(files.keySet());
		files.clear();
		dispose();
		return ret;
	}

	@Override
	public FileTransferMode getTransferMode() {
		return mode;
	}
}
