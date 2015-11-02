package frank.incubator.testgrid.client.message;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.File;
import java.io.FileFilter;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import javax.jms.Message;

import org.apache.commons.io.filefilter.WildcardFileFilter;

import com.google.gson.reflect.TypeToken;

import frank.incubator.testgrid.client.TaskClient;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.file.FileTransferTask;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.model.DeviceCapacity;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.Test.Phase;

/**
 * Handler of "HUB_TASK_COMMUNICATION" Queue. Which including Task confirmation.
 * 
 * @author Wang Frank
 * 
 */
public class TaskClientCommunicator extends MessageListenerAdapter {

	public TaskClientCommunicator(TaskClient client, Semaphore lock, OutputStream tracker) {
		super("TaskClientCommunicator", tracker);
		this.client = client;
		this.lock = lock;
	}

	private TaskClient client;

	private Semaphore lock;

	public TaskClient getClient() {
		return client;
	}

	public void setClient(TaskClient client) {
		this.client = client;
	}

	private String convertState(int state) {
		switch (state) {
			case Constants.MSG_TASK_ACCEPT:
				return "MSG_TASK_ACCEPT";
			case Constants.MSG_TASK_CONFIRM:
				return "MSG_TASK_CONFIRM";
			case Constants.MSG_TEST_READY:
				return "MSG_TEST_READY";
			case Constants.MSG_TEST_FAIL:
				return "MSG_TEST_FAIL";
			case Constants.MSG_TEST_FINISHED:
				return "MSG_TEST_FINISHED";
			case Constants.MSG_TASK_RESERVE:
				return "MSG_TASK_RESERVE";
			case Constants.MSG_TASK_START:
				return "MSG_TASK_START";
			case Constants.MSG_START_FT_FETCH:
				return "MSG_START_FT_FETCH";
			case Constants.MSG_TEST_FINISH_CONFIRM:
				return "MSG_TEST_FINISH_CONFIRM";
			case Constants.MSG_TASK_CANCEL:
				return "MSG_TASK_CANCEL";
			default:
				return "UNKNOWN";
		}
	}
	
	@Override
	public void onMessage(Message message) {
		try {
			lock.acquire();
			String from = getProperty(message, Constants.MSG_HEAD_FROM, "Unknown");
			String taskID = getProperty(message, Constants.MSG_HEAD_TASKID, "");
			String testId = getProperty(message, Constants.MSG_HEAD_TESTID, "");
			Test test = this.client.getTestById(testId);
			File timelineFile = null;
			File resultParent = null;
			String content = null;
			if (client.getTask().getId().equals(taskID)) {
				int state = getProperty(message, Constants.MSG_HEAD_TRANSACTION, 0);
				log.info("Received task message: " + state + "-" + convertState(state) + " from " + from);
				switch (state) {
					case Constants.MSG_TASK_ACCEPT:
						if (!client.isAllTestStart()) {
							TestListener tl = this.client.getTestSlots().get(test);
							if(test != null && (test.getPhase().equals(Phase.UNKNOWN) || test.getPhase().equals(Phase.PENDING))
									&& tl instanceof FakeTestListener) {
								DeviceCapacity capacity = CommonUtils.fromJson(getProperty(message, Constants.MSG_HEAD_RESPONSE, ""), DeviceCapacity.class);
								log.info("[" + from + "] give response, capacity:" + capacity);
								// check capacity and compare with test size.
								if (capacity.getAvailable() > 0 || capacity.getNeedWait() > 0) {
									log.info("New candidate coming up from " + from + " for test:" + taskID + Constants.TASK_TEST_SPLITER + testId + ", available:"+capacity.getAvailable() +",needWait:" + capacity.getNeedWait());
									if (!client.getAgentCandidates().containsKey(testId)) {
										client.getAgentCandidates().put(testId, new ConcurrentHashMap<String, DeviceCapacity>());
									}
									client.getAgentCandidates().get(testId).put(from, capacity);
									if(capacity.getAvailable() > 0) {
										log.info("There are available devices for test:" + taskID + Constants.TASK_TEST_SPLITER + testId+" , request to reserve it!");
										client.handleNewAccpetance(taskID, testId);
									}else {
										log.info("No current available devices for test:" + taskID + Constants.TASK_TEST_SPLITER + testId+" , delay 10 sec and try it again!");
										client.publishTestWithDelay(client.getTestById(testId), Constants.ONE_SECOND * 30);
									}
								} else {
									if (client.getAgentCandidates().containsKey(testId)) {
										client.getAgentCandidates().get(testId).remove(from);
									}
									log.info("[" + from + "] didn't have device resources needed for current test.");
									client.publishTestWithDelay(client.getTestById(testId), Constants.ONE_SECOND * 40);
								}
							}else {
								log.info("test:{} is in wrong state or null or already started", test);
							}
						}
						break;
					case Constants.MSG_TASK_CONFIRM:
						int reply = getProperty(message, Constants.MSG_HEAD_RESPONSE, 0);
						if (reply == Constants.RESERVE_SUCC) {
							String dvs = getProperty(message, Constants.MSG_HEAD_RESERVED_DEVICES, "");
							Map<String, Integer> devices = CommonUtils.fromJson(dvs, new TypeToken<Map<String, Integer>>() {
							}.getType());
							log.info("Agent [" + from + "] have already reserved the device for this test["+ taskID + Constants.TASK_TEST_SPLITER + testId + "].");
							for (String id : devices.keySet()) {
								log.info(id + " have been reserved as Role[" + devices.get(id) + "]");
								client.addExecuteDevices(testId, id);
							}
							test = client.getTestById(testId);
							if (test.getPhase().equals(Phase.PENDING)) {
								test.setPhase(Phase.PREPARING);
								log.info("set test[{}:::{}] phase to PREPARING", test.getTaskID(), testId);
								Collection<File> files = new ArrayList<File>();
								File af = null;
								for (String fn : test.getArtifacts().keySet()) {
									af = new File(client.getWorkspace(), fn);
									if (af.exists() && af.isFile() && af.length() == test.getArtifacts().get(fn)) {
										files.add(af);
									} else {
										String reason = "File[" + af.getAbsolutePath() + "] invalid, maybe not exists or not a file, or length[" + af.length()
												+ "] not equals to planned transfer size:" + test.getArtifacts().get(fn);
										log.warn(reason);
										Message msg = client.getHub().createMessage(Constants.BROKER_TASK);
										setProperty(msg, Constants.MSG_HEAD_TARGET, from);
										setProperty(msg, Constants.MSG_HEAD_TESTID, testId);
										setProperty(msg, Constants.MSG_HEAD_TASKID, test.getTaskID());
										setProperty(msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_CANCEL);
										setProperty(msg, Constants.MSG_HEAD_ERROR, reason);
										client.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).send(msg);
										return;
									}
								}

								FileTransferTask ftTask = new FileTransferTask(Constants.MSG_TARGET_CLIENT + CommonUtils.getHostName(), from, taskID, testId,
										files);
								client.getFts().sendTo(ftTask);
								this.client.getTask().setPhase(Task.Phase.STARTED);
								resultParent = new File(client.getResultFolder(), testId);
								if (!resultParent.exists())
									resultParent.mkdirs();
								timelineFile = new File(resultParent, "timeline.txt");
								if (!timelineFile.exists()) {
									try {
										timelineFile.createNewFile();
									} catch (Exception e) {
										this.log.error("Create Timeline file for test:" + taskID + Constants.TASK_TEST_SPLITER + testId + " failed when ready to send artifacts.", e);
									}
								}
								content = Constants.TIMELINE_DEVICE_RESERVE + "=" + System.currentTimeMillis() + "\n";
								CommonUtils.appendContent2File(timelineFile.getAbsolutePath(), content, "UTF-8");
							} else {
								log.warn("Test[" + testId + "] have been in phase:" + test.getPhase() + ". should cancel reservation.");
								Message msg = client.getHub().createMessage(Constants.BROKER_TASK);
								setProperty(msg, Constants.MSG_HEAD_TARGET, from);
								setProperty(msg, Constants.MSG_HEAD_TESTID, testId);
								setProperty(msg, Constants.MSG_HEAD_TASKID, test.getTaskID());
								setProperty(msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_CANCEL);
								setProperty(msg, Constants.MSG_HEAD_ERROR, "Cancel duplicated reserved device for test:"+ testId);
								client.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).send(msg);
							}

						} else if (reply == Constants.RESERVE_FAILED) {
							// TODO: reserve failed. just forget it, remove the
							// listener from testslots.
							log.error("Reserved device failed for Test[" + taskID + Constants.TASK_TEST_SPLITER + testId + "].");
							test = client.getTestById(testId);
							test.setPhase(Phase.PENDING);
							client.getTestSlots().put(client.getTestById(testId), new FakeTestListener(client.getTestById(testId), client.isSerialized()));
							client.publishTestWithDelay(test, Constants.ONE_SECOND * 10);
						} else {
							log.warn("Illegal Message:" + CommonUtils.toJson(message));
						}
						break;
					case Constants.MSG_TEST_READY:
						test = client.getTestById(testId);
						log.info("set test[{}:::{}] phase to STARTED", test.getTaskID(), testId);
						test.setPhase(Phase.STARTED);
						Message msg = client.getHub().createMessage(Constants.BROKER_TASK);
						setProperty(msg, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TASK_START);
						setProperty(msg, Constants.MSG_HEAD_TARGET, from);
						setProperty(msg, Constants.MSG_HEAD_TASKID, test.getTaskID());
						setProperty(msg, Constants.MSG_HEAD_TESTID, testId);
						client.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).getProducer().send(msg);
						client.getTestSlots().get(test).setStart(System.currentTimeMillis());
						resultParent = new File(client.getResultFolder(), testId);
						if (!resultParent.exists())
							resultParent.mkdirs();
						timelineFile = new File(resultParent, "timeline.txt");
						if (!timelineFile.exists()) {
							try {
								timelineFile.createNewFile();
							} catch (Exception e) {
								this.log.error("Create Timeline file for test:" + taskID + Constants.TASK_TEST_SPLITER + testId + " failed when ready to start test.", e);
							}
						}
						content = Constants.TIMELINE_SEND_ARTIFACTS + "=" + System.currentTimeMillis() + "\n";
						CommonUtils.appendContent2File(timelineFile.getAbsolutePath(), content, "UTF-8");
						break;
					case Constants.MSG_TEST_FAIL:
						String reason = getProperty(message, Constants.MSG_HEAD_ERROR, "");
						log.error("Test[" + taskID + Constants.TASK_TEST_SPLITER + testId + "] failed in agent side for:" + reason);
						client.testFail(testId, reason);
						break;
					case Constants.MSG_TEST_FINISHED:
						final int result = getProperty(message, Constants.MSG_HEAD_TEST_RESULT, Constants.MSG_TEST_SUCC);
						Map<String, Boolean> missingFiles = new HashMap<String, Boolean>();
						final Test itest = client.getTestById(testId);
						boolean isSucc = false;
						if (itest != null) {
							isSucc = checkResultFilesValidation(itest, missingFiles);

							if (!missingFiles.isEmpty()) {
								log.warn("Test[" + taskID + Constants.TASK_TEST_SPLITER + testId + "] missing some result files. missing files:" + CommonUtils.toJson(missingFiles));
							}
							String errorMsg = getProperty(message, Constants.MSG_HEAD_ERROR, "Unknown");
							if (result == Constants.MSG_TEST_SUCC && isSucc) {
								Message msgi = client.getHub().createMessage(Constants.BROKER_TASK);
								setProperty(msgi, Constants.MSG_HEAD_TESTID, testId);
								setProperty(msgi, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FINISH_CONFIRM);
								setProperty(msgi, Constants.MSG_HEAD_TARGET, from);
								setProperty(msgi, Constants.MSG_HEAD_TEST_SUCC, true);
								client.getHub().getPipe(Constants.HUB_TASK_COMMUNICATION).send(msgi);
								client.testFinished(itest);
							} else {
								if (result != Constants.MSG_TEST_SUCC) {
									log.error("Test[" + taskID + Constants.TASK_TEST_SPLITER + testId + "] received unsuccess execution result:" + result);
								} else if (!isSucc) {
									log.error("Test["+ taskID + Constants.TASK_TEST_SPLITER + testId + "] got result, but some of mandatory result files missing:" + CommonUtils.toJson(missingFiles));
								}
								client.testFail(testId, errorMsg);
							}
							client.triggerWaitingTest(test.getRequirements(), from);
						}
						break;
					default:
						log.warn("Can't handle this transction Item: " + state);
				}
			} else {
				log.warn("Received a mismatched message belong to task:" + taskID);
			}
		} catch (Exception e) {
			log.error("Handling Message in TaskCommunicator got Exception. Message:" + CommonUtils.toJson(message), e);
		} finally {
			lock.release();
		}
	}

	private boolean checkResultFilesValidation(Test test, Map<String, Boolean> missingFiles) {
		File parent = new File(client.getWorkspace(), "results/" + test.getId());
		missingFiles.putAll(test.getResultFiles());
		boolean result = true;
		if (parent.exists() && parent.isDirectory()) {
			for (String f : test.getResultFiles().keySet()) {
				FileFilter fileFilter = new WildcardFileFilter(f);
				File[] fs = parent.listFiles(fileFilter);
				if (fs != null && fs.length > 0) {
					missingFiles.remove(f);
				}
			}
		}
		for (boolean r : missingFiles.values()) {
			if (r) {
				result = false;
				break;
			}
		}
		return result;
	}

}
