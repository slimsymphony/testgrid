package frank.incubator.testgrid.common.file;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * Very simple implementation of FileTransferSource via directly socket
 * transferring.
 * 
 * @author Wang Frank
 * 
 */
public final class DirectSocketTransferSource extends Thread implements FileTransferSource {

	private Map<String,Map<String, File>> filelist = new HashMap<String, Map<String,File>>();
	private ServerSocket server;
	private String host;
	private int port;
	private LogConnector log;
	private boolean running = true;
	private FileTransferMode mode;

	public DirectSocketTransferSource(String remoteHost, int port, OutputStream out) {
		this.setName("DirectSocketTransferSource");
		this.host = remoteHost;
		this.port = port;
		if (remoteHost == null)
			this.mode = FileTransferMode.SOURCE_HOST;
		else
			this.mode = FileTransferMode.TARGET_HOST;
		log = LogUtils.get("FileTransferSource", out);
	}

	public int getPort() {
		return port;
	}

	@Override
	public void run() {
		try {
			switch (mode) {
			case SOURCE_HOST:
				server = new ServerSocket(port);
				log.info("Start hosting on Port:" + port);
				while (running) {
					Socket socket = server.accept();
					log.info("Incoming request from:" + socket.getRemoteSocketAddress());
					handle(socket);
				}
				break;
			/*
			 * case ACCEPT: handleActive( host, port ); break;
			 */
			default:
				break;
			}
		} catch (IOException e) {
			System.err.print("Socket connect break," + e.getMessage());
		}
	}

	/**
	 * Check the token, then receive the request file name and send back the
	 * file content.
	 * 
	 * @param socket
	 * @throws IOException
	 */
	private void handle(Socket socket) throws IOException {
		DataInputStream in = null;
		DataOutputStream os = null;
		String incomingToken = null;
		try {
			in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			incomingToken = in.readUTF();
			/*StringBuilder sb = new StringBuilder(50);
			try {
				for(int i=0;i<33;i++) {
					sb.append(in.readChar());
				}
			}catch(EOFException ex) {
				log.error("Read token chars failed. ex:" + ex.getMessage());
			}
			String incomingToken = new String(sb.toString());*/ 
			log.info("Receiving an incoming token:" + incomingToken);
			if (filelist.containsKey(incomingToken)) { // valid request.
				Map<String,File> fs = filelist.get(incomingToken);
				os = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
				os.writeInt(Constants.VALIDATION_SUCC);
				os.flush();
				log.info("[{}] validate succ.", incomingToken);
				while (true) {
					String request = in.readUTF();
					if (!request.equals(Constants.TRANSFER_ENDED)) {
						log.info("[{}]Got request to send file:{}", incomingToken, request);
						if (fs.keySet().contains(request)) {
							File f = fs.get(request);
							InputStream fin = null;
							try {
								fin = new FileInputStream(f);
								log.info("[{}]start to send file:{}", incomingToken, f.getName());
								IOUtils.copy(fin, os);
								os.flush();
								log.info("[{}]finish sending file:{}", incomingToken, request);
							} catch (Exception e) {
								e.printStackTrace();
							} finally {
								CommonUtils.closeQuietly(fin);
							}
						}
					} else {
						filelist.remove(incomingToken);
						fs.clear();
						break;
					}
				}
			} else {
				os = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
				os.writeInt(Constants.VALIDATION_FAIL);
				os.flush();
				log.info("Validate incoming token failed, current incoming token is {}, current support tokens is {}", incomingToken, CommonUtils.toJson(filelist.keySet()));
			}
		} catch (IOException e) {
			log.error("handle incoming request met problem. incomingToken:"+ incomingToken, e);
		} finally {
			CommonUtils.closeQuietly(os);
			CommonUtils.closeQuietly(in);
			CommonUtils.closeQuietly(socket);
			// dispose();
		}
	}

	@Override
	public void publish(String token, Collection<File> fileList) throws Exception {
		log.info("Start to publish files with token:{}", token);
		if (fileList == null || fileList.isEmpty())
			throw new NullPointerException("No file provided to be published.");
		if(filelist == null)
			filelist = new HashMap<String, Map<String,File>>();
		if(filelist.containsKey(token)) {
			filelist.remove(token).clear();;
		}
		filelist.put(token, new HashMap<String,File>());
		Map<String,File> fs = filelist.get(token);
		for (File file : fileList) {
			if (!file.exists())
				throw new FileNotFoundException("File:" + file + " didn't exists");
			else
				fs.put(file.getName(), file);
		}
		try {
			if (this.getState().equals(State.NEW))
				this.start();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("resource")
	@Override
	public void push(String token, Collection<File> fileList) throws Exception {
		Socket socket = null;
		DataInputStream in = null;
		DataOutputStream os = null;
		InputStream fin = null;
		try {
			socket = new Socket();
			int countDown = 3;
			while (countDown > 0) {
				try {
					socket.connect(new InetSocketAddress(host, port));
					break;
				} catch (Exception ex) {
					log.error("Connect to Dest Server[" + host + ":" + port + "] failed.", ex);
					countDown--;
					TimeUnit.SECONDS.sleep(5 - countDown);
					if (countDown == 0) {
						throw new Exception("Can't connect to Destination Server for 3 times.", ex);
					}
				}

			}
			os = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			os.writeUTF(token);
			os.flush();
			int response = in.readInt();
			if (response == Constants.VALIDATION_SUCC) {
				for (File file : fileList) {
					try {
						os.writeUTF(file.getName());
						os.flush();
						response = in.readInt();
						if (response == Constants.RECEIVE_READY) {
							log.info("Source: sending:" + file);
							fin = new BufferedInputStream(new FileInputStream(file));
							IOUtils.copy(fin, os);
							os.flush();
						}
						response = in.readInt();
						if (response == Constants.RECEIVE_FINISHED) {
							log.info("Source: finished sent :" + file);
						}
					} finally {
						CommonUtils.closeQuietly(fin);
					}
				}
				os.writeUTF(Constants.TRANSFER_ENDED);
				os.flush();
			} else {
				throw new IOException("Invalid token for sending files to " + host + ":" + port + ", token:" + token);
			}
		} finally {
			CommonUtils.closeQuietly(os);
			CommonUtils.closeQuietly(in);
			CommonUtils.closeQuietly(socket);
			// dispose();
		}
	}

	@Override
	public void dispose() {
		running = false;
		CommonUtils.closeQuietly(server);
	}

	@Override
	public FileTransferMode getTransferMode() {
		return mode;
	}
}
