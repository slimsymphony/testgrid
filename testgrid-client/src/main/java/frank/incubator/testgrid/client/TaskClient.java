package frank.incubator.testgrid.client;

import static frank.incubator.testgrid.common.Constants.HUB_TASK_COMMUNICATION;
import static frank.incubator.testgrid.common.Constants.HUB_TASK_PUBLISH;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;

import frank.incubator.testgrid.client.message.FakeTestListener;
import frank.incubator.testgrid.client.message.NotificationHandler;
import frank.incubator.testgrid.client.message.TaskClientCommunicator;
import frank.incubator.testgrid.client.message.TestExecutionChecker;
import frank.incubator.testgrid.client.message.TestListener;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.StatusChangeNotifier;
import frank.incubator.testgrid.common.file.FileTransferDescriptor;
import frank.incubator.testgrid.common.file.FileTransferService;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.BrokerDescriptor;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageFilter;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.message.Pipe;
import frank.incubator.testgrid.common.model.Agent;
import frank.incubator.testgrid.common.model.Client;
import frank.incubator.testgrid.common.model.DeviceCapacity;
import frank.incubator.testgrid.common.model.DeviceRequirement;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.Test.Phase;

/**
 * Task Client, intelligent client to publish a task and keep tracking the task
 * process. Who is controlling the overall task life-cycle working with message
 * responser and other components.
 * 
 * @author Wang Frank
 *
 */
public class TaskClient extends Thread {

	private LogConnector log;

	private Client client;

	private Task task;

	private String taskID;

	private String owner;

	private long start;

	private long serialPartStart;

	private long end;

	private long lastPublishTime;

	private MessageHub hub;

	private Map<String, Map<String, DeviceCapacity>> agentCandidates;

	private Map<Test, TestListener> testSlots;

	private Map<String, Integer> failCounter;

	private Map<String, String> executeDevices = new ConcurrentHashMap<String, String>();

	private Agent executeAgent;

	private TestStrategy strategy;

	private long publishTimeout = Constants.ONE_MINUTE * 10;

	private long reserveTimeout = Constants.ONE_MINUTE * 30;

	private long taskTimeout = Constants.ONE_HOUR * 8;

	private int maxRetryTimes = 0;

	private Semaphore lock = new Semaphore(1);

	private File workspace;

	private OutputStream tracker;
	
	private ConcurrentHashMap<String, Long> publishQueue = new ConcurrentHashMap<String, Long>();
	
	private ExecutorService delayPublishPool = Executors.newCachedThreadPool(new ThreadFactory() {
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setName("DelayPublisher");
			t.setDaemon(true);
			return t;
		}
	});
	
	private ExecutorService pool = Executors.newCachedThreadPool(new ThreadFactory() {
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setName("Checker-" + t.getName());
			t.setDaemon(true);
			return t;
		}
	});

	private boolean running = true;

	private StatusChangeNotifier notifier;

	private TestExecutionChecker checker;

	private FileTransferService fts;

	private boolean serialized = false;

	private int maxConcurrentSize = 1;

	private File resultFolder = null;

	public TaskClient(String uri, Task task, TestStrategy strategy, File workspace, OutputStream tracker, FileTransferDescriptor ftDescriptor,
			boolean activeHeartbeatCheck, boolean isSerialized, BrokerDescriptor... descs) {
		this.setDaemon(true);
		this.setName("TaskClient");
		this.setSerialized(isSerialized);
		this.task = task;
		this.strategy = strategy;
		if (!workspace.exists())
			workspace.mkdirs();
		this.workspace = workspace;
		resultFolder = new File(workspace, "results");
		if (!resultFolder.exists())
			resultFolder.mkdirs();
		this.tracker = tracker;
		log = LogUtils.get("TaskClient", tracker);
		start = System.currentTimeMillis();
		serialPartStart = start;
		checker = new TestExecutionChecker(this, tracker, activeHeartbeatCheck);
		fts = new FileTransferService(hub, ftDescriptor, workspace, tracker);
		try {
			if (descs != null && descs.length > 0)
				initMessageHub(descs);
			else if (uri != null && !uri.isEmpty())
				initMessageHub(uri);
			else
				throw new RuntimeException("Initialize MessageHub met exception. Both URI and BrokerDescriptor are Empty.");
		} catch (MessageException e) {
			throw new RuntimeException("Initialize MessageHub met exception.", e);
		}
		notifier = new StatusChangeNotifier(hub, Constants.MSG_TARGET_CLIENT);
		agentCandidates = new ConcurrentHashMap<String, Map<String, DeviceCapacity>>();
		failCounter = new ConcurrentHashMap<String, Integer>();
		testSlots = new ConcurrentHashMap<Test, TestListener>();
		long current = System.currentTimeMillis();
		task.setStartTime(current);
		this.client = new Client();
		this.client.addObserver(notifier);
		this.client.setTaskId(this.task.getId());
		this.client.setHost(CommonUtils.getHostName());
		this.client.setStatus(Client.Status.INUSE);
		this.task.addObserver(notifier);
		log.info("Test slots loaded, there are " + testSlots.size() + " test under executing.");
		log.info("Current Task have " + task.getTestsuite().getTests().size() + " tests to be executed.");
		pool.execute(checker);
	}

	public TaskClient(String uri, Task task, TestStrategy strategy, File workspace, OutputStream tracker, FileTransferDescriptor ftDescriptor,
			boolean activeHeartbeatCheck, BrokerDescriptor... descs) {
		this(uri, task, strategy, workspace, tracker, ftDescriptor, activeHeartbeatCheck, false, descs);
	}

	private void initMessageHub(String uri) throws MessageException {
		hub = new MessageHub(uri, Constants.MSG_TARGET_CLIENT);
		hub.bindHandlers(Constants.BROKER_TASK, Topic.class, HUB_TASK_PUBLISH, null, null);
		hub.bindHandlers(Constants.BROKER_TASK, Queue.class, HUB_TASK_COMMUNICATION, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_CLIENT
				+ CommonUtils.getHostName() + "' AND " + Constants.MSG_HEAD_TASKID + "='" + task.getId() + "'", new TaskClientCommunicator(this, this.lock,
				tracker));
		hub.bindHandlers(Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_CLIENT_NOTIFICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "'", new NotificationHandler(this));
		hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_TEST_STATUS, null, new MessageListenerAdapter("ClientTestStatusListener", tracker));
		hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_CLIENT_STATUS, null, null);
		hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_TASK_STATUS, null, null);
		hub.bindHandlers(Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_AGENT_NOTIFICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "'", checker);
		hub.bindHandlers(Constants.BROKER_FT, Queue.class, Constants.HUB_FILE_TRANSFER, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_CLIENT
				+ CommonUtils.getHostName() + "' AND " + Constants.MSG_HEAD_TASKID + "='" + task.getId() + "'", fts);
		fts.setHub(hub);
	}

	private void initMessageHub(BrokerDescriptor[] descs) throws MessageException {
		hub = new MessageHub(Constants.MSG_TARGET_CLIENT, descs);
		hub.bindHandlers(Constants.BROKER_TASK, Topic.class, HUB_TASK_PUBLISH, null, null);
		hub.bindHandlers(Constants.BROKER_TASK, Queue.class, HUB_TASK_COMMUNICATION, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_CLIENT
				+ CommonUtils.getHostName() + "' AND " + Constants.MSG_HEAD_TASKID + "='" + task.getId() + "'", new TaskClientCommunicator(this, this.lock,
				tracker));
		hub.bindHandlers(Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_CLIENT_NOTIFICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "'", new NotificationHandler(this));
		hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_TEST_STATUS, null, new MessageListenerAdapter("ClientTestStatusListener", tracker));
		hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_CLIENT_STATUS, null, null);
		hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_TASK_STATUS, null, null);
		hub.bindHandlers(Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_AGENT_NOTIFICATION, Constants.MSG_HEAD_TARGET + "='"
				+ Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName() + "'", checker);
		hub.bindHandlers(Constants.BROKER_FT, Queue.class, Constants.HUB_FILE_TRANSFER, Constants.MSG_HEAD_TARGET + "='" + Constants.MSG_TARGET_CLIENT
				+ CommonUtils.getHostName() + "' AND " + Constants.MSG_HEAD_TASKID + "='" + task.getId() + "'", fts);
		fts.setHub(hub);
	}

	public Map<String, Map<String, DeviceCapacity>> getAgentCandidates() {
		return agentCandidates;
	}

	public Map<Test, TestListener> getTestSlots() {
		return testSlots;
	}

	public Agent getExecuteAgent() {
		return executeAgent;
	}

	public void setExecuteAgent(Agent executeAgent) {
		this.executeAgent = executeAgent;
	}

	public int getMaxRetryTimes() {
		return maxRetryTimes;
	}

	public void setMaxRetryTimes(int maxRetryTimes) {
		this.maxRetryTimes = maxRetryTimes;
	}

	public long getPublishTimeout() {
		return publishTimeout;
	}

	public void setPublishTimeout(long publishTimeout) {
		this.publishTimeout = publishTimeout;
	}

	public long getReserveTimeout() {
		return reserveTimeout;
	}

	public void setReserveTimeout(long reserveTimeout) {
		this.reserveTimeout = reserveTimeout;
	}

	public long getTaskTimeout() {
		return taskTimeout;
	}

	public void setTaskTimeout(long taskTimeout) {
		this.taskTimeout = taskTimeout;
	}

	public MessageHub getHub() {
		return hub;
	}

	public Task getTask() {
		return task;
	}

	public void setTask(Task task) {
		this.task = task;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public long getStart() {
		return start;
	}

	public void setStart(long start) {
		this.start = start;
	}

	public long getEnd() {
		return end;
	}

	public void setEnd(long end) {
		this.end = end;
	}

	public TestStrategy getStrategy() {
		return strategy;
	}

	public File getWorkspace() {
		return workspace;
	}

	public ExecutorService getPool() {
		return pool;
	}

	public StatusChangeNotifier getNotifier() {
		return notifier;
	}

	public FileTransferService getFts() {
		return fts;
	}

	public boolean isSerialized() {
		return serialized;
	}

	public void setSerialized(boolean serialized) {
		this.serialized = serialized;
	}

	public int getMaxConcurrentSize() {
		return maxConcurrentSize;
	}

	public void setMaxConcurrentSize(int maxConcurrentSize) {
		this.maxConcurrentSize = maxConcurrentSize;
	}

	public File getResultFolder() {
		return resultFolder;
	}

	@Override
	public void run() {
		while (running) {
			long current = System.currentTimeMillis();
			try {
				if (running) {
					if((current- lastPublishTime) > (Constants.ONE_MINUTE * 1)) {
						checkPublish();
						lastPublishTime = current;
					}
				}
				if (running)
					checkHangTests();
				if (running)
					timeoutCheck();
				if (running)
					TimeUnit.SECONDS.sleep(30);
			} catch (Exception e) {
				log.error("Check task progress met exception.", e);
			}
		}
		// LogUtils.dispose(log);
	}

	private void checkPublish() {
		if (testSlots.isEmpty())
			return;

		if (this.isSerialized()) {
			long timeout = System.currentTimeMillis() - serialPartStart;
			if (this.getWaitingSerializedTestsNumber() == this.testSlots.size() && timeout < this.publishTimeout && agentCandidates.isEmpty()) {
				startNextSerializedTest();
			} else {
				Test t = this.getFirstNotInWaitingStateSerializedTest();
				if (t != null && t.getPhase() == Phase.PENDING && this.getWaitingSerializedTestsNumber() < this.testSlots.size()
						&& timeout >= this.publishTimeout && agentCandidates.isEmpty()) {
					this.cancelTest(t, "Can't find required devices for test:" + t, true);
				}
			}
			for (Test t : testSlots.keySet()) {
				TestListener tl = testSlots.get(t);
				if (tl instanceof FakeTestListener) {
					FakeTestListener ftl = (FakeTestListener) tl;
					if (!ftl.isSerializeWaiting()) {
						try {
							this.publishTests(t);
						} catch (MessageException e) {
							log.error("publish test failed. test:" + t.getId(), e);
						}
					}
				}
			}
		} else {
			long timeout = System.currentTimeMillis() - start;
			if (this.getWaitingTestsNumber() == this.testSlots.size() && timeout >= this.publishTimeout && agentCandidates.isEmpty()) {
				this.cancelTask("Can't find capable devices during publish timeout interval[" + CommonUtils.convert(timeout));
			}
			if (!isAllTestStart()) {
				log.warn("Still have " + getWaitingTestsNumber() + "tests to be executed!");
				List<Test> unstarted = new ArrayList<Test>();
				for (Test t : this.testSlots.keySet()) {
					if (testSlots.get(t) instanceof FakeTestListener) {
						log.warn("Test: " + t.getId() + " is waiting.");
						unstarted.add(t);
					} else if (t.getPhase().equals(Phase.PENDING)) {
						log.warn("Test: " + t.getId() + " is still in pending phase, try to republish.");
						unstarted.add(t);
					}
				}
				if (!unstarted.isEmpty() && (System.currentTimeMillis() - lastPublishTime) >= Constants.ONE_SECOND * 30) {
					log.info("Not all the sub tests started, still need to publish task once again!");
					try {
						this.publishTests(unstarted);
					} catch (MessageException e) {
						log.error("Trying to republishing task failed. task:" + task, e);
					}
				}
			}
		}
	}

	private void timeoutCheck() {
		log.info("Checking timeout ...");
		if ((System.currentTimeMillis() - start) > taskTimeout) {
			log.warn("Timeout ! Stop the task.");
			// taskFinished();
			cancelTask("Task timeout reach :" + CommonUtils.convert(taskTimeout));
			this.end = System.currentTimeMillis();
		}
	}

	/**
	 * Try to check the test phase and based on the test timeout find if there
	 * are hang tests exist.
	 */
	private void checkHangTests() {
		log.info("checking hang tests...");
		long current = System.currentTimeMillis();
		for (Test test : testSlots.keySet()) {
			TestListener tl = testSlots.get(test);
			if (tl instanceof FakeTestListener) {
				continue;
			} else {
				long interval = current - tl.getStart();
				if (interval > test.getTimeout()) {
					log.warn("Going to cancel suspect test execution timeout, test=" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + ", " + "start point:" + CommonUtils.parseTimestamp(tl.getStart())
							+ ", current duration : " + CommonUtils.convert(interval));
					cancelTest(test,
							"Execute timeout. Start from " + CommonUtils.parseTimestamp(tl.getStart()) + ", timeout duration:" + CommonUtils.convert(interval), true);
				}
			}
		}

		if (testSlots.isEmpty()) {
			log.warn(" all test finished and no hang test. Could end this task client.");
			taskFinished();
		}
	}

	/**
	 * Cancel assigned running test.
	 * 
	 * @param test
	 */
	public void cancelTest(Test test, String reason, boolean continueSerial) {
		if (reason == null)
			reason = "Unknown";
		log.warn("Going to cancel test[{}:::{}] for reason:{}", this.getTask().getId(), test.getId(), reason);
		try {
			TestListener tl = testSlots.get(test);
			if (tl != null && !(tl instanceof FakeTestListener)) {
				Message msg = hub.createMessage(Constants.BROKER_TASK);
				setProperty(msg, Constants.MSG_HEAD_TARGET, tl.getHostAgent());
				setProperty(msg, Constants.MSG_HEAD_TASKID, test.getTaskID());
				setProperty(msg, Constants.MSG_HEAD_TESTID, test.getId());
				setProperty(msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_CANCEL);
				setProperty(msg, Constants.MSG_HEAD_ERROR, reason);
				hub.getPipe(Constants.HUB_TASK_COMMUNICATION).send(msg);
				log.info("Sent message to cancel test[{}:::{}] msg:{}", test.getTaskID(), test.getId(), CommonUtils.toJson(msg));
				((MessageListenerAdapter) hub.getPipe(Constants.HUB_TEST_STATUS).getListener()).removeFilter(tl);
				LogUtils.dispose(tl.getLog());
			}
			testSlots.remove(test);
			test.setPhase(Phase.STOPPED);
		} catch (Exception ex) {
			log.error("Cancel test failed. test=" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId(), ex);
		}
		if(continueSerial) {
			if (!testSlots.isEmpty() && this.isSerialized()) {
				startNextSerializedTest();
			}
		}
	}

	/**
	 * Steps for task client.
	 * <ol>
	 * <li>Allocate test requirements.</li>
	 * <li>Publish Task.</li>
	 * <li>Select candidates.</li>
	 * <li>Send reservation.</li>
	 * <li>Connect to agent node.</li>
	 * <li>Send artifacts.</li>
	 * <li>waiting for task execution.</li>
	 * <li>Monitoring the execution notification.</li>
	 * <li>Receiving results.</li>
	 * <li>Done!</li>
	 * </ol>
	 */
	public void begin() {
		if(this.getTask().getTestsuite() == null || this.getTask().getTestsuite().getTests() == null || this.getTask().getTestsuite().getTests().isEmpty()) {
			log.warn("Give Task[{}] didn't contains validate tests, stop running.", this.getTask().getId());
		}
		log.info("Task Client start working for task[{}]", task.getId());
		long startpoint = System.currentTimeMillis();
		int retryTimes = 0;
		if (this.isSerialized() && this.getMaxConcurrentSize() >= this.getTask().getTestsuite().getTests().size()) {
			this.setSerialized(false);
		}
		// first, publish the task.
		while (!Task.Phase.PUBLISHED.equals(task.getPhase())) {
			try {
				lastPublishTime = System.currentTimeMillis();
				for (Test t : task.getTestsuite().getTests()) {
					t.addObserver(notifier);
					testSlots.put(t, new FakeTestListener(t, this.isSerialized()));
					failCounter.put(t.getId(), 0);
				}
				publishTask(task);
				break;
			} catch (MessageException e) {
				log.error("Met Exception while publishing task:{}, try to republish[" + retryTimes + "] it again.", e, task.getId());
				retryTimes++;
				if ((System.currentTimeMillis() - startpoint) >= publishTimeout || retryTimes >= maxRetryTimes) {
					log.error("Can't successfully pushlish the task from " + CommonUtils.parse(startpoint) + " to "
							+ CommonUtils.parse(System.currentTimeMillis()) + " during " + retryTimes + " times retry, Task[" + task.getId() + "]");
					this.cancelTask("Publish task reach maxium timeout[" + CommonUtils.convert(publishTimeout) + "]");
					break;
				}
			}
			try {
				TimeUnit.SECONDS.sleep(10 * (retryTimes + 1));
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}

		this.start();
	}

	/**
	 * Handle new incoming task acceptance. Event handler for candidateBus
	 * Event.
	 */
	public void handleNewAccpetance(String taskId, String testId) {
		switch (strategy) {
			case EXEC_ASAP:
				if (!isAllTestStart()) {
					Map<String, DeviceCapacity> candidates = this.agentCandidates.get(testId);
					Iterator<String> it = candidates.keySet().iterator();
					String from = null;
					while (it.hasNext()) {
						from = it.next();
						DeviceCapacity capacity = candidates.remove(from);
						log.info("Agent:[" + from + "] have " + capacity.getAvailable() + " devicesets which can execute the test[" + testId + "] now.");
						Test t = getNextWaitingTest(testId);
						if (t == null) {
							log.error("Can't find Test from waiting test list:" + taskId + Constants.TASK_TEST_SPLITER + testId);
						}
						// t.getRequirements().equals(capacity.getRequirement())
						boolean needListener = true;
						if (t != null && testId.equals(capacity.getTestId()) && capacity.getAvailable() > 0) {
							t.setPhase(Phase.PENDING);
							List<MessageFilter> filters = ((MessageListenerAdapter) hub.getPipe(Constants.HUB_TEST_STATUS).getListener()).getFilters();
							if (filters != null) {
								for (MessageFilter mf : filters) {
									if (mf != null) {
										if (mf instanceof TestListener) {
											Test td = ((TestListener) mf).getTest();
											if (td != null && td.getId().equals(testId)) {
												needListener = false;
												break;
											}
										}
									}
								}
							}
							if (needListener) {
								TestListener tl = new TestListener(t, from, tracker);
								((MessageListenerAdapter) hub.getPipe(Constants.HUB_TEST_STATUS).getListener()).addFilter(tl);
								testSlots.put(t, tl);
							}
							try {
								sendReservation(from, t);
							} catch (MessageException e) {
								log.error("Send Reservation failed. Will Try again.", e);
								try {
									TimeUnit.SECONDS.sleep(5);
									sendReservation(from, t);
								} catch (Exception ex) {
									cancelTask("Can't send reservation to Agents for reason: " + e.getMessage());
								}
							}
						} else {
							log.error("Can't find related capabal devices for running test:" + taskId + Constants.TASK_TEST_SPLITER + testId);
						}
						it.remove();
					}
				}
				break;
			case SPLIT_AMAP:
				break;
			case MODERATE:
				break;
		}
	}

	/**
	 * Get next test in waiting state.
	 * 
	 * @return
	 */
	public Test getNextWaitingTest(String testId) {
		for (Test t : this.testSlots.keySet()) {
			if ((testSlots.get(t) instanceof FakeTestListener || t.getPhase().equals(Phase.PENDING)) && t.getId().equals(testId))
				return t;
		}
		return null;
	}

	public synchronized Test getNextWaitingSerializedTest() {
		for (Test t : this.testSlots.keySet()) {
			if ((testSlots.get(t) instanceof FakeTestListener)) {
				FakeTestListener ftl = (FakeTestListener) testSlots.get(t);
				if (ftl.isSerializeWaiting()) {
					ftl.setSerializeWaiting(false);
					return t;
				}
			}
		}
		return null;
	}

	public Test getFirstNotInWaitingStateSerializedTest() {
		if (this.isSerialized()) {
			for (Test t : this.testSlots.keySet()) {
				if ((testSlots.get(t) instanceof FakeTestListener)) {
					FakeTestListener ftl = (FakeTestListener) testSlots.get(t);
					if (!ftl.isSerializeWaiting())
						return t;
				}
			}
		}
		return null;
	}

	/**
	 * Get next test in waiting state.
	 * 
	 * @return
	 */
	public int getWaitingTestsNumber() {
		int cnt = 0;
		for (Test t : this.testSlots.keySet()) {
			if (testSlots.get(t) instanceof FakeTestListener)
				cnt++;
			else if (t.getPhase().equals(Phase.PENDING))
				cnt++;
		}
		return cnt;
	}

	/**
	 * Get next Serialized test in waiting state.
	 * 
	 * @return
	 */
	public int getWaitingSerializedTestsNumber() {
		int cnt = 0;
		for (Test t : this.testSlots.keySet()) {
			if (testSlots.get(t) instanceof FakeTestListener) {
				if (((FakeTestListener) testSlots.get(t)).isSerializeWaiting())
					cnt++;
			}
		}
		return cnt;
	}

	/**
	 * Check if all the tests are under execution.
	 * 
	 * @return
	 */
	public boolean isAllTestStart() {
		boolean ret = true;
		for (Test t : this.testSlots.keySet()) {
			if (testSlots.get(t) instanceof FakeTestListener) {
				// if ( t.getPhase() != Phase.UNKNOWN && t.getPhase() !=
				// Phase.PENDING ) {
				ret = false;
				break;
			} else if (t.getPhase().equals(Phase.PENDING)) {
				ret = false;
				break;
			} else {
				continue;
			}
		}
		return ret;
	}

	/*
	 * private void setSerializeState(Test test, boolean state) { if(test !=
	 * null && testSlots.containsKey(test)) { Object obj = testSlots.get(test);
	 * if(obj != null && obj instanceof FakeTestListener) {
	 * ((FakeTestListener)obj).setSerializeWaiting(state); } } }
	 */

	/**
	 * Client publish task to agent nodes.
	 * 
	 * @param task
	 * @throws JMSException
	 */
	public void publishTask(Task task) throws MessageException {
		int testCount = task.getTestsuite().getTests().size();
		if (testCount > 0) {
			if (this.isSerialized()) {
				for (int i = 0; i < this.maxConcurrentSize; i++) {
					Test test = task.getTestsuite().getTests().get(i);
					FakeTestListener fl = (FakeTestListener) testSlots.get(test);
					fl.setSerializeWaiting(false);
					publishTests(test);
					if (testCount == (i + 1))
						break;
				}
			} else {
				publishTests(task.getTestsuite().getTests());
			}
		} else {
			log.error("No Test leaving in testsuite. quit.");
			this.taskFinished();
		}

		task.setPhase(Task.Phase.PUBLISHED);
	}
	
	/**
	 * publish test with certain delay.
	 * @param test
	 * @param delay
	 */
	public void publishTestWithDelay(final Test test, final long delay) {
		long curr = System.currentTimeMillis();
		long planTime = curr + delay;
		Long preTime = publishQueue.putIfAbsent(test.getId(), planTime);
		if(preTime == null) {
			delayPublishPool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					try {
						publishTests(test);
					} catch (MessageException e) {
						log.error("publish test["+test.getId()+"] failed.", e);
					}
				}});
		}else {
			if((planTime - preTime) < (Constants.ONE_SECOND * 30)) {
				log.info("test:[{}:::{}] Have one publish in less than 30 secs.ignore", test.getTaskID(), test.getId());
			}
		}
	}
	
	public void publishTestToAgent(Test test, String target) throws MessageException {
		if(test != null) {
			try {
				Pipe pipe = hub.getPipe(Constants.HUB_TASK_COMMUNICATION);
				Message message = hub.createMessage(Constants.BROKER_TASK);
				setProperty(message, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_SUBSCRIBE);
				setProperty(message, Constants.MSG_HEAD_TEST, test.toString());
				setProperty(message, Constants.MSG_HEAD_TARGET, target);
				pipe.getProducer().send(message);
				log.info("Test[{}:::{}] have been published to Agent:{}! Device requirement is:{}"
						, test.getTaskID(), test.getId(), target, test.getRequirements());
			}catch (JMSException e) {
				log.error("Publish test met exception.target:"+target+",tests:" + test, e);
				throw new MessageException(e);
			}
		}
	}
	
	public void publishTests(Test... tests) throws MessageException {
		if (tests != null && tests.length > 0) {
			try {
				Pipe pipe = hub.getPipe(Constants.HUB_TASK_PUBLISH);
				Message message = hub.createMessage(Constants.BROKER_TASK);
				setProperty(message, Constants.MSG_HEAD_FROM, Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName());
				long current = System.currentTimeMillis();
				for (Test test : tests) {
					if (Test.Phase.UNKNOWN.equals(test.getPhase()) || Test.Phase.PENDING.equals(test.getPhase())) {
						setProperty(message, Constants.MSG_HEAD_TEST, test.toString());
						pipe.getProducer().send(message);
						log.info("Test[{}:::{}] have been RePublished! Device requirement is:{}"
								, test.getTaskID(), test.getId(), test.getRequirements());
						publishQueue.put(test.getId(), current);
						File resultParent = new File(getResultFolder(), test.getId());
						if (!resultParent.exists())
							resultParent.mkdirs();
						File timelineFile = new File(resultParent, "timeline.txt");
						if (!timelineFile.exists()) {
							try {
								timelineFile.createNewFile();
								String content = Constants.TIMELINE_PUBLISH_TEST + "=" + System.currentTimeMillis() + "\n";
								CommonUtils.appendContent2File(timelineFile.getAbsolutePath(), content, "UTF-8");
							} catch (Exception e) {
								this.log.error("Create Timeline file for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + " failed.", e);
							}
						}
					}else {
						log.info("Test:[{}:::{}] is in phase:{}, no need to publish.", test.getTaskID(), test.getId(), test.getPhase());
					}
				}
			} catch (JMSException e) {
				log.error("Publish test met exception. tests:" + CommonUtils.toJson(tests), e);
				throw new MessageException(e);
			}
		}
	}

	public void publishTests(List<Test> tests) throws MessageException {
		if (tests == null || tests.size() == 0)
			return;
		publishTests(tests.toArray(new Test[0]));

	}

	/**
	 * Client send reservation to assigned agent node, ask agent to reserve the
	 * specific devices for task.
	 * 
	 * @param target
	 * @param test
	 * @throws JMSException
	 */
	public void sendReservation(String target, Test test) throws MessageException {
		Pipe taskPipe = hub.getPipe(Constants.HUB_TASK_COMMUNICATION);
		Message msg;
		try {
			msg = hub.createMessage(Constants.BROKER_TASK);
			// setProperty(msg, Constants.MSG_HEAD_TASK, task.toString());
			setProperty(msg, Constants.MSG_HEAD_TASKID, task.getId());
			setProperty(msg, Constants.MSG_HEAD_TEST, test.toString());
			setProperty(msg, Constants.MSG_HEAD_FROM, Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName());
			setProperty(msg, Constants.MSG_HEAD_TARGET, target);
			setProperty(msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_RESERVE);
			taskPipe.getProducer().send(msg);
		} catch (JMSException e) {
			throw new MessageException(e);
		}
		log.info("Sent reservation message to " + target + " for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId());
	}

	/**
	 * pick up test object from test slots by testId.
	 * 
	 * @param testId
	 * @return
	 */
	public Test getTestById(String testId) {
		for (Test t : this.testSlots.keySet()) {
			if (t.getId().equals(testId))
				return t;
		}
		return null;
	}

	/**
	 * Clean up all the resources of current Task. Should be called in final
	 * stage.
	 */
	public void cleanUp() {
		try {
			this.checker.stop();
		} catch (Exception ex) {
			log.error("Stop Test execution Checker met exception.", ex);
		}
		fts.dispose();
		hub.dispose();
		pool.shutdown();
		agentCandidates.clear();
		// this.getNotifier().dispose();
		log.info("Task Client resource clean up finished.");
		log.getTags().clear();
	}

	/**
	 * Called when some tests failed during execution.
	 * 
	 * @param testId
	 * @param reason
	 */
	public void testFail(String testId, String reason) {
		log.error("Test[{}] Failed because: {}", testId, reason);
		Test test = this.getTestById(testId);
		if (test != null) {
			int count = failCounter.get(testId);
			if (count >= this.maxRetryTimes) {
				failCounter.remove(testId);
				test.setPhase(Phase.FAILED);
				cancelTest(test, "Failed over max retry times[" + maxRetryTimes + "].", true);
				TestListener tl = testSlots.remove(test);
				if (tl != null) {
					LogUtils.dispose(tl.getLog());
					((MessageListenerAdapter) hub.getPipe(Constants.HUB_TEST_STATUS).getListener()).removeFilter(tl);
				} else {
					log.warn("Test[" + test.getTaskID() + Constants.TASK_TEST_SPLITER + testId + "] can't find relative TestListener in current test slots in final retry round.");
				}
			} else {
				TestListener tl = testSlots.remove(test);
				if (tl != null) {
					LogUtils.dispose(tl.getLog());
					((MessageListenerAdapter) hub.getPipe(Constants.HUB_TEST_STATUS).getListener()).removeFilter(tl);
					test.setPhase(Phase.PENDING);
					testSlots.put(test, new FakeTestListener(test, this.isSerialized()));
				} else {
					log.warn("Test[" + test.getTaskID() + Constants.TASK_TEST_SPLITER + testId + "] can't find relative TestListener in current test slots.");
				}
				failCounter.put(testId, (count + 1));
			}

			if (!testSlots.isEmpty() && this.isSerialized()) {
				startNextSerializedTest();
			}
		}

	}

	/**
	 * Called when some of tests successfully finished.
	 * 
	 * @param testId
	 */
	public void testFinished(Test test) {
		TestListener tl = this.testSlots.remove(test);
		test.setPhase(Phase.FINISHED);
		((MessageListenerAdapter) hub.getPipe(Constants.HUB_TEST_STATUS).getListener()).removeFilter(tl);
		tl.dispose();
		if (testSlots.isEmpty()) {
			log.warn("All tests have been finished.");
			taskFinished();
		}
		log.info("Test[" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + "] execution finished.");
		File resultParent = new File(getResultFolder(), test.getId());
		if (!resultParent.exists())
			resultParent.mkdirs();
		File timelineFile = new File(resultParent, "timeline.txt");
		if (!timelineFile.exists()) {
			try {
				timelineFile.createNewFile();
			} catch (Exception e) {
				this.log.error("Create Timeline file for test:" + test.getTaskID() + Constants.TASK_TEST_SPLITER + test.getId() + " failed when finish test.", e);
			}
		}
		String content = Constants.TIMELINE_FINISH_TEST + "=" + System.currentTimeMillis();
		CommonUtils.appendContent2File(timelineFile.getAbsolutePath(), content, "UTF-8");
		if (!testSlots.isEmpty() && this.isSerialized()) {
			startNextSerializedTest();
		}
	}

	public void triggerWaitingTest(DeviceRequirement requirement, String target) {
		for (Test test : this.testSlots.keySet()) {
			if ((test.getPhase().equals(Phase.UNKNOWN) || test.getPhase().equals(Phase.PENDING))
					&& testSlots.get(test) instanceof FakeTestListener) {
				if(test.getRequirements().equals(requirement)) {
					try {
						publishTestToAgent(test, target);
					} catch (MessageException e) {
						log.error("Trigger waiting test failed. target:" + target +",requirement:"+requirement, e);
					}
				}
			}
		}
		
	}
	
	private void startNextSerializedTest() {
		log.info("Trying to start next Serialized test..");
		if (this.isSerialized()) {
			Test nextTest = getNextWaitingSerializedTest();
			if (nextTest != null) {
				log.info("The serialzied test tobe started is {}", nextTest.getId());
				try {
					serialPartStart = System.currentTimeMillis();
					this.publishTests(nextTest);
				} catch (MessageException e) {
					log.error("Publish test failed.test:" + nextTest, e);
				}
			}
		}
	}

	private synchronized void taskFinished() {
		running = false;
		this.getTask().setPhase(Task.Phase.FINISHED);
		client.setStatus(Client.Status.IDLE);
		log.info("Current Task finally over. Good luck!");
		this.end = System.currentTimeMillis();
		this.cleanUp();
	}

	/**
	 * Provided for outside checker.
	 * 
	 * @return if task have been finished.
	 */
	public boolean isFinished() {
		return !running;
	}

	/**
	 * Cancel whole Task, including all the belonging tests.
	 */
	public void cancelTask(String reason) {
		log.warn("Task["+this.getTask().getId()+"] is going to be cancelled. Reason:" + reason);
		for (Test test : testSlots.keySet()) {
			cancelTest(test, reason, false);
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException e) {
				log.error("Interrupted when wait for cancel message been sent.");
			}
		}
		taskFinished();
	}

	public void addExecuteDevices(String testId, String... deviceIds) {
		if (testId != null && deviceIds != null) {
			for (String id : deviceIds) {
				executeDevices.put(testId, id);
			}
		}
	}

	public Map<String, String> getExecuteDevices() {
		return executeDevices;
	}

	public String getExecuteDeviceByTestId(String testId) {
		return executeDevices.get(testId);
	}

}
