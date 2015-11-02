package frank.incubator.testgrid.common;

import static frank.incubator.testgrid.common.Constants.MAX_PORT_NUMBER;
import static frank.incubator.testgrid.common.Constants.MIN_PORT_NUMBER;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.sun.jna.Pointer;

import frank.incubator.testgrid.common.file.DirectSocketFTChannel;
import frank.incubator.testgrid.common.file.FileTransferChannel;
import frank.incubator.testgrid.common.file.FtpFTChannel;
import frank.incubator.testgrid.common.file.HttpGetFTChannel;
import frank.incubator.testgrid.common.file.HttpPutFTChannel;
import frank.incubator.testgrid.common.file.NfsFTChannel;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.BaseObject;

public final class CommonUtils {
	public static boolean DISABLE_HOSTNAME = false;
	private static final GsonBuilder gb = new GsonBuilder();
	static {
		gb.serializeNulls();
		gb.registerTypeAdapter(new TypeToken<Map<String, Object>>() {
		}.getType(), new JsonDeserializer<Map<String, Object>>() {

			@Override
			public Map<String, Object> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				Map<String, Object> map = new HashMap<String, Object>();
				Iterator<Entry<String, JsonElement>> it = json.getAsJsonObject().entrySet().iterator();
				Entry<String, JsonElement> item = null;
				String key = null;
				JsonElement je = null;
				while (it.hasNext()) {
					item = it.next();
					key = item.getKey();
					je = item.getValue();
					if (!je.isJsonNull() && je.isJsonPrimitive() && ((JsonPrimitive) je).isNumber()) {
						try {
							map.put(key, je.getAsInt());
						} catch (Exception ex) {
							map.put(key, context.deserialize(je, Object.class));
						}
					} else {
						map.put(key, context.deserialize(je, Object.class));
					}
				}

				return map;
			}
		});

		gb.registerTypeAdapter(FileTransferChannel.class, new JsonDeserializer<FileTransferChannel>() {
			@Override
			public FileTransferChannel deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				FileTransferChannel ftc = null;
				JsonObject jo = json.getAsJsonObject();
				int priority = 100;
				if (jo.has("priority"))
					priority = jo.get("priority").getAsInt();
				Map<String, Object> properties = null;
				if (jo.has("properties")) {
					String propStr = jo.get("properties").toString();
					JsonReader reader = new JsonReader(new StringReader(propStr));
					reader.setLenient(true);
					properties = gb.create().fromJson(reader, new TypeToken<Map<String, Object>>() {
					}.getType());
				}
				final String id = jo.get("id").getAsString();
				long lastUpdated = System.currentTimeMillis();
				if (jo.has("lastUpdated"))
					lastUpdated = jo.get("lastUpdated").getAsLong();
				switch (id) {
					case "SOCKET":
						ftc = new DirectSocketFTChannel();
						break;
					case "FTP":
						ftc = new FtpFTChannel();
						break;
					case "HTTPGET":
						ftc = new HttpGetFTChannel();
						break;
					case "HTTPPUT":
						ftc = new HttpPutFTChannel();
						break;
					case "NFS":
						ftc = new NfsFTChannel();
						break;
				}
				if (ftc != null) {
					ftc.setLastUpdated(lastUpdated);
					if (priority != 100)
						ftc.setPriority(priority);
					if (properties != null)
						ftc.setProperties(properties);
				}
				return ftc;
			}
		});
	}
	private static Gson json = new Gson();
	private static Gson json2 = gb.create();

	public static void registerTypeAdapter(Type type, Object typeAdapter) {
		gb.registerTypeAdapter(type, typeAdapter);
		json2 = gb.create();
	}

	public static String toJsonDefault(Object o) {
		if (o == null)
			return "{}";
		return json.toJson(o);
	}

	public static String toJson(Object o) {
		if (o == null)
			return "{}";
		return json2.toJson(o);
	}

	public static String toJsonStructDefault(Object o) {
		if (o == null)
			return "{}";
		return json.toJson(o);
	}

	public static String toJsonStruct(Object o) {
		if (o == null)
			return "{}";
		return json2.toJson(o);
	}

	public static <T> T fromJsonDefault(String jsonStr, Class<T> t) {
		if (jsonStr == null)
			return null;
		try {
			JsonReader reader = new JsonReader(new StringReader(jsonStr));
			reader.setLenient(true);
			return json.fromJson(reader, t);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public static <T> T fromJson(String jsonStr, Class<T> t) {
		if (jsonStr == null)
			return null;
		try {
			JsonReader reader = new JsonReader(new StringReader(jsonStr));
			reader.setLenient(true);
			return json2.fromJson(reader, t);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public static <T> T fromJsonDefault(String jsonStr, Type type) {
		if (jsonStr == null)
			return null;
		try {
			JsonReader reader = new JsonReader(new StringReader(jsonStr));
			reader.setLenient(true);
			return json.fromJson(reader, type);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public static <T> T fromJson(String jsonStr, Type type) {
		if (jsonStr == null)
			return null;
		try {
			JsonReader reader = new JsonReader(new StringReader(jsonStr));
			reader.setLenient(true);
			return json2.fromJson(reader, type);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public static void closeQuietly(Closeable conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(Socket conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(ServerSocket conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(PreparedStatement ps) {
		if (ps != null) {
			try {
				ps.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(InputStream in) {
		if (in != null) {
			try {
				in.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(OutputStream out) {
		if (out != null) {
			try {
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(Reader reader) {
		if (reader != null) {
			try {
				reader.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(Writer writer) {
		if (writer != null) {
			try {
				writer.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(Session session) {
		if (session != null) {
			try {
				session.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(javax.jms.Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(MessageProducer mp) {
		if (mp != null) {
			try {
				mp.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void closeQuietly(MessageConsumer mc) {
		if (mc != null) {
			try {
				mc.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static String getErrorStack(Throwable t) {
		return getErrorStack(t, Charset.defaultCharset().name());
	}

	public static String getErrorStack(Throwable t, String charset) {
		Charset cset = Charset.forName(charset);
		cset = (cset == null) ? Charset.defaultCharset() : cset;
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		t.printStackTrace(new PrintStream(bao));
		closeQuietly(bao);
		try {
			return new String(bao.toByteArray(), charset);
		} catch (UnsupportedEncodingException e) {
			return new String(bao.toByteArray());
		}
	}

	public static void rollback(Connection conn) {
		try {
			conn.rollback();
		} catch (SQLException e) {
			LogUtils.error("Connection Roll back failed.", e);
		}
	}

	public static void setCommit(Connection conn, boolean val) {
		try {
			conn.setAutoCommit(val);
		} catch (SQLException e) {
			LogUtils.error("Connection Set autocommit option failed. value=" + val, e);
		}
	}

	public static String exec(String cmd, String workDir) throws IOException, InterruptedException {
		return exec(cmd, workDir, Charset.defaultCharset().name());
	}

	public static String exec(String cmd, String workDir, String charset) throws IOException, InterruptedException {
		charset = (charset == null) ? Charset.defaultCharset().name() : charset;
		ProcessBuilder pb = null;

		if (isWindows()) {
			pb = new ProcessBuilder("cmd", "/C", cmd);
		} else {
			pb = new ProcessBuilder("/bin/bash", "-cl", cmd);
		}

		if (workDir != null) {
			File f = new File(workDir);
			if (f.exists())
				pb.directory(f);
		}

		pb.redirectErrorStream(true);
		Process p = null;
		int result = -1;
		InputStream in = null;
		String output = "";
		try {
			p = pb.start();
			in = p.getInputStream();
			ByteArrayOutputStream sos = new ByteArrayOutputStream();
			byte[] data = new byte[1024];
			int read = 0;
			while ((read = in.read(data)) != -1) {
				sos.write(data, 0, read);
			}
			result = p.waitFor();
			output = sos.toString(charset);
		} finally {
			try {
				in.close();
			} catch (Exception ex) {
			}
		}
		if (result == 0)
			LogUtils.debug("Exec [" + cmd + "] @" + workDir + " success!");
		else
			LogUtils.debug("Exec [" + cmd + "] @" + workDir + " failed!");
		return output;
	}

	public static String exec(final String cmd, final String workDir, long timeout, final Logger logger) {
		return exec(cmd, workDir, timeout, null, logger, Charset.defaultCharset().name());
	}

	public static String exec(final String cmd, final String workDir, long timeout, StringBuilder isFinish, final Logger logger) {
		return exec(cmd, workDir, timeout, isFinish, logger, Charset.defaultCharset().name());
	}

	public static String exec(final String cmd, final String workDir, long timeout, StringBuilder isFinish, final Logger logger, String charset) {
		if (logger != null)
			logger.info("Executing:" + cmd + " ,under:" + workDir + ", with timeout:" + TimeUnit.SECONDS.convert(timeout, TimeUnit.MILLISECONDS));
		final String charsetF = (charset == null) ? Charset.defaultCharset().name() : charset;
		final StringBuilder output = new StringBuilder(200);
		Runnable r = new Runnable() {
			@Override
			public void run() {
				ProcessBuilder pb = null;

				if (isWindows()) {
					pb = new ProcessBuilder("cmd", "/C", cmd);
				} else {
					pb = new ProcessBuilder("/bin/bash", "-cl", cmd);
				}

				if (workDir != null) {
					File f = new File(workDir);
					if (f.exists())
						pb.directory(f);
				}

				pb.redirectErrorStream(true);
				Process p = null;
				int result = -1;
				InputStream in = null;
				try {
					p = pb.start();
					in = p.getInputStream();
					ByteArrayOutputStream sos = new ByteArrayOutputStream();
					byte[] data = new byte[1024];
					int read = 0;
					while ((read = in.read(data)) != -1) {
						sos.write(data, 0, read);
					}
					result = p.waitFor();
					output.append(sos.toString(charsetF));
				} catch (Exception ex) {
					output.append(getErrorStack(ex));
					if (logger != null) {
						logger.error("ExecCmd failed.", ex);
					} else {
						ex.printStackTrace();
					}
				} finally {
					try {
						in.close();
					} catch (Exception ex) {
					}
				}
				if (logger != null) {
					if (result == 0)
						logger.info("Exec [" + cmd + "] @" + workDir + " success!");
					else
						logger.info("Exec [" + cmd + "] @" + workDir + " failed!");
				}
			}
		};
		ExecutorService es = Executors.newFixedThreadPool(1);
		Future<?> f = es.submit(r);
		try {
			f.get(timeout, TimeUnit.MILLISECONDS);
			if (isFinish != null)
				isFinish.append("true");
		} catch (ExecutionException | TimeoutException | InterruptedException e) {
			logger.error("Execute CMD[" + cmd + "] failed", e);
			if (isFinish != null)
				isFinish.append("false");
		} finally {
			es.shutdownNow();
		}
		return output.toString();
	}

	public static String exec(String cmd, String workDir, Map<String, String> env) throws IOException, InterruptedException {
		return exec(cmd, workDir, env, Charset.defaultCharset().name());
	}

	public static String exec(String cmd, String workDir, Map<String, String> env, String charset) throws IOException, InterruptedException {
		charset = (charset == null) ? Charset.defaultCharset().name() : charset;
		ProcessBuilder pb = null;

		if (isWindows()) {
			if (env != null && !env.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (String key : env.keySet()) {
					sb.append("set ").append(key).append("=").append(env.get(key)).append(" & ");
				}
				sb.append("call ");
				cmd = sb.toString() + cmd;
			}
			pb = new ProcessBuilder("cmd", "/C", cmd);
		} else {
			if (env != null && !env.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (String key : env.keySet()) {
					sb.append(key).append("=").append(env.get(key)).append(" && ");
				}
				cmd = sb.toString() + cmd;
			}
			pb = new ProcessBuilder("/bin/bash", "-cl", cmd);
		}

		if (workDir != null) {
			File f = new File(workDir);
			if (f.exists())
				pb.directory(f);
		}

		pb.redirectErrorStream(true);
		Process p = null;
		int result = -1;
		InputStream in = null;
		String output = "";
		try {
			p = pb.start();
			in = p.getInputStream();
			ByteArrayOutputStream sos = new ByteArrayOutputStream();
			byte[] data = new byte[1024];
			int read = 0;
			while ((read = in.read(data)) != -1) {
				sos.write(data, 0, read);
			}
			result = p.waitFor();
			output = sos.toString(charset);
		} finally {
			try {
				in.close();
			} catch (Exception ex) {
			}
		}
		if (result == 0)
			LogUtils.debug("Exec [" + cmd + "] @" + workDir + " success!");
		else
			LogUtils.debug("Exec [" + cmd + "] @" + workDir + " failed!");
		return output;
	}

	public static Process exec(String cmd, File workDir, Map<String, String> env) throws IOException, InterruptedException {

		ProcessBuilder pb = null;

		if (isWindows()) {
			if (env != null && !env.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (String key : env.keySet()) {
					sb.append("set ").append(key).append("=").append(env.get(key)).append(" & ");
				}
				sb.append("call ");
				cmd = sb.toString() + cmd;
			}
			pb = new ProcessBuilder("cmd", "/C", cmd);
		} else {
			if (env != null && !env.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (String key : env.keySet()) {
					sb.append(key).append("=").append(env.get(key)).append(" && ");
				}
				cmd = sb.toString() + cmd;
			}
			pb = new ProcessBuilder("/bin/bash", "-cl", cmd);
		}

		if (workDir != null && workDir.exists()) {
			pb.directory(workDir);
		}

		pb.redirectErrorStream(true);
		return pb.start();
	}

	public static String getOsType() {
		String os = System.getProperty("os.name");
		if (os.toLowerCase().indexOf("win") >= 0)
			return Constants.OS_WINDOWS_SERIES;
		else if (os.toLowerCase().indexOf("mac") >= 0)
			return Constants.OS_MAC;
		else
			return Constants.OS_UNIX_SERIES;
	}

	public static boolean isWindows() {
		String os = System.getProperty("os.name");
		if (os.toLowerCase().indexOf("win") >= 0)
			return true;
		else
			return false;
	}

	public static String grep(String content, String key, boolean caseSensitive) throws IOException {
		if (content == null || content.isEmpty() || key == null || key.isEmpty())
			return content;
		BufferedReader br = new BufferedReader(new StringReader(content));
		String line = null;
		StringBuilder sb = new StringBuilder(content.length());
		while ((line = br.readLine()) != null) {
			if (caseSensitive) {
				if (line.toLowerCase().indexOf(key.toLowerCase()) >= 0)
					sb.append(line).append("\n");
			} else {
				if (line.indexOf(key) > 0)
					sb.append(line).append("\n");
			}
		}
		return sb.toString();
	}

	public static String grep(InputStream in, String key, boolean caseSensitive) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (in != null) {
			byte[] data = new byte[1024];
			int cnt = 0;
			while ((cnt = in.read(data)) != -1) {
				baos.write(data, 0, cnt);
			}
			return grep(baos.toString(), key, caseSensitive);
		}
		return null;
	}

	public static String getHostName() {
		if (DISABLE_HOSTNAME)
			return getValidHostIp();
		String hostName = "localhost";
		try {
			hostName = InetAddress.getLocalHost().getCanonicalHostName();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return hostName;
	}

	public static String getValidHostIp() {
		String IP = "127.0.0.1";
		try {
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			NetworkInterface networkInterface = null;
			while (networkInterfaces.hasMoreElements()) {
				networkInterface = networkInterfaces.nextElement();
				if (networkInterface.isLoopback())
					continue;
				Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
				while (inetAddresses.hasMoreElements()) {
					InetAddress inetAddress = inetAddresses.nextElement();
					if (inetAddress.getHostAddress().startsWith("10.") || inetAddress.getHostAddress().startsWith("172.")) {
						IP = inetAddress.getHostAddress();
						break;
					}
				}
			}
			if (IP.equals("127.0.0.1") && !InetAddress.getLocalHost().getHostAddress().startsWith("169"))
				IP = InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return IP;
	}

	public static int execBlocking(String line, Map<String, String> environment, StringBuilder output, long timeout) throws IOException {
		return execBlocking(line, null, environment, output, timeout, Charset.defaultCharset().name());
	}

	public static int execBlocking(String line, File workDir, Map<String, String> environment, StringBuilder output, long timeout) throws IOException {
		return execBlocking(line, workDir, environment, output, timeout, Charset.defaultCharset().name());
	}

	public static int execBlocking(String line, File workDir, Map<String, String> environment, StringBuilder output, long timeout, String charset)
			throws IOException {
		charset = (charset == null) ? Charset.defaultCharset().name() : charset;
		if (environment != null) {
			Map<String, String> sysenv = System.getenv();
			for (String key : sysenv.keySet()) {
				boolean contains = false;
				for (String k : environment.keySet()) {
					if (k.equalsIgnoreCase(key)) {
						contains = true;
						break;
					}
				}
				if (!contains)
					environment.put(key, sysenv.get(key));
			}
		}
		DefaultExecutor executor = new DefaultExecutor();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		executor.setStreamHandler(new PumpStreamHandler(baos, baos, null));
		ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
		executor.setWatchdog(watchdog);
		if (workDir != null)
			executor.setWorkingDirectory(workDir);
		int exitVal = executor.execute(CommandLine.parse(line), environment);
		if (output != null) {
			output.append(baos.toString(charset));
		} else {
			System.out.println(baos.toString(charset));
		}

		return exitVal;
	}

	public static String executeCmd(String cmd) {
		return executeCmd(cmd, Charset.defaultCharset().name());
	}

	/**
	 * Execute given command.
	 * 
	 * @param cmd
	 *            Command to be executed
	 * @return output
	 * @since Aug 20, 2012
	 */
	public static String executeCmd(String cmd, String charset) {
		charset = (charset == null) ? Charset.defaultCharset().name() : charset;
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		PumpStreamHandler psh = new PumpStreamHandler(stdout);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(10000);
		Executor exec = new DefaultExecutor();
		exec.setWatchdog(watchdog);
		exec.setStreamHandler(psh);
		CommandLine cl = CommandLine.parse(cmd);
		int exitvalue = -1;
		try {
			exitvalue = exec.execute(cl);
		} catch (ExecuteException e) {
			System.err.println("Execute CMD [" + cmd + "] failed." + getErrorStack(e));
		} catch (IOException e) {
			System.err.println("Execute CMD [" + cmd + "] failed." + getErrorStack(e));
		} finally {
			System.out.println("Execute CMD [" + cmd + "], result:" + exitvalue);
		}
		String output = "";
		try {
			output = stdout.toString(charset);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return output;
	}

	public static ExecuteWatchdog execASync(String line, Map<String, String> environment, OutputStream output, ExecuteResultHandler erh, long timeout)
			throws IOException {
		return execASync(line, null, environment, output, erh, timeout);
	}

	public static ExecuteWatchdog execASync(String line, File workDir, Map<String, String> environment, OutputStream output, ExecuteResultHandler erh,
			long timeout) throws IOException {
		if (environment != null) {
			Map<String, String> sysenv = System.getenv();
			for (String key : sysenv.keySet()) {
				boolean contains = false;
				for (String k : environment.keySet()) {
					if (k.equalsIgnoreCase(key)) {
						contains = true;
						break;
					}
				}
				if (!contains)
					environment.put(key, sysenv.get(key));
			}
		}
		DefaultExecutor executor = new DefaultExecutor();
		if (workDir != null)
			executor.setWorkingDirectory(workDir);
		PumpStreamHandler sh = new PumpStreamHandler(output, output, null);
		executor.setStreamHandler(sh);
		ExecuteWatchdog watchdog = new ProcessTreeWatchDog(timeout);
		executor.setWatchdog(watchdog);
		executor.execute(CommandLine.parse(line), environment, erh);
		return watchdog;
	}
	
	public static ExecuteWatchdog execASync(String line, File workDir, Map<String, String> environment, OutputStream output, ExecuteResultHandler erh,
			long timeout, Logger log) throws IOException {
		if (environment != null) {
			Map<String, String> sysenv = System.getenv();
			for (String key : sysenv.keySet()) {
				boolean contains = false;
				for (String k : environment.keySet()) {
					if (k.equalsIgnoreCase(key)) {
						contains = true;
						break;
					}
				}
				if (!contains)
					environment.put(key, sysenv.get(key));
			}
		}
		DefaultExecutor executor = new DefaultExecutor();
		if (workDir != null)
			executor.setWorkingDirectory(workDir);
		PumpStreamHandler sh = new PumpStreamHandler(output, output, null);
		executor.setStreamHandler(sh);
		ExecuteWatchdog watchdog = new ProcessTreeWatchDog(timeout, log);
		executor.setWatchdog(watchdog);
		executor.execute(CommandLine.parse(line), environment, erh);
		return watchdog;
	}

	public static String toString(Collection<String> strings, String delima) {
		StringBuilder sb = new StringBuilder();
		if (strings != null && !strings.isEmpty()) {
			for (String s : strings) {
				if (s == null || s.isEmpty())
					continue;
				if (sb.length() > 0)
					sb.append(delima);
				sb.append(s);
			}
		}
		return sb.toString();
	}

	public static String toString(Object[] strings, String delima) {
		StringBuilder sb = new StringBuilder();
		if (strings != null && strings.length > 0) {
			for (int i = 0; i < strings.length; i++) {
				if (strings[i] == null || strings[i].toString().isEmpty())
					continue;
				if (sb.length() > 0)
					sb.append(delima);
				sb.append(strings[i].toString());
			}
		}
		return sb.toString();
	}

	public static int getRandomInt(int scope) {
		Random r = new Random();
		scope = Math.abs(scope);
		if (scope == 0)
			scope = r.nextInt();
		return r.nextInt(scope);
	}

	public static Map<String, String> getPcStatus() throws IOException {
		Map<String, String> status = new HashMap<String, String>();
		if (isWindows()) {
			StringBuilder sb = new StringBuilder();
			sb.append(CommonUtils.exec("wmic cpu get name,loadpercentage /value", null, 3000L, null));
			status.put("cpu", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("wmic LOGICALDISK get size,freespace /value", null, 3000L, null));
			status.put("disk", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("wmic OS get FreePhysicalMemory /value", null, 3000L, null));
			sb.append("\n");
			sb.append(CommonUtils.exec("wmic ComputerSystem get TotalPhysicalMemory /value", null, 3000L, null));
			status.put("memory", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("netstat -an", null, 3000L, null));
			status.put("network", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("wmic OS get Caption,CSDVersion,Version,CSName,Status /value", null, 3000L, null));
			status.put("os", sb.toString().trim());

			sb = new StringBuilder();
			// CommonUtils.execBlocking(
			// "wmic process get processid,commandline /value", null, sb, 3000L
			// );
			sb.append(CommonUtils.exec("tasklist", null, 3000L, null));
			status.put("process", sb.toString().trim());
		} else if (Constants.OS_MAC.equals(CommonUtils.getOsType())) {
			StringBuilder sb = new StringBuilder();
			sb.append(CommonUtils.exec("/bin/df -h", null, 3000L, null));
			status.put("disk", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("uname -a", null, 3000L, null));
			status.put("os", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("/bin/ps -ef", null, 3000L, null));
			status.put("process", sb.toString().trim());
		} else { // linux?
			StringBuilder sb = new StringBuilder();
			sb.append(CommonUtils.exec("cat /proc/cpuinfo", null, 3000L, null));
			sb.append("\n");
			sb.append(CommonUtils.exec("vmstat |tail -n 1|awk \"{print $15}", null, 3000L, null));
			status.put("cpu", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("df -h", null, 3000L, null));
			status.put("disk", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("top -n 1|grep \"Mem\"", null, 3000L, null));
			status.put("memory", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("netstat -tulnp", null, 3000L, null));
			status.put("network", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("uname -a", null, 3000L, null));
			status.put("os", sb.toString().trim());

			sb = new StringBuilder();
			sb.append(CommonUtils.exec("ps -ef", null, 3000L, null));
			status.put("process", sb.toString().trim());
		}
		return status;
	}

	/**
	 * Get all process info, including PID, and CMD line.
	 * 
	 * @return
	 */
	public static Map<Integer, String> getAllProcess() {
		Map<Integer, String> map = new HashMap<Integer, String>();
		try {
			StringBuilder sb = new StringBuilder();
			if (isWindows()) {
				int ret = CommonUtils.execBlocking("wmic process get processid,commandline /value", null, sb, 3000L);
				if (ret == 0) {
					BufferedReader br = new BufferedReader(new StringReader(sb.toString()));
					String line = null;
					String cmd = "", pid = "";
					while ((line = br.readLine()) != null) {
						line = line.trim();
						if (line.isEmpty())
							continue;
						if (line.startsWith("CommandLine") && !line.endsWith("=")) {
							cmd = line.split("=")[1];
						} else if (line.startsWith("ProcessId")) {
							pid = line.split("=")[1];
							map.put(parseInt(pid, 0), cmd);
							cmd = "";
							pid = "";
						}
					}
				}
			} else {

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return map;
	}

	public static boolean killProcess(int pid) {
		return killProcess(pid, false);
	}

	public static int getPid(Process p) {
		int pid = 0;
		Field f;
		if (isWindows()) {
			if (p.getClass().getName().equals("java.lang.Win32Process") || p.getClass().getName().equals("java.lang.ProcessImpl")) {
				/* determine the pid on windows plattforms */
				try {
					f = p.getClass().getDeclaredField("handle");
					f.setAccessible(true);
					long handl = f.getLong(p);
					Kernel32 kernel = Kernel32.INSTANCE;
					W32API.HANDLE handle = new W32API.HANDLE();
					handle.setPointer(Pointer.createConstant(handl));
					pid = kernel.GetProcessId(handle);
				} catch (Throwable e) {
				}
			}
		} else {
			try {
				f = p.getClass().getDeclaredField("pid");
				f.setAccessible(true);
				pid = (Integer) f.get(p);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		return pid;
	}

	public static boolean killProcess(int pid, boolean killSubprocess) {
		return killProcess(pid, killSubprocess, null);
	}
	
	public static boolean killProcess(int pid, boolean killSubprocess, Logger log) {
		if(log != null) {
			log.info("Killing pid:" + pid);
		}
		StringBuilder sb = new StringBuilder();
		int ret = 0;
		try {
			String osType = CommonUtils.getOsType();
			switch(osType) {
				case Constants.OS_WINDOWS_SERIES:
					if (killSubprocess) {
						ret = execBlocking("taskkill /F /T /PID " + pid, null, sb, 3000L);
					} else {
						ret = execBlocking("taskkill /F /PID " + pid, null, sb, 3000L);
					}
					break;
				case Constants.OS_MAC:
					if (killSubprocess) {
						try {
							ret = execBlocking("pkill -TERM -P " + pid, null, sb, 3000L);
						}catch(Throwable t) {}
						ret = execBlocking("kill -TERM " + pid, null, sb, 3000L);
					} else {
						ret = execBlocking("kill " + pid, null, sb, 3000L);
					}
					break;
				case Constants.OS_UNIX_SERIES:
					try {
						ret = execBlocking("kill $(pstree " + pid + " -p -a -l | cut -d, -f2 |cut -d  -f1)", null, sb, 3000L);
					}catch(Throwable t) {
						try {
							ret = execBlocking("pkill -TERM -P " + pid, null, sb, 3000L);
						}catch(Throwable z) {}
					}
					break;
			}
			if (ret != 0) {
				if(log != null)
					log.error("Kill Process[" + pid + "] failed." + sb.toString());
				else
					System.err.println("Kill Process[" + pid + "] failed." + sb.toString());
			}
		} catch (IOException e) {
			if(log != null)
				log.error("Kill Process[" + pid + "] failed."+sb.toString(), e);
			else {
				System.err.println("Kill Process[" + pid + "] failed." + sb.toString());
				e.printStackTrace();
			}
			ret = -1;
		}
		return (ret == 0) ? true : false;
	}

	public static boolean parseBoolean(String boolStr) {
		if (boolStr != null && "true".equals(boolStr.toLowerCase()))
			return true;
		return false;
	}
	
	public static boolean parseBoolean(Object boolStr, boolean defaultValue) {
		if (boolStr == null)
			return defaultValue;
		try {
			return Boolean.parseBoolean(boolStr.toString());
		} catch (Exception ex) {
			return defaultValue;
		}
	}

	public static int parseInt(Object v, int backup) {
		if(v == null)
			return backup;
		try {
			if(v instanceof Number)
				return ((Number)v).intValue();
			else 
				return Integer.parseInt(v.toString());
		} catch (Exception e) {
			return backup;
		}
	}

	public static long parseLong(Object v, long backup) {
		if(v == null)
			return backup;
		try {
			if(v instanceof Number)
				return ((Number)v).longValue();
			else 
				return Long.parseLong(v.toString());
		} catch (Exception e) {
			return backup;
		}
	}

	public static float parseFloat(Object v, float backup) {
		if(v == null)
			return backup;
		try {
			if(v instanceof Number)
				return ((Number)v).floatValue();
			else 
				return Float.parseFloat(v.toString());
		} catch (Exception e) {
			return backup;
		}
	}

	public static Double parseDouble(Object v, double backup) {
		if(v == null)
			return backup;
		try {
			if(v instanceof Number)
				return ((Number)v).doubleValue();
			else 
				return Double.parseDouble(v.toString());
		} catch (Exception e) {
			return backup;
		}
	}

	public static Date parseDate(String v, String pattern) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat(pattern);
			return sdf.parse(v);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Timestamp parseTimestamp(long start) {
		return new Timestamp(start);
	}

	public static String toString(Date date, String pattern) {
		if (date == null)
			return null;
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		return sdf.format(date);
	}

	public static String getProp(Properties props, String key) {
		return props.get(key) == null ? null : (String) props.get(key);
	}

	public static Integer getPropInt(Properties props, String key, int backup) {
		String v = getProp(props, key);
		return v == null ? backup : parseInt(v, backup);
	}

	public static Long getPropLong(Properties props, String key, long backup) {
		String v = getProp(props, key);
		return v == null ? backup : parseLong(v, backup);
	}

	public static Float getPropFloat(Properties props, String key, float backup) {
		String v = getProp(props, key);
		return v == null ? backup : parseFloat(v, backup);
	}

	public static Double getPropDouble(Properties props, String key, double backup) {
		String v = getProp(props, key);
		return v == null ? backup : parseDouble(v, backup);
	}

	public static Boolean getPropBoolean(Properties props, String key, boolean backup) {
		String v = getProp(props, key);
		return v == null ? backup : parseBoolean(v);
	}

	public static Date getPropDate(Properties props, String key, String pattern) {
		String v = getProp(props, key);
		return v == null ? null : parseDate(v, pattern);
	}

	public static Timestamp parse(long timestamp) {
		return new Timestamp(timestamp);
	}

	public static synchronized String generateToken(int count) {
		try {
			TimeUnit.MILLISECONDS.sleep(1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return random(count, 0, 0, true, true, null, new Random(System.currentTimeMillis()));
	}

	public static String generateToken() {
		return UUID.randomUUID().toString();
	}

	public static String random(int count, int start, int end, boolean letters, boolean numbers, char[] chars, Random random) {
		if (count == 0) {
			return "";
		} else if (count < 0) {
			throw new IllegalArgumentException("Requested random string length " + count + " is less than 0.");
		}
		if ((start == 0) && (end == 0)) {
			end = 'z' + 1;
			start = ' ';
			if (!letters && !numbers) {
				start = 0;
				end = Integer.MAX_VALUE;
			}
		}

		char[] buffer = new char[count];
		int gap = end - start;

		while (count-- != 0) {
			char ch;
			if (chars == null) {
				ch = (char) (random.nextInt(gap) + start);
			} else {
				ch = chars[random.nextInt(gap) + start];
			}
			if ((letters && Character.isLetter(ch)) || (numbers && Character.isDigit(ch)) || (!letters && !numbers)) {
				if (ch >= 56320 && ch <= 57343) {
					if (count == 0) {
						count++;
					} else {
						// low surrogate, insert high surrogate after putting it
						// in
						buffer[count] = ch;
						count--;
						buffer[count] = (char) (55296 + random.nextInt(128));
					}
				} else if (ch >= 55296 && ch <= 56191) {
					if (count == 0) {
						count++;
					} else {
						// high surrogate, insert low surrogate before putting
						// it in
						buffer[count] = (char) (56320 + random.nextInt(128));
						count--;
						buffer[count] = ch;
					}
				} else if (ch >= 56192 && ch <= 56319) {
					// private high surrogate, no effing clue, so skip it
					count++;
				} else {
					buffer[count] = ch;
				}
			} else {
				count++;
			}
		}
		return new String(buffer);
	}

	public static synchronized int availablePort(int startPoint) {
		return AvailablePortFinder.getNextAvailable(startPoint);
	}

	private static class AvailablePortFinder {

		/**
		 * Creates a new instance.
		 */
		private AvailablePortFinder() {
		}

		/**
		 * Returns the {@link Set} of currently available port numbers (
		 * {@link Integer}). This method is identical to
		 * <code>getAvailablePorts(MIN_PORT_NUMBER, MAX_PORT_NUMBER)</code>.
		 * 
		 * WARNING: this can take a very long time.
		 */
		@SuppressWarnings("unused")
		public static Set<Integer> getAvailablePorts() {
			return getAvailablePorts(MIN_PORT_NUMBER, MAX_PORT_NUMBER);
		}

		/**
		 * Gets the next available port starting at the lowest port number.
		 * 
		 * @throws NoSuchElementException
		 *             if there are no ports available
		 */
		@SuppressWarnings("unused")
		public static int getNextAvailable() {
			return getNextAvailable(MIN_PORT_NUMBER);
		}

		/**
		 * Gets the next available port starting at a port.
		 * 
		 * @param fromPort
		 *            the port to scan for availability
		 * @throws NoSuchElementException
		 *             if there are no ports available
		 */
		public static int getNextAvailable(int fromPort) {
			if ((fromPort < MIN_PORT_NUMBER) || (fromPort > MAX_PORT_NUMBER)) {
				throw new IllegalArgumentException("Invalid start port: " + fromPort);
			}

			for (int i = fromPort; i <= MAX_PORT_NUMBER; i++) {
				if (available(i)) {
					return i;
				}
			}

			throw new NoSuchElementException("Could not find an available port " + "above " + fromPort);
		}

		/**
		 * Checks to see if a specific port is available.
		 * 
		 * @param port
		 *            the port to check for availability
		 */
		public static boolean available(int port) {
			if ((port < MIN_PORT_NUMBER) || (port > MAX_PORT_NUMBER)) {
				throw new IllegalArgumentException("Invalid start port: " + port);
			}

			Socket ss = null;
			try {
				ss = new Socket("localhost", port);
				return false;
			} catch (IOException e) {
				return true;
			} finally {
				CommonUtils.closeQuietly(ss);
			}
		}

		/**
		 * Returns the {@link Set} of currently avaliable port numbers (
		 * {@link Integer}) between the specified port range.
		 * 
		 * @throws IllegalArgumentException
		 *             if port range is not between {@link #MIN_PORT_NUMBER} and
		 *             {@link #MAX_PORT_NUMBER} or <code>fromPort</code> if
		 *             greater than <code>toPort</code>.
		 */
		public static Set<Integer> getAvailablePorts(int fromPort, int toPort) {
			if ((fromPort < MIN_PORT_NUMBER) || (toPort > MAX_PORT_NUMBER) || (fromPort > toPort)) {
				throw new IllegalArgumentException("Invalid port range: " + fromPort + " ~ " + toPort);
			}

			Set<Integer> result = new TreeSet<Integer>();

			for (int i = fromPort; i <= toPort; i++) {
				ServerSocket s = null;

				try {
					s = new ServerSocket(i);
					result.add(new Integer(i));
				} catch (IOException e) {
				} finally {
					if (s != null) {
						try {
							s.close();
						} catch (IOException e) {
							/* should not be thrown */
						}
					}
				}
			}

			return result;
		}
	}

	/**
	 * Converts specified time in milliseconds into a nice string containing
	 * days, hours, minutes and seconds.
	 * 
	 * @param milliseconds
	 *            Time value to be converted
	 * @return String containing days, hours, minutes and seconds from specified
	 *         milliseconds value
	 */
	public static String convert(long milliseconds) {
		String result = "";
		if (milliseconds < Constants.ONE_SECOND) {
			if (milliseconds > 1L) {
				result = milliseconds + " milliseconds";
			} else {
				result = milliseconds + " millisecond";
			}
			return result;
		}
		long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
		long dSeconds = seconds % 60L;
		if (dSeconds > 0L) {
			if (dSeconds > 1L) {
				result = dSeconds + " seconds";
			} else {
				result = dSeconds + " second";
			}
			seconds -= dSeconds;
		}
		long minutes = seconds / 60L;
		long dMinutes = minutes % 60L;
		if (dMinutes > 0L) {
			if (dMinutes > 1L) {
				result = dMinutes + " minutes " + result;
			} else {
				result = dMinutes + " minute " + result;
			}
			minutes -= dMinutes;
		}
		long hours = minutes / 60L;
		long dHours = hours % 24L;
		if (dHours > 0L) {
			if (dHours > 1L) {
				result = dHours + " hours " + result;
			} else {
				result = dHours + " hour " + result;
			}
			hours -= dHours;
		}
		long days = hours / 24L;
		if (days > 0L) {
			if (days > 1L) {
				result = days + " days " + result;
			} else {
				result = days + " day " + result;
			}
		}

		return result.trim();
	}

	/**
	 * Get Current time string.
	 * 
	 * @return
	 */
	public static String getTime() {
		return new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss:SSS] ").format(new Date());
	}

	/**
	 * Get Current time pure string.
	 * 
	 * @return
	 */
	public static String getCurrentTime(String pattern) {
		return new SimpleDateFormat(pattern).format(new Date());
	}

	public static List<String> splitBlanks(String input) {
		if (input == null)
			return null;
		input = input.trim();
		if (input.isEmpty())
			return new ArrayList<String>();
		StringBuilder sb = new StringBuilder();
		List<String> arr = new ArrayList<String>();
		boolean appendable = true;
		for (char c : input.toCharArray()) {
			if (Character.isWhitespace(c)) {
				if (appendable) {
					appendable = false;
					arr.add(sb.toString());
				}
			} else {
				if (!appendable) {
					appendable = true;
					sb = new StringBuilder();
				}
				sb.append(c);
			}
		}
		arr.add(sb.toString());
		return arr;
	}

	/**
	 * Return non-null string value of this object.
	 * 
	 * @param str
	 * @param forWeb
	 * @return
	 */
	public static String notNull(Object str, boolean forWeb) {
		if (str == null)
			return forWeb ? "&nbsp;" : "";
		return str.toString();
	}

	/**
	 * Format some character for web represent format.
	 * 
	 * @param str
	 * @return
	 */
	public static String format4Web(String str) {
		if (str == null)
			return "&nbsp;";
		return str.trim().replaceAll("\n", "<br/>");
	}

	/**
	 * Render assigned Object to HTML form.
	 * 
	 * @param obj
	 *            Object to be rendered.
	 * 
	 * @return
	 */
	public static String renderToHtml(Object obj) {
		return renderToHtml(obj, 0, new AtomicInteger(0));
	}

	/**
	 * Render assigned Object to HTML form.
	 * 
	 * @param obj
	 *            Object to be rendered.
	 * @param level
	 *            . The level of rendered structure.
	 * 
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String renderToHtml(Object obj, int level, AtomicInteger counter) {
		if (obj == null)
			return notNull(obj, true);

		if (obj.getClass().isPrimitive() || isWrapperType(obj.getClass()) || CharSequence.class.isAssignableFrom(obj.getClass()))
			return notNull(obj, true);

		StringBuilder sb = new StringBuilder();
		if (level == 0)
			sb.append("<a href='javascript:void(0)' onclick='switcher(\"div" + counter + "\")'>&nbsp;switch&nbsp;</a><div id='div" + counter.getAndIncrement()
					+ "' style='display:none' class='level" + level + "'>");
		// Special for Test cloud.
		if (obj instanceof BaseObject) {
			sb.append("<span class='topLabel'>").append(((BaseObject) obj).getId()).append("</span>");
		}
		sb.append("<ul class='level" + level + "'>");
		if (Collection.class.isAssignableFrom(obj.getClass())) {
			for (Object o : (Collection) obj) {
				if (o != null) {
					sb.append("<li>");
					sb.append(
							"<a href='javascript:void(0)' onclick='switcher(\"div" + counter + "\")'>&nbsp;switch&nbsp;</a><div id='div"
									+ counter.getAndIncrement() + "' style='display:none' class='vallevel" + level + "'>")
							.append(renderToHtml(o, level + 1, counter)).append("</div>");
					sb.append("</li>");
				}
			}
		} else if (Map.class.isAssignableFrom(obj.getClass())) {
			for (Map.Entry o : (Set<Map.Entry>) ((Map) obj).entrySet()) {
				if (o != null) {
					sb.append("<li>");
					sb.append("<span class='keylevel" + level + "'>").append(renderToHtml(o.getKey(), level + 1, counter)).append("</span>");
					sb.append(
							" : <a href='javascript:void(0)' onclick='switcher(\"div" + counter + "\")'>&nbsp;switch&nbsp;</a><div id='div"
									+ counter.getAndIncrement() + "' style='display:none' class='vallevel" + level + "'>")
							.append(renderToHtml(o.getValue(), level + 1, counter)).append("</div>");
					sb.append("</li>");
				}
			}
		} else {
			Field[] selfFields = obj.getClass().getDeclaredFields();
			Field[] parentFields = obj.getClass().getSuperclass().getDeclaredFields();
			for (Field f : parentFields) {
				// didn't render final/static, native and transient fields.
				if (!Modifier.isTransient(f.getModifiers()) && !(Modifier.isFinal(f.getModifiers()) && Modifier.isStatic(f.getModifiers()))
						&& !Modifier.isNative(f.getModifiers())) {
					f.setAccessible(true);
					sb.append("<li>").append("<span class='keylevel" + level + "'>").append(f.getName()).append("</span>");
					try {
						sb.append(
								" : <a href='javascript:void(0)' onclick='switcher(\"div" + counter + "\")'>&nbsp;switch&nbsp;</a><div id='div"
										+ counter.getAndIncrement() + "' style='display:none' class='vallevel" + level + "'>")
								.append(renderToHtml(f.get(obj), level + 1, counter)).append("</div>");
					} catch (Exception e) {
						LogUtils.error("Reflect model value failed.", e);
					}
					sb.append("</li>");
				}
			}

			for (Field f : selfFields) {
				f.setAccessible(true);
				// didn't render final/static, native and transient fields.
				if (!Modifier.isTransient(f.getModifiers()) && !(Modifier.isFinal(f.getModifiers()) && Modifier.isStatic(f.getModifiers()))
						&& !Modifier.isNative(f.getModifiers())) {
					f.setAccessible(true);
					sb.append("<li>").append("<span class='keylevel" + level + "'>").append(f.getName()).append("</span>");
					try {
						sb.append(
								" : <a href='javascript:void(0)' onclick='switcher(\"div" + counter + "\")'>&nbsp;switch&nbsp;</a><div id='div"
										+ counter.getAndIncrement() + "' style='display:none' class='vallevel" + level + "'>")
								.append(renderToHtml(f.get(obj), level + 1, counter)).append("</div>");
					} catch (Exception e) {
						LogUtils.error("Reflect failed when render", e);
					}
					sb.append("</li>");
				}
			}
		}
		sb.append("</ul>");
		if (level == 0)
			sb.append("</div>");
		return sb.toString();
	}

	private static final Set<Class<?>> WRAPPER_TYPES = getWrapperTypes();

	public static boolean isWrapperType(Class<?> clazz) {
		return WRAPPER_TYPES.contains(clazz);
	}

	private static Set<Class<?>> getWrapperTypes() {
		Set<Class<?>> ret = new HashSet<Class<?>>();
		ret.add(Boolean.class);
		ret.add(Character.class);
		ret.add(Byte.class);
		ret.add(Short.class);
		ret.add(Integer.class);
		ret.add(Long.class);
		ret.add(Float.class);
		ret.add(Double.class);
		ret.add(Void.class);
		return ret;
	}

	public static InputStream loadResources(String filename, boolean externalFirst) {
		InputStream in = null;
		File f = new File(System.getProperty("user.dir"), filename);
		try {
			if (externalFirst) {
				if (f.exists() && !f.isDirectory())
					in = new FileInputStream(f);
				else
					in = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
			} else {
				in = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
				if (in == null && f.exists() && !f.isDirectory()) {
					in = new FileInputStream(f);
				}
			}
		} catch (Exception ex) {
			LogUtils.error("Load Resouce [ " + filename + " ] failed.", ex);
		}
		return in;
	}

	public static final int RESOURCE_LOAD_MODE_ONLY_EMBEDED = 0;

	public static final int RESOURCE_LOAD_MODE_ONLY_EXTERNAL = 1;

	public static final int RESOURCE_LOAD_MODE_BOTH = 2;

	/**
	 * Load resource from embeded resource or external resource.
	 * 
	 * @param filename
	 *            resource name.
	 * @param mode
	 *            Should be one of RESOURCE_LOAD_MODE_ONLY_EMBEDED |
	 *            RESOURCE_LOAD_MODE_ONLY_EXTERNAL | RESOURCE_LOAD_MODE_BOTH
	 * @param externalFirst
	 *            If load external resource first. Otherwise embeded resource
	 *            first as default.
	 * @return
	 */
	public static String loadResourcesAsString(String filename, int mode, boolean externalFirst) {
		if (mode > RESOURCE_LOAD_MODE_BOTH || mode < RESOURCE_LOAD_MODE_ONLY_EMBEDED)
			mode = RESOURCE_LOAD_MODE_ONLY_EMBEDED;
		InputStream in = null;
		File f = new File(System.getProperty("user.dir"), filename);
		StringWriter sw = new StringWriter();
		try {
			if (externalFirst) {
				if (mode != RESOURCE_LOAD_MODE_ONLY_EMBEDED && f.exists() && !f.isDirectory()) {
					in = new FileInputStream(f);
					IOUtils.copy(in, sw);
					sw.write('\n');
					IOUtils.closeQuietly(in);
				}
				if (mode != RESOURCE_LOAD_MODE_ONLY_EXTERNAL) {
					in = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
					if (in != null) {
						IOUtils.copy(in, sw);
						IOUtils.closeQuietly(in);
					}
				}
			} else {
				if (mode != RESOURCE_LOAD_MODE_ONLY_EXTERNAL) {
					in = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
					if (in != null) {
						IOUtils.copy(in, sw);
						sw.write('\n');
						IOUtils.closeQuietly(in);
					}
				}

				if (mode != RESOURCE_LOAD_MODE_ONLY_EMBEDED && f.exists() && !f.isDirectory()) {
					in = new FileInputStream(f);
					IOUtils.copy(in, sw);
					IOUtils.closeQuietly(in);
				}
			}
		} catch (Exception ex) {
			LogUtils.error("Load Resouce [ " + filename + " ] failed.", ex);
		}
		return sw.toString();
	}

	public static File getLastmodifiedFileInFolder(File folder, FileFilter filter) {
		if (folder == null || !folder.exists())
			return null;
		File[] files = folder.listFiles(filter);
		File ret = null;
		if (files != null) {
			long lastModified = 0L;
			for (File f : files) {
				if (lastModified <= f.lastModified())
					ret = f;
			}
		}
		return ret;
	}

	/**
	 * Check if the given host( name or ip ) is reachable.
	 * 
	 * @param host
	 * @return boolean
	 */
	public static boolean isReachable(String host) {
		try {
			return InetAddress.getByName(host).isReachable(2000);
		} catch (UnknownHostException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * check if assigned remote port is open.
	 * 
	 * @param host
	 * @param port
	 * @return
	 */
	public static boolean isRemotePortOpen(String host, int port) {
		Socket socket = null;
		try {
			socket = new Socket();
			socket.connect(new InetSocketAddress(host, port), 1000);
			return true;
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		} finally {
			CommonUtils.closeQuietly(socket);
		}
	}

	/**
	 * Http Get assigned URL and return the status code.
	 * 
	 * @param url
	 * @param logConnector
	 *            if you want to print the output to some logConnector, assign a
	 *            logConnector or if left null, default logger will be used.
	 * @return
	 */
	public static int httpGet(String url, LogConnector log) {
		int statusCode = -1;
		if (url == null || url.trim().isEmpty()) {
			if (log != null)
				log.error("HttpGet given NUll URL. ");
			else
				LogUtils.error("HttpGet given NUll URL. ", new NullPointerException());
			return statusCode;
		}
		if (log != null)
			log.info("Send http resquest to URL:{}", url);
		CloseableHttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		HttpResponse response = null;
		try {
			response = client.execute(request);
			statusCode = response.getStatusLine().getStatusCode();
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			if (response.getEntity() != null)
				response.getEntity().writeTo(bao);
			String output = "HttpResponse: " + bao.toString("UTF-8");
			if (log != null)
				log.info(output);
			else
				LogUtils.p(output);
		} catch (Exception e) {
			if (log != null)
				log.error("HttpGet for " + url + " failed. ", e);
			else
				LogUtils.error("HttpGet for " + url + " failed.", e);
		} finally {
			CommonUtils.closeQuietly(client);
		}
		return statusCode;
	}

	public static int httpGet(String url, Logger log) {
		int statusCode = -1;
		if (url == null || url.trim().isEmpty()) {
			if (log != null)
				log.error("HttpGet given NUll URL. ");
			else
				LogUtils.error("HttpGet given NUll URL. ", new NullPointerException());
			return statusCode;
		}
		if (log != null)
			log.info("Send http resquest to URL:{}", url);
		CloseableHttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		HttpResponse response = null;
		try {
			response = client.execute(request);
			statusCode = response.getStatusLine().getStatusCode();
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			if (response.getEntity() != null)
				response.getEntity().writeTo(bao);
			String output = "HttpResponse: " + bao.toString("UTF-8");
			if (log != null)
				log.info(output);
			else
				LogUtils.p(output);
		} catch (Exception e) {
			if (log != null)
				log.error("HttpGet for " + url + " failed. ", e);
			else
				LogUtils.error("HttpGet for " + url + " failed.", e);
		} finally {
			CommonUtils.closeQuietly(client);
		}
		return statusCode;
	}

	public static int httpGet(String url, StringBuilder sb, Logger log) {
		int statusCode = -1;
		if (url == null || url.trim().isEmpty()) {
			if (log != null)
				log.error("HttpGet given NUll URL. ");
			else
				LogUtils.error("HttpGet given NUll URL. ", new NullPointerException());
			return statusCode;
		}
		if (log != null)
			log.info("Send http resquest to URL:{}", url);
		CloseableHttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		HttpResponse response = null;
		try {
			response = client.execute(request);
			statusCode = response.getStatusLine().getStatusCode();
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			if (response.getEntity() != null)
				response.getEntity().writeTo(bao);
			sb.append(bao.toString());
			if(log != null)
				log.info("http get get response status:{}, with response text:{}", statusCode, sb.toString());
		} catch (Exception e) {
			if (log != null)
				log.error("HttpGet for " + url + " failed. ", e);
			else
				LogUtils.error("HttpGet for " + url + " failed.", e);
		} finally {
			CommonUtils.closeQuietly(client);
		}
		return statusCode;
	}

	/**
	 * Send a Http Post.
	 * 
	 * @param url
	 * @param paramName
	 * @param value
	 * @param sb
	 * @return
	 */
	public static int httpPost(String url, String paramName, String value, StringBuilder sb, Logger ... logs) {
		List<NameValuePair> pairs = new ArrayList<NameValuePair>();
		NameValuePair p = new BasicNameValuePair(paramName, value);
		pairs.add(p);
		return httpPost(url, pairs, "UTF-8", sb, logs);
	}

	/**
	 * Send a Http Post.
	 * 
	 * @param url
	 * @param paramName
	 * @param value
	 * @param sb
	 * @return
	 */
	public static int httpPost(String url, String paramName, String value, String encoding, StringBuilder sb, Logger ... logs) {
		List<NameValuePair> pairs = new ArrayList<NameValuePair>();
		NameValuePair p = new BasicNameValuePair(paramName, value);
		pairs.add(p);
		return httpPost(url, pairs, encoding, sb, logs);
	}

	/**
	 * Send a Http Post
	 * 
	 * @param url
	 * @param data
	 * @param sb
	 * @return
	 */
	public static int httpPost(String url, Map<String, String> data, String encoding, StringBuilder sb, Logger ... logs) {
		List<NameValuePair> pairs = new ArrayList<NameValuePair>();
		NameValuePair p = null;
		for (String key : data.keySet()) {
			p = new BasicNameValuePair(key, data.get(key));
			pairs.add(p);
		}
		return httpPost(url, pairs, encoding, sb, logs);
	}

	/**
	 * Http Post assigned URL and return the status code.
	 * 
	 * @param url
	 * @param sb
	 *            Response Output.
	 * @return status code
	 * @throws UnsupportedEncodingException
	 */
	public static int httpPost(String url, List<NameValuePair> data, String encoding, StringBuilder sb, Logger ... logs) {
		return httpPost(url, null, data, encoding, sb, logs);
	}

	/**
	 * Http Post assigned URL and return the status code.
	 * 
	 * @param url
	 * @param sb
	 *            Response Output.
	 * @return status code
	 * @throws UnsupportedEncodingException
	 */
	public static int httpPost(String url, CookieStore store, List<NameValuePair> data, String encoding, StringBuilder sb, Logger ... logs) {
		int statusCode = -1;
		if (url == null || url.trim().isEmpty()) {
			if (sb != null)
				sb.append("HttpPost given NUll URL. ");
			return statusCode;
		}
		Logger log = null;
		if(logs != null && logs.length>0)
			log = logs[0];
		CloseableHttpClient client = HttpClientBuilder.create().setDefaultCookieStore(store).build();
		HttpPost request = new HttpPost(url);
		if(log != null)
			log.info("Send http post to url:{}", url);
		try {
			if (encoding == null) {
				encoding = "UTF-8";
			}
			request.setEntity(new UrlEncodedFormEntity(data, encoding));
			HttpResponse response = null;
			response = client.execute(request);
			statusCode = response.getStatusLine().getStatusCode();
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			if (response.getEntity() != null)
				response.getEntity().writeTo(bao);
			if (sb != null)
				sb.append(bao.toString("UTF-8"));
			if(log != null)
				log.info("http post get response status:{}, with response text:{}", statusCode, sb.toString());
		} catch (Exception e) {
			if (sb != null)
				sb.append(getErrorStack(e));
		} finally {
			CommonUtils.closeQuietly(client);
		}
		return statusCode;
	}

	public static File downloadFileTo(String url, File target) {
		if (url == null || url.trim().isEmpty()) {
			return null;
		}
		CloseableHttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		HttpResponse response = null;
		InputStream in = null;
		FileOutputStream os = null;
		try {
			response = client.execute(request);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == 200) {
				if (response.getEntity() != null) {
					in = response.getEntity().getContent();
					os = new FileOutputStream(target);
					IOUtils.copy(in, os);
					os.flush();
					return target;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			closeQuietly(os);
			closeQuietly(in);
			closeQuietly(client);
		}
		return null;
	}

	public static File downloadFile(String url, File base) {
		if (url == null || url.trim().isEmpty()) {
			return null;
		}
		String fileName = url.substring(url.lastIndexOf("/") + 1);
		CloseableHttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		HttpResponse response = null;
		InputStream in = null;
		FileOutputStream os = null;
		try {
			response = client.execute(request);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == 200) {
				if (response.getEntity() != null) {
					in = response.getEntity().getContent();
					File target = new File(base, fileName);
					os = new FileOutputStream(target);
					IOUtils.copy(in, os);
					os.flush();
					return target;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			closeQuietly(os);
			closeQuietly(in);
			closeQuietly(client);
		}
		return null;
	}

	public static void writeFile(File dest, String content, String encoding) {
		if (dest == null)
			throw new NullPointerException("Write to File failed. destination file is NULL");
		if (content == null)
			return;
		if (encoding == null)
			encoding = "UTF-8";
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(dest);
			fos.write(content.getBytes(encoding));
			fos.flush();
		} catch (Exception ex) {
			throw new RuntimeException("write to File[" + dest + "] failed.", ex);
		} finally {
			CommonUtils.closeQuietly(fos);
		}
	}
	
	public static boolean writeFile(File dest, String content, String encoding, Logger log){
		if (dest == null) {
			if(log != null)
				log.error("Write to File failed. destination file is NULL.");
			return false;
		}
		if (content == null) {
			if(log != null)
				log.warn("Write content is NULL. Do nothing");
			return true;
		}
		if (encoding == null)
			encoding = "UTF-8";
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(dest);
			fos.write(content.getBytes(encoding));
			fos.flush();
			return true;
		} catch (Exception ex) {
			if(log != null)
				log.error("write to File[" + dest + "] failed.content:"+content+",encoding:"+ encoding, ex);
			else
				ex.printStackTrace();
		} finally {
			CommonUtils.closeQuietly(fos);
		}
		return false;
	}

	public static String readFileContent(File file) {
		return readFileContent(file, "UTF-8");
	}

	public static String readFileContent(File file, String encoding) {
		if (file == null || !file.exists() || !file.isFile()) {
			return null;
		}
		FileInputStream fin = null;
		ByteArrayOutputStream baos = null;
		try {
			baos = new ByteArrayOutputStream();
			fin = new FileInputStream(file);
			IOUtils.copy(fin, baos);
			baos.flush();
			String str = new String(baos.toByteArray(), encoding);
			return str;
		} catch (Exception ex) {
			LogUtils.error("Read File[" + file.getAbsolutePath() + "] failed.", ex);
		} finally {
			CommonUtils.closeQuietly(baos);
			CommonUtils.closeQuietly(fin);
		}
		return null;
	}

	public static boolean equal(Object a, Object b) {
		if (a == b)
			return true;
		if (a == null || b == null)
			return false;
		return a.equals(b);
	}

	public static float checkLatency(String dest) {
		float latency = -1;
		if (dest != null && isReachable(dest)) {
			StringBuilder sb = new StringBuilder();
			int retCode = 0;
			List<Float> secs = new ArrayList<Float>();
			try {
				if (CommonUtils.isWindows()) {
					retCode = CommonUtils.execBlocking("ping -n 5 " + dest, null, sb, TimeUnit.SECONDS.toMillis(10));
					if (retCode == 0) {
						String[] arr = sb.toString().split("\r\n");
						if (arr != null) {
							for (String line : arr) {
								if (line.indexOf("=") > 0 && line.indexOf("ms") > 0) {
									if (line.indexOf('<') > 0) {
										line = line.substring(line.indexOf('<') + 1, line.indexOf("ms"));
									} else {
										line = line.substring(line.indexOf('=', line.indexOf('=') + 1) + 1, line.indexOf("ms"));
									}
									float la = CommonUtils.parseFloat(line.trim(), 0);
									if (la != 0)
										secs.add(la);
									if (secs.size() == 5)
										break;
								}
							}
						}
					}
				} else {
					retCode = CommonUtils.execBlocking("ping -c 5 " + dest, null, sb, TimeUnit.SECONDS.toMillis(10));
					if (retCode == 0) {
						String[] arr = sb.toString().split("\n");
						if (arr != null) {
							for (String line : arr) {
								if (line.indexOf("time=") > 0 && line.indexOf("ms") > 0) {
									line = line.substring(line.indexOf("time=") + 5, line.indexOf("ms"));
									float la = CommonUtils.parseFloat(line.trim(), 0);
									if (la != 0)
										secs.add(la);
									if (secs.size() == 5)
										break;
								}
							}
						}
					}
				}
				if (!secs.isEmpty()) {
					float sum = 0;
					for (float f : secs) {
						sum += f;
					}
					latency = sum / (float) secs.size();
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		return latency;
	}

	public static boolean appendContent2File(String timelineFile, String content, String encoding) {
		if (timelineFile == null || content == null)
			return false;
		if (encoding == null)
			encoding = "UTF-8";
		RandomAccessFile f = null;
		boolean result = false;
		try {
			f = new RandomAccessFile(timelineFile, "rw");
			f.seek(f.length());
			f.write(content.getBytes(encoding));
			result = true;
		} catch (Exception ex) {
			System.err.println("Append content 2 file :" + timelineFile + " failed.");
			ex.printStackTrace();
		} finally {
			CommonUtils.closeQuietly(f);
		}
		return result;
	}
}
