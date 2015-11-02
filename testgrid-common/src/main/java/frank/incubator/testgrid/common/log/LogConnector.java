package frank.incubator.testgrid.common.log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import frank.incubator.testgrid.common.CommonUtils;

/**
 * Logger Class for connecting Slf4j log and tracker output(which provided by
 * client or agent). Simulate a log level to control the output content.
 * 
 * @author Wang Frank
 * 
 */
public class LogConnector {

	public static class NullOutputSteam extends OutputStream {
		@Override
		public void write(int b) throws IOException {
		}
	}

	public static NullOutputSteam no = new NullOutputSteam();
	private Logger log;
	private OutputStream os;
	private LogLevel level = LogLevel.INFO;
	private Set<String> tags = new LinkedHashSet<String>();

	public static enum LogLevel {
		DEBUG, INFO, WARN, ERROR;

		private int getValue() {
			int ret = 0;
			switch (this) {
				case DEBUG:
					ret = 0;
					break;
				case INFO:
					ret = 1;
					break;
				case WARN:
					ret = 2;
					break;
				case ERROR:
					ret = 3;
					break;
			}
			return ret;
		}

		public Level convert() {
			switch (this) {
				case DEBUG:
					return Level.DEBUG;
				case INFO:
					return Level.INFO;
				case WARN:
					return Level.WARN;
				case ERROR:
					return Level.ERROR;
				default:
					return Level.INFO;
			}
		}

		public int compare(LogLevel o) {
			if (this.getValue() > ((LogLevel) o).getValue()) {
				return 1;
			} else if (this.getValue() < ((LogLevel) o).getValue()) {
				return -1;
			} else {
				return 0;
			}
		}
	}

	public LogConnector(Logger log, OutputStream os) {
		this.log = log;
		if (os != null)
			this.os = os;
		else
			this.os = no;
	}

	public LogConnector(Logger log) {
		this.log = log;
		this.os = no;
	}

	public LogConnector(OutputStream os) {
		this.log = LoggerFactory.getLogger(LogConnector.class.getName());
		this.os = os;
	}

	public LogConnector(OutputStream os, LogLevel level) {
		this.log = LoggerFactory.getLogger(LogConnector.class.getName());
		this.os = no;
		this.level = level;
	}

	public LogConnector(Logger log, OutputStream os, LogLevel level) {
		this.log = log;
		this.os = os;
		this.level = level;
	}

	public LogConnector(Logger log, LogLevel level) {
		this.log = log;
		this.os = no;
		this.level = level;
	}

	public Logger getLog() {
		return log;
	}

	public void setLog(Logger log) {
		this.log = log;
	}

	public OutputStream getOs() {
		return os;
	}

	public void setOs(OutputStream os) {
		this.os = os;
	}

	public LogLevel getLevel() {
		return level;
	}

	public void setLevel(LogLevel level) {
		this.level = level;
	}

	public Set<String> getTags() {
		return tags;
	}

	public void setTags(Set<String> tags) {
		this.tags = tags;
	}

	public void addTags(String... tags) {
		if (tags != null) {
			for (String tag : tags) {
				if (tag != null)
					this.tags.add(tag);
			}
		}
	}

	public void removeTags(String... tags) {
		if (tags != null) {
			for (String tag : tags) {
				if (tag != null)
					this.tags.remove(tag);
			}
		}
	}

	public void debug(String msg) {
		debug(msg, (Object) null);
	}

	public void debug(String msg, Object... objs) {
		if (level.getValue() <= 1)
			output("Debug", msg, null, objs);
		StringBuilder sb = new StringBuilder();
		if (tags != null && !tags.isEmpty())
			for (String tag : tags)
				if (tag != null && !tag.trim().isEmpty())
					sb.append("<").append(tag).append(">");
		sb.append(msg);
		if (objs != null && objs.length > 0)
			log.debug(sb.toString(), objs);
		else
			log.debug(sb.toString());
	}

	public void info(String msg) {
		info(msg, (Object) null);
	}

	public void info(String msg, Object... objs) {
		if (level.getValue() <= 1)
			output("Info", msg, null, objs);
		StringBuilder sb = new StringBuilder();
		if (tags != null && !tags.isEmpty())
			for (String tag : tags)
				if (tag != null && !tag.trim().isEmpty())
					sb.append("<").append(tag).append(">");
		sb.append(msg);
		if (objs != null && objs.length > 0)
			log.info(sb.toString(), objs);
		else
			log.info(sb.toString());
	}

	public void warn(String msg) {
		warn(msg, (Object) null);
	}

	public void warn(String msg, Object... objects) {
		if (level.getValue() <= 2)
			output("Warn", msg, null, objects);
		StringBuilder sb = new StringBuilder();
		if (tags != null && !tags.isEmpty())
			for (String tag : tags)
				if (tag != null && !tag.trim().isEmpty())
					sb.append("<").append(tag).append(">");
		sb.append(msg);
		if (objects != null && objects.length > 0)
			log.warn(sb.toString(), objects);
		else
			log.warn(sb.toString());
	}

	public void error(String msg, Throwable t) {
		error(msg, t, (Object) null);
	}

	public void error(String msg, Object... objects) {
		error(msg, null, objects);
	}

	public void error(String msg, Throwable t, Object... objects) {
		if (level.getValue() <= 3)
			output("Error", msg, t, objects);
		StringBuilder sb = new StringBuilder();
		if (tags != null && !tags.isEmpty())
			for (String tag : tags)
				if (tag != null && !tag.trim().isEmpty())
					sb.append("<").append(tag).append(">");
		sb.append(msg);
		if (objects != null && objects.length > 0) {
			log.error(msg, objects);
			log.error("", t);
		} else {
			log.error(sb.toString(), t);
		}
	}

	private void output(String level, String msg, Throwable t, Object... objects) {
		try {
			if (os != null) {
				StringBuilder sb = new StringBuilder();
				sb.append(CommonUtils.getTime()).append("[").append(log.getName()).append("] [").append(level).append("]");
				if (tags != null && !tags.isEmpty())
					for (String tag : tags)
						if (tag != null && !tag.trim().isEmpty())
							sb.append("<").append(tag).append(">");
				sb.append(msg);
				if (objects != null && objects.length > 0) {
					for (Object obj : objects) {
						sb.append(" ").append(obj).append(" ");
					}
				}
				sb.append("\n");
				if (t != null)
					sb.append((CommonUtils.getErrorStack(t) + "\n"));
				os.write(sb.toString().getBytes("UTF-8"));
				os.flush();
			}
		} catch (IOException e) {
			log.error("Can't output error msg to tracker.", e);
			close();
		}
	}

	public void close() {
		if (this.os != null)
			CommonUtils.closeQuietly(os);
	}
}
