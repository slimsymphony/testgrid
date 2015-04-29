package frank.incubator.testgrid.common.log;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.DefaultTimeBasedFileNamingAndTriggeringPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

import frank.incubator.testgrid.common.log.LogConnector.LogLevel;
import frank.incubator.testgrid.common.message.BufferedMessageOutputStream;
import frank.incubator.testgrid.common.message.Pipe;

/**
 * Common Log Utilities. This class provides 2 types of log. LogConnector and
 * Slf4j Loggers. It will Hold on all the created log instances so it won't
 * create multiple times.
 * 
 * @author Wang Frank
 *
 */
public class LogUtils {

	final public static LogConnector.NullOutputSteam no = new LogConnector.NullOutputSteam();
	private static Map<String, Logger> loggers = new ConcurrentHashMap<String, Logger>();

	private static LogConnector commonlog = new LogConnector(LoggerFactory.getLogger("root"));

	private static LoggerContext context = new LoggerContext();

	public static LogLevel DEFAULT_LOG_LEVEL = LogLevel.INFO;

	public static LogConnector get(String name) {
		return get(name, null, DEFAULT_LOG_LEVEL);
	}

	public static LogConnector get(String name, Pipe pipe, Map<String, Object> criteria) {
		LogConnector log = null;
		Logger logger = loggers.get(name);
		if (logger == null) {
			logger = getLogger(name);
		}
		log = new LogConnector(logger, new BufferedMessageOutputStream(pipe.getParentBroker().getSession(),
				pipe.getProducer(), criteria));
		return log;
	}

	public static LogConnector get(String name, OutputStream tracker) {
		return get(name, tracker, DEFAULT_LOG_LEVEL);
	}

	public static LogConnector get(String name, OutputStream tracker, LogLevel level) {
		LogConnector log = null;
		Logger logger = loggers.get(name);
		if (logger == null) {
			logger = getLogger(name);
		}
		log = new LogConnector(logger, tracker);
		return log;
	}

	public static LogConnector getCommonLog() {
		return commonlog;
	}

	public static void debug(String message) {
		commonlog.debug(message);
	}

	public static void p(String message) {
		commonlog.info(message);
	}

	public static void error(String message, Throwable t) {
		commonlog.error(message, t);
	}

	public static Logger getLogger(String name) {
		Logger log = loggers.get(name);
		if (log == null) {
			log = dynamic(name);
			loggers.put(name, log);
		}
		return log;
	}
	
	public static Logger getLog(String appender) {
		return (Logger)LoggerFactory.getLogger(appender);
	}

	private static Logger dynamic(String name) {
		return dynamic(name, DEFAULT_LOG_LEVEL);
	}

	private static Logger dynamic(String name, LogLevel level) {
		// ( LoggerContext ) LoggerFactory.getILoggerFactory();
		/*
		 * String thName = Thread.currentThread().getName(); if( thName != null
		 * && !thName.trim().isEmpty() ) name = name + "_" +
		 * thName.trim().replaceAll( " ", "_" );
		 */
		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
		encoder.setContext(context);
		encoder.setCharset(Charset.forName("UTF-8"));

		DefaultTimeBasedFileNamingAndTriggeringPolicy<ILoggingEvent> timeBasedTriggeringPolicy = new DefaultTimeBasedFileNamingAndTriggeringPolicy<ILoggingEvent>();
		timeBasedTriggeringPolicy.setContext(context);

		TimeBasedRollingPolicy<ILoggingEvent> timeBasedRollingPolicy = new TimeBasedRollingPolicy<ILoggingEvent>();
		timeBasedRollingPolicy.setContext(context);
		String workspace = System.getenv("WORKSPACE");
		if (workspace == null)
			workspace = "";
		if (!workspace.isEmpty() && !workspace.endsWith("/"))
			workspace += "/";
		timeBasedRollingPolicy.setFileNamePattern(workspace + "logs/" + name + "-%d{yyyy-MM-dd}.log");
		timeBasedRollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(timeBasedTriggeringPolicy);

		timeBasedTriggeringPolicy.setTimeBasedRollingPolicy(timeBasedRollingPolicy);

		RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<ILoggingEvent>();
		rollingFileAppender.setAppend(false);
		rollingFileAppender.setContext(context);
		rollingFileAppender.setEncoder(encoder);
		rollingFileAppender.setFile("logs/" + name + ".log");
		rollingFileAppender.setName(name + "Appender");
		rollingFileAppender.setPrudent(true); // set to true to release the hold
												// of log files.
		rollingFileAppender.setRollingPolicy(timeBasedRollingPolicy);
		rollingFileAppender.setTriggeringPolicy(timeBasedTriggeringPolicy);

		timeBasedRollingPolicy.setParent(rollingFileAppender);

		encoder.start();
		timeBasedRollingPolicy.start();

		rollingFileAppender.stop();
		rollingFileAppender.start();

		Logger logger = context.getLogger(name);
		logger.setLevel(level.convert());
		logger.setAdditive(false);
		logger.addAppender(rollingFileAppender);

		return logger;
	}

	public static void dispose(LogConnector connector) {
		if (connector != null) {
			Logger log = (Logger) connector.getLog();
			log.detachAndStopAllAppenders();
			connector.setOs(no);
		}
	}

	public static void dispose(org.slf4j.Logger log) {
		if (log != null) {
			loggers.remove(log.getName());
			if (log instanceof Logger)
				((Logger) log).detachAndStopAllAppenders();
		}
	}

	public static void dispose() {
		loggers.clear();
		context.reset();
	}
}
