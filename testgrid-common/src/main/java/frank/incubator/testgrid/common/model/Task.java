package frank.incubator.testgrid.common.model;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;

public class Task extends BaseObject {

	private String taskOwner;
	private long created;
	private TestSuite testsuite;
	private Phase phase;
	private long startTime = 0L;
	private long endTime = 0L;
	private boolean sendOutput = false;
	private long timeout = Constants.ONE_MINUTE * 10;

	public Task() {
		created = System.currentTimeMillis();
		this.id = "Task_" + created + "_" + CommonUtils.generateToken(5);
	}

	public Task(String id) {
		this.id = id;
		created = System.currentTimeMillis();
	}

	@Override
	public void setId(String id) {
		if (testsuite != null)
			for (Test t : testsuite.getTests()) {
				t.setTaskID(this.id);
			}
		this.id = id;
		if (this.testsuite != null) {
			if (testsuite.getTests() != null) {
				for (Test t : testsuite.getTests()) {
					t.setTaskID(id);
				}
			}
		}
	}

	public String getTaskOwner() {
		return taskOwner;
	}

	public void setTaskOwner(String taskOwner) {
		this.taskOwner = taskOwner;
	}

	public long getCreated() {
		return created;
	}

	public boolean isSendOutput() {
		return sendOutput;
	}

	public void setSendOutput(boolean sendOutput) {
		this.sendOutput = sendOutput;
		if (this.getTestsuite() != null && this.getTestsuite().getTests() != null) {
			for (Test t : this.getTestsuite().getTests()) {
				t.setSendOutput(sendOutput);
			}
		}
	}

	public TestSuite getTestsuite() {
		return testsuite;
	}

	public void setTestsuite(TestSuite testsuite) {
		this.testsuite = testsuite;
		for (Test t : testsuite.getTests()) {
			t.setTaskID(this.id);
		}
	}

	public Phase getPhase() {
		return phase;
	}

	public void setPhase(Phase phase) {
		if (phase != this.phase)
			this.setChanged();
		this.phase = phase;
		if (phase == Phase.FINISHED) {
			setEndTime(System.currentTimeMillis());
		}
		this.notifyObservers();
	}

	public enum Phase {
		PUBLISHED, STARTED, FINISHED // ,CANCELLED, FAILED
	}

	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public void setCreated(long created) {
		this.created = created;
	}

}
