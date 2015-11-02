package frank.incubator.testgrid.common.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;

/**
 * Basic Unit contains all elements of running a *REAL* test. Including required
 * file list, execution script, result package.
 * 
 * @author Wang Frank
 * 
 */
public class Test extends BaseObject {
	private transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	public Test(String id) {
		this.id = id;
		if(lock == null)
			lock = new ReentrantReadWriteLock();
	}

	public Test() {
		// Please DO NOT change the ID prefix "Test_", since it is a convention
		// used by other part of the system.
		this.id = "Test_" + System.currentTimeMillis() + "_" + CommonUtils.generateToken(5);
		if(lock == null)
			lock = new ReentrantReadWriteLock();
	}

	/**
	 * Supported test targets. FLASH target is used by tests which are actually
	 * flashing products. SIMULATOR target is used by test which are not using
	 * any physical products.
	 */
	public enum Target {
		DEVICE, SIMULATOR
	};

	/**
	 * Supported test states:
	 * 
	 * UNKNOWN - Meaning that test was just created or not yet initialized.
	 * PENDING - Meaning that test was accepted by the Test agent, but not yet
	 * started on any of the test nodes. STARTED - Meaning that test was
	 * successfully started on the assigned test node. STOPPED - Meaning that
	 * test was stopped by some administration commands. FINISHD - Meaning that
	 * test has ended its execution on a test node and has returned file with
	 * test result back to the test issuer. FAILED - Meaning that test was
	 * considered as failed for some reason.
	 */
	public enum Phase {
		UNKNOWN, PENDING, PREPARING, STARTED, STOPPED, FINISHED, FAILED
	};

	/**
	 * Parent Task ID. Storing for message identification.
	 */
	private String taskID;
	/**
	 * Filepath to the directory where test's artifacts are located.
	 */
	private String workspacePath = "";

	/**
	 * URL related to the test.
	 */
	private String url = "";

	/**
	 * Name of the application which is installed to the testing node for
	 * execution of delivered tests. Right now it is Iron Python by default, but
	 * might be something else in the future.
	 */
	private String executorApplication = "";

	/**
	 * Name of the script which actually executes test on a testing node. If
	 * executor script will be specified, it will be automatically added to the
	 * list of test's artifacts.
	 */
	private String executorScript = "";

	private String preScript = "";

	private String postScript = "";

	private boolean sendOutput = false;

	/**
	 * Environment parameters for executor. Define env param here and executor
	 * will set those env paramters before execution.
	 */
	private Map<String, String> executorEnvparams = new HashMap<String, String>();

	/**
	 * Parameters for execute the main scripts. All the parameters should be
	 * stored in String form.
	 */
	private List<String> executorParameters = new ArrayList<String>();

	/**
	 * Names of the test results archive that should be generated by executor
	 * script and then copied from testing node to Test Automation Service.
	 */
	// private String resultsFilename = "";
	@SuppressWarnings("serial")
	private Map<String, Boolean> resultFiles = new HashMap<String, Boolean>() {
		{
			put(Constants.DEVICE_CONFIG_FILE, true);
			put(Constants.EXEC_CONSOLE_OUTPUT, false);
			put(Constants.EXEC_TIMELINE_OUTPUT, false);
		}
	};

	/**
	 * Current status of the test.
	 */
	private Phase phase = Phase.UNKNOWN;

	/**
	 * Some details related to the current status.
	 */
	private String phaseDetails = "";

	/**
	 * List of filenames representing all the artifacts required to run the test
	 * on real phones.
	 */
	private Map<String, Long> artifacts = new HashMap<String, Long>();

	/**
	 * Device disconnection timeout used in test.
	 */
	private long disconnectionTimeout = Constants.ONE_MINUTE * 5;

	/**
	 * Target of the test.
	 */
	private Target target = Target.DEVICE;

	/**
	 * An dictionary describing the environment required to execute this test.
	 */
	private Map<String, Object> testConditions;

	/**
	 * Timestamp of the moment when this test was actually started.
	 */
	private long startTime = 0L;
	/**
	 * Timestamp of the moment when this test was actually ended.
	 */
	private long endTime = 0L;

	private DeviceRequirement requirements;

	/**
	 * Timeout setting for the test in milliseconds. Any negative or zero value
	 * in timeout will set timeout to default value of 6 hours.
	 */
	private long timeout = Constants.DEFAULT_TEST_TIMEOUT;

	public String getTaskID() {
		return taskID;
	}

	public void setTaskID(String taskID) {
		this.taskID = taskID;
	}

	public String getWorkspacePath() {
		return workspacePath;
	}

	public void setWorkspacePath(String workspacePath) {
		this.workspacePath = workspacePath;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getExecutorApplication() {
		return executorApplication;
	}

	public void setExecutorApplication(String executorApplication) {
		this.executorApplication = executorApplication;
	}

	public String getExecutorScript() {
		return executorScript;
	}

	public void setExecutorScript(String executorScript) {
		this.executorScript = executorScript;
	}

	public Map<String, String> getExecutorEnvparams() {
		return executorEnvparams;
	}

	public Test addExecutorEnvParam(String key, String value) {
		if (key != null && value != null)
			this.executorEnvparams.put(key.trim(), value.trim());
		return this;
	}

	public void setExecutorEnvparams(Map<String, String> executorEnvparams) {
		this.executorEnvparams = executorEnvparams;
	}

	public List<String> getExecutorParameters() {
		return executorParameters;
	}

	public void setExecutorParameters(String... executorParameters) {
		this.executorParameters.clear();
		for (String ep : executorParameters)
			this.executorParameters.add(ep);
	}

	/*
	 * public String getResultsFilename() { return resultsFilename; }
	 * 
	 * public void setResultsFilename(String resultsFilename) {
	 * this.resultsFilename = resultsFilename; }
	 */

	public boolean isSendOutput() {
		return sendOutput;
	}

	public void setSendOutput(boolean sendOutput) {
		this.sendOutput = sendOutput;
	}

	public Phase getPhase() {
		lock.readLock().lock();
		try {
			return phase;
		}finally {
			lock.readLock().unlock();
		}
	}

	public Map<String, Boolean> getResultFiles() {
		return resultFiles;
	}

	public void setResultFiles(Map<String, Boolean> resultFiles) {
		this.resultFiles = resultFiles;
	}

	public void addResultFiles(Map<String, Boolean> resultFiles) {
		this.resultFiles.putAll(resultFiles);
		;
	}

	public Test addResultFile(String resultFileName, Boolean isMandatory) {
		if (resultFileName != null) {
			resultFiles.put(resultFileName, isMandatory);
		}
		return this;
	}

	public void setPhase(Phase phase) {
		lock.writeLock().lock();
		try {
			if (this.phase != null)
				setChanged();
			this.phase = phase;
		}finally {
			lock.writeLock().unlock();
		}
		
		if (phase == Phase.STARTED) {
			setStartTime(System.currentTimeMillis());
		} else if (phase == Phase.STOPPED || phase == Phase.FAILED || phase == Phase.FINISHED) {
			setEndTime(System.currentTimeMillis());
		}
		this.notifyObservers(startTime);
	}

	public String getPhaseDetails() {
		return phaseDetails;
	}

	public void setPhaseDetails(String phaseDetails) {
		this.phaseDetails = phaseDetails;
	}

	public Map<String, Long> getArtifacts() {
		return artifacts;
	}

	public void setArtifacts(Map<String, Long> artifacts) {
		this.artifacts = artifacts;
	}

	public Test addArtifact(String name, long size) {
		this.artifacts.put(name, size);
		return this;
	}

	public long getDisconnectionTimeout() {
		return disconnectionTimeout;
	}

	public void setDisconnectionTimeout(long disconnectionTimeout) {
		this.disconnectionTimeout = disconnectionTimeout;
	}

	public Target getTarget() {
		return target;
	}

	public void setTarget(Target target) {
		this.target = target;
	}

	public Map<String, ? extends Object> getTestConditions() {
		return testConditions;
	}

	public void setTestConditions(Map<String, Object> testConditions) {
		this.testConditions = testConditions;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public String getPreScript() {
		return preScript;
	}

	public void setPreScript(String preScript) {
		this.preScript = preScript;
	}

	public String getPostScript() {
		return postScript;
	}

	public void setPostScript(String postScript) {
		this.postScript = postScript;
	}

	public void setExecutorParameters(List<String> executorParameters) {
		this.executorParameters = executorParameters;
	}

	public DeviceRequirement getRequirements() {
		return requirements;
	}

	public void setRequirements(DeviceRequirement requirements) {
		this.requirements = requirements;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Test))
			return false;
		Test other = (Test) obj;
		return this.id.equals(other.getId());
	}

	@Override
	public int hashCode() {
		return this.id.hashCode();
	}
}
