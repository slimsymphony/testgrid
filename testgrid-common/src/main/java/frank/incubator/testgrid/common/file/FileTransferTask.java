package frank.incubator.testgrid.common.file;

import java.io.File;
import java.util.Collection;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.BaseObject;

/**
 * Define the File Transfer Task.
 * 
 * @author Wang Frank
 * 
 */
public class FileTransferTask extends BaseObject implements Comparable<FileTransferTask> {
	private String from;
	private String targetUri;
	private String taskId;
	private String testId;
	private Collection<File> files;
	private long start;
	private int retry;
	private FileTransferDescriptor descriptor;
	private FileTransferChannel currentChannel;

	public FileTransferTask(String from, String targetUri, String taskId, String testId, Collection<File> files) {
		this(null, from, targetUri, taskId, testId, files);
	}

	public FileTransferTask(String id, String from, String targetUri, String taskId, String testId, Collection<File> files) {
		if (id == null || id.trim().isEmpty())
			this.id = "FT_" + taskId + "_" + testId + "_" + CommonUtils.generateToken(5);
		else
			this.id = id;
		this.from = from;
		this.targetUri = targetUri;
		this.taskId = taskId;
		this.testId = testId;
		this.files = files;
		this.start = System.currentTimeMillis();
		this.retry = 0;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getTargetUri() {
		return targetUri;
	}

	public void setTargetUri(String targetUri) {
		this.targetUri = targetUri;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getTestId() {
		return testId;
	}

	public void setTestId(String testId) {
		this.testId = testId;
	}

	public Collection<File> getFiles() {
		return files;
	}

	public void setFiles(Collection<File> files) {
		this.files = files;
	}

	public long getStart() {
		return start;
	}

	public void setStart(long start) {
		this.start = start;
	}

	public int getRetry() {
		return retry;
	}

	public void setRetry(int retry) {
		this.retry = retry;
	}

	public FileTransferDescriptor getDescriptor() {
		return descriptor;
	}

	public void setDescriptor(FileTransferDescriptor descriptor) {
		this.descriptor = descriptor;
	}

	public FileTransferChannel getCurrentChannel() {
		return currentChannel;
	}

	public void setCurrentChannel(FileTransferChannel currentChannel) {
		this.currentChannel = currentChannel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(FileTransferTask o) {
		if (o == null)
			throw new NullPointerException("Compare Object is Null");
		return (this.start < o.getStart()) ? -1 : ((this.start == o.getStart()) ? 0 : -1);
	}
}
