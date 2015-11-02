package frank.incubator.testgrid.agent;

import static frank.incubator.testgrid.common.Constants.HUB_AGENT_STATUS;
import static frank.incubator.testgrid.common.Constants.HUB_TASK_COMMUNICATION;
import static frank.incubator.testgrid.common.Constants.HUB_TASK_PUBLISH;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import com.google.common.eventbus.EventBus;
import com.google.gson.reflect.TypeToken;

import frank.incubator.testgrid.agent.device.AndroidDeviceDetector;
import frank.incubator.testgrid.agent.device.DeviceDetector;
import frank.incubator.testgrid.agent.device.DeviceManager;
import frank.incubator.testgrid.agent.device.IosDeviceDetector;
import frank.incubator.testgrid.agent.message.InfoUpdater;
import frank.incubator.testgrid.agent.message.NotificationHandler;
import frank.incubator.testgrid.agent.message.TaskCommunicator;
import frank.incubator.testgrid.agent.message.TaskSubscriber;
import frank.incubator.testgrid.agent.plugin.PluginManager;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.StatusChangeNotifier;
import frank.incubator.testgrid.common.file.FileTransferDescriptor;
import frank.incubator.testgrid.common.file.FileTransferService;
import frank.incubator.testgrid.common.file.FileTransferTask;
import frank.incubator.testgrid.common.file.FileWatchService;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.BrokerDescriptor;
import frank.incubator.testgrid.common.message.BufferedMessageOutputStream;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.Notifier;
import frank.incubator.testgrid.common.message.Pipe;
import frank.incubator.testgrid.common.model.Agent;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.DeviceCapacity;
import frank.incubator.testgrid.common.model.DeviceRequirement;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.Test.Phase;

/**
 * Agent Main Class for Keep running the agent instance.
 * 
 * @author Wang Frank
 * 
 */
public final class AgentNode extends Thread {

	final public static int HOST_ONLY = 1;
	final public static int TASK_INCLUDE = 2;
	final public static int DEVICE_INCLUDE = 3;
	final public static int STATUS_INCLUDE = 4;

	private LogConnector log;

	private File workspace;

	private MessageHub hub;

	private DeviceManager dm;

	private DeviceDetector androidDetector;

	private DeviceDetector iosDetector;

	private boolean isRunning;

	private static Properties config = new Properties();

	private Map<Test, Long> reservedTests = new ConcurrentHashMap<Test, Long>();

	private Map<Test, Collection<Device>> reservedDevices = new ConcurrentHashMap<Test, Collection<Device>>();

	private Map<String, String> backupWorkspace = new ConcurrentHashMap<String, String>();

	private ExecutorService pool = Executors.newCachedThreadPool();

	private ScheduledExecutorService pluginPool = Executors.newScheduledThreadPool(2);

	private InfoUpdater taskUpdater;

	private EventBus taskStatusEventBus;

	private EventBus deviceBusyEventBus;

	private StatusChangeNotifier notifier;

	private boolean maintainMode;

	private HttpServer httpService;

	private FileTransferDescriptor ftDescriptor;

	private FileTransferService fts;

	private TaskCleaner cleaner;
	
	private Notifier mqNotifier;

	/**
	 * Store running tasks. Key was the test object. And Value is TaskExecutor
	 * instance.
	 */
	private Map<Test, TestExecutor> runningTests = new ConcurrentHashMap<Test, TestExecutor>();

	public AgentNode(String uri) {
		this.setName("AgentNode");
		log = LogUtils.get("AgentNode");
		log.info("EventBus Loaded...");
		taskStatusEventBus = new EventBus();
		deviceBusyEventBus = new EventBus();
		mqNotifier = new Notifier();
		log.info("Configuration Loaded...");
		loadConfigFromFile();
		log.info("Workspace initialzing...");
		workspace = new File(System.getProperty("user.dir"), Constants.DEFAULT_WORKSPACE_NAME);
		if (workspace.exists()) {
			if (CommonUtils.parseBoolean((String) getConfig(Constants.AGENT_CONFIG_DUP_WORKSPACE_BACKUP))) {
				workspace.renameTo(new File(workspace.getName() + "_" + System.currentTimeMillis()));
				workspace = new File(Constants.DEFAULT_WORKSPACE_NAME);
			} else {
				workspace.delete();
			}
			workspace.mkdirs();
		} else {
			workspace.mkdirs();
		}
		boolean supportShareZone = CommonUtils.parseBoolean((String) getConfig("support_share_zone"));
		File shareZone = null;
		if(supportShareZone) {
			shareZone = new File(workspace, "shareZone");
			if(!shareZone.exists() || !shareZone.isDirectory()) {
				shareZone.mkdirs();
			}
		}
		Set<String> exts = CommonUtils.fromJson((String) getConfig("share_zone_extions"), new TypeToken<Set<String>>(){}.getType());
		fts = new FileTransferService(hub, ftDescriptor, workspace, null, shareZone, exts);
		log.info("Message hub initialzing...");
		try {
			initMessageHub(uri);
		} catch (MessageException e) {
			throw new RuntimeException(e);
		}

		log.info("DeviceDetector start working...");
		notifier = new StatusChangeNotifier(hub, Constants.MSG_TARGET_AGENT);
		dm = new DeviceManager(Constants.ONE_MINUTE * 5, notifier);
		Map<String, Object> defaultAttrs = CommonUtils.fromJson(this.getConfig(Constants.AGENT_CONFIG_DEVICE_DEFAULT_ATTRS, String.class, "{}"),
				new TypeToken<Map<String, Object>>() {
				}.getType());
		if (defaultAttrs != null && !defaultAttrs.isEmpty()) {
			dm.getDefaultAttributes().putAll(defaultAttrs);
		}
		long scanInterval = CommonUtils.parseLong((String) getConfig(Constants.AGENT_CONFIG_DEVICE_DETECT_INTERVAL), Constants.ONE_MINUTE);
		if (CommonUtils.getOsType().equals(Constants.OS_MAC)) {
			iosDetector = new IosDeviceDetector(workspace, dm, scanInterval);
			iosDetector.start();
			if (!CommonUtils.parseBoolean((String) getConfig(Constants.AGENT_CONFIG_DISABLE_ANDROID_DETECTOR), false)) {
				androidDetector = new AndroidDeviceDetector(workspace, dm, scanInterval);
				androidDetector.start();
			}
		} else {
			androidDetector = new AndroidDeviceDetector(workspace, dm, scanInterval);
			androidDetector.start();
		}

		isRunning = true;
		this.start();
		log.info("Task scaner starting...");
		this.taskUpdater = new InfoUpdater(this);
		taskUpdater.start();

		this.cleaner = new TaskCleaner(workspace, CommonUtils.parseLong((String) getConfig(Constants.AGENT_CONFIG_WORKSPACE_TESTFOLDER_KEEPDAYS), 7L));
		cleaner.start();
		if (getConfig(Constants.AGENT_CONFIG_ENABLE_PLUGIN, Boolean.class, false))
			startPlugins();
		if (getConfig(Constants.AGENT_CONFIG_ENABLE_HTTPSERVER, Boolean.class, false))
			startHttpService(CommonUtils.availablePort(5451));
	}

	public DeviceDetector getAndroidDetector() {
		return androidDetector;
	}

	public DeviceDetector getIosDetector() {
		return iosDetector;
	}

	public Notifier getMqNotifier() {
		return mqNotifier;
	}

	/**
	 * Entry to start plugin search, load, and initialization.
	 */
	private void startPlugins() {
		for (String pluginName : PluginManager.pds.keySet()) {
			try {
				PluginManager.initialize(this, pluginName);
				log.info("Plugin[" + pluginName + "] initialized success.");
			} catch (Exception e) {
				log.error("Initialize plugin[" + pluginName + "] failed.", e);
			}
		}
	}

	/**
	 * Entry to start http service.
	 * 
	 * @param port
	 */
	public void startHttpService(int port) {
		HttpFileTransferTargetAcceptor.setWorkspace(this.workspace);
		PkgInstallController.setWorkspace(this.workspace);
		if (httpService == null)
			httpService = new HttpServer(this, port);
		httpService.start();
		// Request.Get( "http://localhost:"+port+"/upload?workspace=" +
		// workspace.getAbsolutePath() );
	}

	public void stopHttpService() {
		if (httpService != null)
			httpService.stop();
	}

	private void loadConfigFromFile() {
		try {
			config.load(CommonUtils.loadResources(Constants.AGENT_CONFIG_FILE, false));
			config.load(CommonUtils.loadResources(Constants.AGENT_CONFIG_FILE, true));
			if (CommonUtils.parseBoolean((String) getConfig(Constants.AGENT_CONFIG_DISABLE_HOSTNAME), false)) {
				CommonUtils.DISABLE_HOSTNAME = true;
			}
			ftDescriptor = CommonUtils.fromJson(config.getProperty(Constants.AGENT_CONFIG_FTILE_TRANSFER_DESCRIPTOR), FileTransferDescriptor.class);
			mqNotifier.setCharset(config.getProperty(Constants.AGENT_CONFIG_NOTIFICATION_CHARSET,"UTF-8".intern()));
			mqNotifier.setEnable(CommonUtils.parseBoolean(config.getProperty(Constants.AGENT_CONFIG_ENABLE_NOTIFICATION, "false"), false));
			mqNotifier.setNotifyUrl(config.getProperty(Constants.AGENT_CONFIG_NOTIFICATION_URL,""));
			mqNotifier.setReceivers(config.getProperty(Constants.AGENT_CONFIG_NOTIFICATION_RECEIVERS,""));
		} catch (Exception ex) {
			log.error("Load Agent Config File[ " + Constants.AGENT_CONFIG_FILE + " ] failed.", ex);
		}
		Path p = new File("").toPath();
		try {
			ConfigFileWatcher cfw = new ConfigFileWatcher(this, p);
			cfw.start();
		}catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Get agent specific configuration items.
	 * 
	 * @param key
	 * @return
	 */
	public static Object getConfig(String key) {
		if (key == null)
			return null;
		return config.get(key);
	}

	/**
	 * 
	 * @param key
	 * @param t
	 * @param backup
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T getConfig(String key, Class<T> t, T backup) {
		if (config != null) {
			if (t == Integer.class) {
				return (T) CommonUtils.getPropInt(config, key, (Integer) backup);
			} else if (t == Long.class) {
				return (T) CommonUtils.getPropLong(config, key, (Long) backup);
			} else if (t == Float.class) {
				return (T) CommonUtils.getPropFloat(config, key, (Float) backup);
			} else if (t == Double.class) {
				return (T) CommonUtils.getPropDouble(config, key, (Double) backup);
			} else if (t == Boolean.class) {
				return (T) CommonUtils.getPropBoolean(config, key, (Boolean) backup);
			} else if (t == Date.class) {
				return (T) CommonUtils.getPropDate(config, Constants.DEFAULT_DATE_PATTERN, (String) backup);
			} else {
				return (T) CommonUtils.getProp(config, key);
			}
		}
		return null;
	}

	public ExecutorService getPool() {
		return pool;
	}

	public MessageHub getHub() {
		return hub;
	}

	public void setHub(MessageHub hub) {
		this.hub = hub;
	}

	public DeviceManager getDm() {
		return dm;
	}

	public void setDm(DeviceManager dm) {
		this.dm = dm;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public void setRunning(boolean isRunning) {
		this.isRunning = isRunning;
	}

	public Map<Test, Long> getReservedTests() {
		return reservedTests;
	}

	public void setReserveTests(Map<Test, Long> reserveTests) {
		this.reservedTests = reserveTests;
	}

	public Map<Test, TestExecutor> getRunningTests() {
		return runningTests;
	}

	public void setRunningTests(Map<Test, TestExecutor> runningTests) {
		this.runningTests = runningTests;
	}

	public Properties getConfig() {
		return config;
	}

	public boolean isMaintainMode() {
		return maintainMode;
	}

	public void setMaintainMode(boolean maintainMode) {
		this.maintainMode = maintainMode;
	}

	public void setConfigItem(String key, Object value) {
		if (key != null && value != null)
			config.put(key, value);
	}

	public Map<Test, Collection<Device>> getReservedDevices() {
		return reservedDevices;
	}

	public LogConnector getLog() {
		return log;
	}

	public File getWorkspace() {
		return workspace;
	}

	public StatusChangeNotifier getNotifier() {
		return notifier;
	}

	public ScheduledExecutorService getPluginPool() {
		return pluginPool;
	}

	public Map<String, String> getBackupWorkspace() {
		return backupWorkspace;
	}

	public FileTransferService getFts() {
		return fts;
	}

	private void initMessageHub(String uri) throws MessageException {
		InputStream in = null;
		BrokerDescriptor[] bds = null;
		try {
			File mqConfigFile = new File(Constants.MQ_CONFIG_FILE);
			if (mqConfigFile.exists() && mqConfigFile.isFile() && mqConfigFile.length() > 0) {
				in = CommonUtils.loadResources(Constants.MQ_CONFIG_FILE, true);
				StringWriter sw = new StringWriter();
				IOUtils.copy(in, sw);
				bds = CommonUtils.fromJson(sw.toString(), new TypeToken<BrokerDescriptor[]>() {
				}.getType());
				hub = new MessageHub(Constants.MSG_TARGET_AGENT, mqNotifier, bds);
			} else if (uri != null) {
				hub = new MessageHub(uri, Constants.MSG_TARGET_AGENT, mqNotifier);
			} else {
				throw new MessageException("Can't initial messageHub, cos nor mqConfig file or uri provided.");
			}

			// agent status
			hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, HUB_AGENT_STATUS, null, null);
			/*
			 * hub.bindHandlers(Constants.BROKER_STATUS, Topic.class,
			 * HUB_TASK_STATUS, Constants.MSG_HEAD_TARGET + "='" +
			 * this.getAgentInfo(HOST_ONLY).getHost() + "'", new
			 * WaitTaskNotifier(this, taskStatusEventBus, deviceBusyEventBus));
			 */
			hub.bindHandlers(Constants.BROKER_TASK, Queue.class, HUB_TASK_COMMUNICATION, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_AGENT
					+ CommonUtils.getHostName() + "'", new TaskCommunicator(this));
			hub.bindHandlers(Constants.BROKER_TASK, Topic.class, HUB_TASK_PUBLISH, null, new TaskSubscriber(this, taskStatusEventBus, deviceBusyEventBus));
			hub.bindHandlers(Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_AGENT_NOTIFICATION, Constants.MSG_HEAD_TARGET + "='"
					+ Constants.MSG_TARGET_AGENT + CommonUtils.getHostName() + "'", new NotificationHandler(this));
			hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_TEST_STATUS, null, null);
			hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_DEVICE_STATUS, null, null);
			hub.bindHandlers(Constants.BROKER_FT, Queue.class, Constants.HUB_FILE_TRANSFER, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_AGENT
					+ CommonUtils.getHostName() + "'", fts);
			fts.setHub(hub);
		} catch (Exception ex) {
			throw new MessageException("Initialize MessageHub Failed.", ex);
		} finally {
			CommonUtils.closeQuietly(in);
		}

	}

	/**
	 * Get the agent node's pc status. Including cpu,memory,disk,network related
	 * basic information. Cross platform method.
	 * 
	 * @return Map, key was attribute, value will be the introduction.
	 */
	public Map<String, String> getNodeStatus() {
		try {
			return CommonUtils.getPcStatus();
		} catch (IOException e) {
			log.error("Get AgentNode status met Exception.", e);
		}
		return new HashMap<String, String>();
	}

	/**
	 * Generation Agent info obj for synchronization. Provide several info
	 * levels.
	 * <ol>
	 * <li>HOST_ONLY : only including host address related info.</li>
	 * <li>TASK_INCLUDE : including host address and task info.</li>
	 * <li>DEVICE_INCLUDE : including host address, task info, and device info.</li>
	 * </ol>
	 * 
	 * @param level
	 *            could be HOST_ONLY / TASK_INCLUDE / DEVICE_INCLUDE
	 * @return
	 */
	public Agent getAgentInfo(int level) {
		if (level <= 0 || level > 4)
			level = HOST_ONLY;
		Agent agent = new Agent(CommonUtils.getHostName());
		agent.getProperties().put(Constants.MQ_URL, this.getHub().getMqUrl().toLowerCase().trim());
		if (level > HOST_ONLY)
			agent.getRunningTasks().addAll(agent.getRunningTasks());
		if (level > TASK_INCLUDE)
			agent.getDevices().addAll(dm.listDevices());
		if (level > DEVICE_INCLUDE)
			agent.getProperties().putAll(getNodeStatus());
		return agent;
	}

	/**
	 * Check if the test requirements could be fulfilled at this agent node.
	 * 
	 * @param requirements
	 * @return
	 */
	public DeviceCapacity checkCondition(String testId, DeviceRequirement requirements) {
		if (testId == null)
			return null;
		if (requirements == null)
			requirements = new DeviceRequirement();
		DeviceCapacity capacity = new DeviceCapacity(testId, requirements);
		Collection<Device> devices = dm.listDevices();
		Device mainRequire = requirements.getMain();
		if (mainRequire == null)
			mainRequire = Device.createRequirement(null);
		Device refRequire = requirements.getRef();
		int avail = 0;
		int busy = 0;

		List<Device> candidates = new ArrayList<Device>();
		for (Device dut : devices) {
			if (mainRequire.match(dut))
				candidates.add(dut);
		}

		if (refRequire == null) {
			for (Device d : candidates) {
				if (d.getState() == Device.DEVICE_FREE && d.isConnected())
					avail++;
				else if (d.getState() != Device.DEVICE_LOST)
					busy++;
			}
			capacity.setAvailable(avail);
			capacity.setNeedWait(busy);
		} else { // complex situation.
			// first check if the main and ref have identity condition
			if (requirements.getMain().match(requirements.getRef())) {
				for (Device d : candidates) {
					if (d.getState() == Device.DEVICE_FREE && d.isConnected())
						avail++;
					else if (d.getState() != Device.DEVICE_LOST)
						busy++;
				}
				int rAvail = avail / 2;
				int rBusy = ((avail % 2) + busy) / 2;
				capacity.setAvailable(rAvail);
				capacity.setNeedWait(rBusy);
			} else {
				List<Device> candidatesRef = new ArrayList<Device>();
				for (Device dut : devices) {
					if (dut.match(mainRequire))
						candidatesRef.add(dut);
				}
				int avMain = 0;
				int avRef = 0;
				int buMain = 0;
				int buRef = 0;

				// first, find one free device from main candidate list.
				Iterator<Device> im = candidates.iterator();
				Iterator<Device> ir = candidatesRef.iterator();
				while (candidates.size() > 0) {
					while (im.hasNext()) {
						Device m = im.next();
						candidatesRef.remove(m);
						if (m.getState() == Device.DEVICE_FREE && m.isConnected())
							avMain++;
						else
							buMain++;

						break;
					}
					while (ir.hasNext()) {
						Device r = ir.next();
						candidatesRef.remove(r);
						if (r.getState() == Device.DEVICE_FREE && r.isConnected())
							avRef++;
						else
							buRef++;

						break;
					}
				}
				avail = Math.min(avMain, avRef);
				busy = Math.min(buMain, buRef);
				capacity.setAvailable(avail);
				capacity.setNeedWait(busy);
			}
		}

		return capacity;
	}

	/**
	 * Main loop for agent Node. Which will keep check hanged tasks and release
	 * resources.
	 * 
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		while (isRunning) {
			log.info("loop checking hang tests...");
			try {
				if (this.getConfig(Constants.AGENT_CONFIG_CHECK_HANG_TEST, Boolean.class, false))
					releaseHangTests();
				// if( this.getConfig(
				// Constants.AGENT_CONFIG_CHECK_INVALID_RESERVATION_DEVICE,
				// Boolean.class, false ) )
				// releaseHangDevices();
				TimeUnit.SECONDS.sleep(60);
			} catch (Throwable e) {
				log.error("AgentNode have got a exception.", e);
			}
		}
	}

	// public void releaseDevices(Collection<Device> devices) {
	// if (devices != null) {
	// for (Device d : devices) {
	// releaseDevice(d);
	// }
	// }
	// }

	// public void releaseDevice(Device device) {
	// if (device != null) {
	// device = dm.getDeviceBy(Constants.DEVICE_SN,
	// device.getAttribute(Constants.DEVICE_SN));
	// device.setRole(Device.ROLE_MAIN);
	// device.setState(Device.DEVICE_FREE);
	// device.setTaskStatus("");
	// device.setPreState(Device.DEVICE_FREE);
	// log.info("Releasing device:" + device.getId());
	// }
	// }

	/**
	 * Check and Release tasks which didn't start and exceed the maximum
	 * timeout.
	 */
	private void releaseHangTests() {
		long curr = System.currentTimeMillis();
		long timeout = getConfig(Constants.AGENT_CONFIG_HANG_TEST_TIMEOUT, Long.class, Constants.ONE_MINUTE * 10);
		try {
			for (Test t : this.reservedTests.keySet()) {
				long cr = this.reservedTests.get(t);
				if ((curr - cr) > timeout) {
					log.warn("Reserved Test[" + t.getTaskID() + ":::" + t.getId() + " has reached the *HANG Test* criteria" + CommonUtils.convert(timeout) + "], should be cancelled");
					int result = this.cancelTest(t.getId(), "This test has reached the *HANG Test* criteria[" + CommonUtils.convert(timeout) + "], cancelled.");
					log.info("Cancel Test result:" + result + ".Release Reserved test[" + t.getTaskID() + Constants.TASK_TEST_SPLITER + t.getId() + "] , because it has been waiting over "
							+ CommonUtils.convert(curr - cr));
				}
			}
		}catch(Throwable t1) {
			log.error("Catch Exception when checking hang reserve test.", t1);
		}
		
		try {
			for (Test t : this.runningTests.keySet()) {
				TestExecutor te = this.runningTests.get(t);
				long cr = te.getStartTime();
				if ((curr - cr) > te.getTimeout()) {
					log.warn("Running Test[" + t.getTaskID() + ":::" + t.getId() + " has reached the *HANG Test* criteria" + CommonUtils.convert(te.getTimeout()) + "], should be cancelled");
					int result = this.cancelTest(t.getId(), "This test has reached the *HANG Test* criteria[" + CommonUtils.convert(te.getTimeout()) + "], cancelled.");
					log.info("Cancel Test result:" + result + ".Release Running test[" + t.getTaskID() + Constants.TASK_TEST_SPLITER + t.getId() + "] , because it has been waiting over "
							+ CommonUtils.convert(curr - cr));
				}
			}
		}catch(Throwable t2) {
			log.error("Catch Exception when checking hang running test.", t2);
		}

	}

	/**
	 * Reserve required device of give task, and change the device state from
	 * free to reserved.
	 * 
	 * @param task
	 * @return
	 */
	/*
	 * public Collection<Device> reserveDevices(Test test, DeviceRequirement
	 * requirements) { List<Device> devices = new
	 * ArrayList<Device>(dm.listDevices()); Collections.shuffle(devices);
	 * Collection<Device> candidates = new ArrayList<Device>(); Device require =
	 * requirements.getMain(); for (Device dut : devices) { if
	 * (require.match(dut) && !candidates.contains(dut) && dut.getState() ==
	 * Device.DEVICE_FREE) { if(!dut.isConnected()) { Device d =
	 * dm.getDeviceBy(Constants.DEVICE_ID, dut.getId());
	 * d.setState(Device.DEVICE_LOST_TEMP); continue; }
	 * dut.setRole(Device.ROLE_MAIN); dut.setState(Device.DEVICE_RESERVED);
	 * dut.setTaskStatus
	 * (test.getTaskID()+Constants.TASK_TEST_SPLITER+test.getId());
	 * candidates.add(dut); break; } }
	 * 
	 * if (candidates.isEmpty()) return null;
	 * 
	 * Device requireRef = requirements.getRef(); if (requireRef != null) { for
	 * (Device dut : devices) { if (requireRef.match(dut) &&
	 * !candidates.contains(dut) && dut.getState() == Device.DEVICE_FREE) {
	 * dut.setRole(Device.ROLE_REF); dut.setState(Device.DEVICE_RESERVED);
	 * dut.setTaskStatus(test.getId()); candidates.add(dut); break; } } if
	 * (candidates.size() < 2) return null; } return candidates; }
	 */

	/**
	 * Reserve the task in this agent node, actually including task reserve and
	 * device reserve.
	 * 
	 * @param task
	 * @return
	 */
	public boolean reserveForTest(Test test, DeviceRequirement requirement) {
		if (test == null) {
			log.error("Received an NUll test request");
			return false;
		} else {
			boolean anyDeviceFree = false;
			for (Device d : dm.listDevices()) {
				if (d.getState() == Device.DEVICE_FREE) {
					anyDeviceFree = false;
					break;
				}
			}
			if (anyDeviceFree) {
				log.info("No devices free now, just return");
				return false;
			}
			long current = System.currentTimeMillis();
			if (requirement == null)
				requirement = new DeviceRequirement();
			if (!reservedTests.keySet().contains(test)) {
				if (reservedDevices.containsKey(test)) {
					Collection<Device> dss = reservedDevices.get(test);
					log.warn("This device have already been reserved before. the device been reserved is :" + CommonUtils.toJson(dss));
					reservedTests.put(test, current);
					test.setPhase(Phase.PREPARING);
					return true;
				}
				Collection<Device> devices = dm.reserveDevices(test, requirement);
				if (null != devices) {
					reservedTests.put(test, current);
					/*
					 * if(reservedDevices.containsKey(test)) {
					 * Collection<Device> ds = reservedDevices.remove(test);
					 * if(ds != null && !ds.isEmpty()) {
					 * log.info("Begin to release devices for test:" +
					 * test.getId()+"("+test.getTaskID()+
					 * ") because test have already been reserved before. the previous task was:"
					 * ); dm.releaseDevices(ds); } }
					 */
					this.reservedDevices.put(test, devices);
					test.setPhase(Phase.PREPARING);
					StringBuilder sb = new StringBuilder();
					if(devices != null) {
						for(Device d: devices) {
							sb.append(d.getId()).append(",");
						}
					}
					log.info("Reserve Devices for Test success! Test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + ",device is:" + sb.toString());
					return true;
				} else {
					log.warn("Can't reserve Devices for Test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId()); //+ ", current status is :" + CommonUtils.toJson(this.dm.listDevices())
					return false;
				}
			} else {
				log.warn("Duplicated reserved Test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + " which have been reserved @:" + CommonUtils.parseTimestamp(reservedTests.get(test)));
				return true;
			}

		}
	}

	/**
	 * @deprecated Still not ready for use. Good to have method. estimate the
	 *             waiting time for assigning task.
	 * @param requirements
	 *            device requirements.
	 * @return
	 */
	protected String getWaitEstimation(DeviceRequirement requirements) {
		return "Unknown";
	}

	/**
	 * Check if assigned testId is running already.
	 * 
	 * @param testId
	 * @return
	 */
	public boolean isTestRunning(String testId) {
		for (Test test : this.runningTests.keySet()) {
			if (test.getId().equals(testId)) {
				return true;
			}
		}
		return false;
	}

	public Test getRunningTestById(String testId) {
		for(Test t : this.runningTests.keySet()) {
			if(t.getId().equals(testId)) {
				return t;
			}
		}
		return null;
	}
	
	public Test getReservedTestById(String testId) {
		for(Test t : this.reservedTests.keySet()) {
			if(t.getId().equals(testId)) {
				return t;
			}
		}
		return null;
	}
	
	/**
	 * Start assigned task, created TaskExecutor, and encapsulate into a
	 * FutureTask.
	 * 
	 * @param task
	 */
	public void startTest(String testId, String target) {
		if (isTestRunning(testId)) {
			log.warn("Assigned test[" + testId + "] request by " + target + " have already been started. No need to restart.");
			return;
		}
		log.info("Test:[" + testId + "] is going to start...");
		Test test = null;
		for (Test t : reservedTests.keySet()) {
			if (t.getId().equals(testId)) {
				test = t;
				test.setPhase(Phase.STARTED);
				break;
			}
		}
		if (test == null) {
			log.error("Can't find Test:[" + testId + "] from reserved devices list. Maybe not in current Agent?");
			return;
		}
		reservedTests.remove(test);
		Pipe pipe = this.hub.getPipe(Constants.HUB_TEST_STATUS);
		OutputStream tracker = null;
		if (test.isSendOutput()) {
			tracker = new BufferedMessageOutputStream(pipe.getParentBroker().getSession(), pipe.getProducer(), Constants.MSG_HEAD_TESTID, test.getId(),
					LogUtils.getLogger("Exec_"+test.getTaskID()+"_" + test.getId()));
		}
		Collection<Device> rDevices = reservedDevices.get(test);
		TestExecutor te = new TestExecutor(test, this, rDevices, tracker, test.getTimeout(), target);
		this.runningTests.put(test, te);
		FutureTask<Boolean> future = new FutureTask<Boolean>(te, null);
		getPool().execute(future);
		test.setPhase(Phase.STARTED);
		log.info("Test:[" +test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + "] is started.");
	}

	/**
	 * Create the workspace for assigned test.
	 * 
	 * @param test
	 * @return
	 */
	public File createTestWorkspace(Test test) {
		File file = new File(workspace, test.getId());
		if (file.exists()) {
			if (CommonUtils.parseBoolean((String) config.get(Constants.AGENT_CONFIG_DUP_WORKSPACE_BACKUP))) {
				file.renameTo(new File(workspace, file.getName() + "_" + System.currentTimeMillis()));
				file = new File(workspace, test.getId());
			} else {
				try {
					FileUtils.deleteDirectory(file);
				} catch (IOException e) {
					log.error("Clean duplicated test workspace failed. path=" + file.getAbsolutePath(), e);
					log.warn("Try to use a different test workspace for this test.");
					file = new File(workspace, test.getId() + "_" + CommonUtils.generateToken(3));
					backupWorkspace.put(test.getId(), file.getAbsolutePath());
				}
			}
		}
		file.mkdirs();
		return file;
	}

	/**
	 * Notify agentNode that some test have been finished. Tasks should be done
	 * include: 1. send result back to test client. 2. release reserved devices.
	 * 3. remove monitors/listeners.
	 * 
	 * @param test
	 *            Test under executed and to be finished.
	 * @param phase
	 *            Final execution result.
	 */
	public void finishTest(TestExecutor te) {
		Test test = te.getTest();
		log.info("Test[" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + "] finished executing with Phase:" + test.getPhase());
		try {
			// first release devices.
			log.info("Releasing devices for test[" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + "]");
			Collection<Device> devices = te.getDevices();
			log.info("Begin to release devices for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + " because test was finished.");
			dm.releaseDevices(devices);
			devices.clear();
			this.reservedDevices.remove(test);
			// remove from global monitor lists.
			// check if it was a task own by this agent node.
			if (this.runningTests.containsKey(test)) {
				runningTests.remove(test);
			}

			if (this.reservedTests.containsKey(test)) {
				// still in reserve state, just cancel
				reservedTests.remove(test);
			}

			Message m = this.hub.createMessage(Constants.BROKER_TASK);
			Pipe taskPipe = hub.getPipe(Constants.HUB_TASK_COMMUNICATION);
			Pipe testStatusPipe = hub.getPipe(Constants.HUB_TEST_STATUS);
			setProperty(m, Constants.MSG_HEAD_TARGET, te.getClientTarget());
			setProperty(m, Constants.MSG_HEAD_TASKID, te.getTest().getTaskID());
			setProperty(m, Constants.MSG_HEAD_TESTID, te.getTest().getId());
			setProperty(m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FINISHED);

			// final File resultFile = new File(te.getWorkspace(),
			// te.getTest().getResultsFilename());
			List<File> resultFiles = new ArrayList<File>();
			Map<String, Boolean> missingFiles = new HashMap<String, Boolean>();
			boolean isSucc = validateResultFiles(te, resultFiles, missingFiles);
			if (!isSucc)
				log.info("Not All mandatory result files available.");
			if (!missingFiles.isEmpty()) {
				log.warn("Test[" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + "] missing some result files:" + CommonUtils.toJson(missingFiles));
			}

			if (!resultFiles.isEmpty()) {
				log.info("Test[" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + "] have been finished, prepare for sending results.");
				// log.info("Result File exist with length:" +
				// resultFile.length());
				FileTransferTask ftt = new FileTransferTask(Constants.MSG_TARGET_AGENT + CommonUtils.getHostName(), te.getClientTarget(), te.getTest()
						.getTaskID(), te.getTest().getId(), resultFiles);
				fts.sendTo(ftt);
				log.info("Begin to send test result back to test client.TestId:" + ftt.getTestId() + ", FtTaskId[" + ftt.getId() + "]");
			} else {
				log.error("Test[" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + "] failed because some of mandatory result files missing.");
				setProperty(m, Constants.MSG_HEAD_TEST_RESULT, Constants.MSG_TEST_FAIL);
				if (test.getPhase() == Phase.FINISHED)
					setProperty(m, Constants.MSG_HEAD_ERROR, "ResultFiles: " + CommonUtils.toJson(missingFiles) + " didn't exist");
				else
					setProperty(m, Constants.MSG_HEAD_ERROR, te.getFailureInfo() + ", and ResultFiles: " + CommonUtils.toJson(missingFiles) + " didn't exist");
				taskPipe.getProducer().send(m);
				testStatusPipe.getProducer().send(m);
			}
			markIfCleanWorkspace(te);
		} catch (Exception ex) {
			log.error("Handle Finished/Failed Test got exception.", ex);
		}
	}

	private boolean validateResultFiles(TestExecutor te, List<File> resultFiles, Map<String, Boolean> missingFiles) {
		Test t = te.getTest();
		boolean result = true;
		boolean isMandatory = false;
		for (String resultFile : t.getResultFiles().keySet()) {
			isMandatory = t.getResultFiles().get(resultFile);
			FileFilter fileFilter = new WildcardFileFilter(resultFile);
			File[] fs = te.getWorkspace().listFiles(fileFilter);
			if (fs != null && fs.length > 0) {
				for (File f : fs) {
					if (!resultFiles.contains(f))
						resultFiles.add(f);
				}
			} else {
				missingFiles.put(resultFile, isMandatory);
				if (isMandatory)
					result = false;
			}

		}
		return result;
	}

	private void markIfCleanWorkspace(TestExecutor te) {
		if (te.getTest().getPhase() == Phase.FAILED) {
			File f = new File(te.getWorkspace(), "dontdelete.tmp");
			if (!f.exists()) {
				try {
					f.createNewFile();
				} catch (IOException e) {
					log.error("Create Don't clean file flag failed.", e);
				}
			}
		}
	}

	/**
	 * Cancel the assigned task, if the task is still been reserved but not
	 * running, just release the reservation, if the task is running, stop it .
	 * 
	 * @param task
	 * @return
	 */
	public int cancelTest(String testId, String reason) {
		Test test = null;
		log.info("Start to cancel a test:{} for reason:{}", testId, reason);

		for (Test t : runningTests.keySet()) {
			if (t.getId().equals(testId)) {
				test = t;
				break;
			}
		}

		if (test == null) {
			for (Test t : this.reservedTests.keySet()) {
				if (t.getId().equals(testId)) {
					test = t;
					break;
				}
			}
		}
		int result = Constants.TASK_CANCEL_SUCC;

		if (test != null) {
			test.setPhase(Phase.STOPPED);
			// release all the devices first.
			Collection<Device> devices = this.reservedDevices.remove(test);
			log.info("Begin to release devices for test:{}:::{}  because test was cancelled.", test.getTaskID(), test.getId());
			if (devices != null && !devices.isEmpty()) {
				dm.releaseDevices(devices);
				devices.clear();
			}

			// check if it was a task own by this agent node.
			if (this.runningTests.containsKey(test)) {
				TestExecutor te = runningTests.remove(test);
				te.stop(reason);
			}

			if (this.reservedTests.containsKey(test)) {
				// still in reserve state, just cancel
				reservedTests.remove(test);
			}
		} else {
			log.warn("The Test["  + testId + "] didn't in this agent node, maybe it have been finished or not running on this node.");
			result = Constants.TASK_CANCEL_NOTFOUND;
		}
		return result;
	}

	/**
	 * Clean up assigned test workspace.
	 * 
	 * @param testId
	 */
	public void cleanUpTest(String testId) {
		try {
			File tw = new File(workspace, testId);
			if (tw.exists() && tw.isDirectory()) {
				File flag = new File(tw, "dontdelete.tmp");
				if (!flag.exists())
					FileUtils.deleteDirectory(tw);
			}
		} catch (Exception ex) {
			log.error("Cleanup test workspace failed. testid=" + testId, ex);
		}
	}

	/**
	 * Shutdown immediately.
	 */
	public void shutdown(String reason) throws Exception {
		System.err.println("Stop Agent service because of :" + reason);
		System.exit(-1);
	}

	/**
	 * Bootstrap entry.
	 * 
	 * @param args
	 */
	@SuppressWarnings("unused")
	public static void main(String[] args) {
		String uri = null;
		if (args == null || args.length == 0) {
			File configFile = new File(Constants.MQ_CONFIG_FILE);
			if (!configFile.exists() || !configFile.isFile() || configFile.length() == 0) {
				System.err
						.println("Please start Testgrid Agent with assigned Messagq Broker Address. E.g.: tcp://10.220.120.16:61616, Or else set Message Service config info to mqservice.config in same folder.");
				System.exit(0);
			}
		} else {
			uri = args[0];
		}
		try {
			final AgentNode node = new AgentNode(uri);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	static class ConfigFileWatcher extends FileWatchService {
		private AgentNode agent;
		public ConfigFileWatcher(AgentNode node,Path dir) throws IOException {
			super(dir, false);
			agent = node;
		}
		
		@Override
		public void handleEvent(WatchEvent<Path> event, Path path) {
			Kind<Path> kind = event.kind();
			if (path.getFileName().toString().toLowerCase().equals(Constants.AGENT_CONFIG_FILE)) {
				if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
					try {
						config = new Properties();
						config.load(CommonUtils.loadResources(Constants.AGENT_CONFIG_FILE, true));
						Map<String, Object> defaultAttrs = CommonUtils.fromJson(agent.getConfig(Constants.AGENT_CONFIG_DEVICE_DEFAULT_ATTRS, String.class, "{}"),
								new TypeToken<Map<String, Object>>() {
								}.getType());
						if (defaultAttrs != null && !defaultAttrs.isEmpty()) {
							agent.getDm().getDefaultAttributes().clear();
							agent.getDm().getDefaultAttributes().putAll(defaultAttrs);
						}
						long scanInterval = CommonUtils.parseLong((String) getConfig(Constants.AGENT_CONFIG_DEVICE_DETECT_INTERVAL), Constants.ONE_MINUTE);
						if(agent.getIosDetector() != null) {
							agent.getIosDetector().setWaitingSchedule(scanInterval);
						}else if(agent.getAndroidDetector() != null) {
							agent.getAndroidDetector().setWaitingSchedule(scanInterval);
						}
						if(agent.getMqNotifier() != null) {
							agent.getMqNotifier().setCharset(config.getProperty(Constants.AGENT_CONFIG_NOTIFICATION_CHARSET,"UTF-8".intern()));
							agent.getMqNotifier().setEnable(CommonUtils.parseBoolean(config.getProperty(Constants.AGENT_CONFIG_ENABLE_NOTIFICATION, "false"), false));
							agent.getMqNotifier().setNotifyUrl(config.getProperty(Constants.AGENT_CONFIG_NOTIFICATION_URL,""));
							agent.getMqNotifier().setReceivers(config.getProperty(Constants.AGENT_CONFIG_NOTIFICATION_RECEIVERS,""));
						}
						agent.getFts().setSupportShareZone(CommonUtils.parseBoolean((String) getConfig("support_share_zone")));
					} catch (Exception ex) {
						log.error("Load Agent Config File[ " + Constants.AGENT_CONFIG_FILE + " ] failed.", ex);
					}
				}
			}
		}
		
	}
}
