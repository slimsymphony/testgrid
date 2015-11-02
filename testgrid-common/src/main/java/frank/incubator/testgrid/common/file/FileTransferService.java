package frank.incubator.testgrid.common.file;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.jms.Message;

import org.apache.commons.io.FileUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.reflect.TypeToken;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.MessageListenerAdapter;
import frank.incubator.testgrid.common.message.Pipe;

/**
 * This is business logic service, which provide a non-dependency file transfer
 * service for testgrid entities.<br/>
 * e.g.: transfer from agent to client or from client to agent or even from
 * agent to agent.<br/>
 * So it should include the steps: introspection, negotiation, action,
 * verification.<br/>
 * 
 * @author Wang Frank
 * 
 */
public class FileTransferService extends MessageListenerAdapter {

	private FileTransferDescriptor descriptor;
	private MessageHub hub;
	private File workspace;
	final private Map<String, FileTransferTask> sendTasks = new ConcurrentHashMap<String, FileTransferTask>();
	final private Map<String, Map<String, Object>> incomingTasks = new ConcurrentHashMap<String, Map<String, Object>>();
	private ListeningExecutorService pool = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
	private ScheduledExecutorService timeoutCheckPool = Executors.newScheduledThreadPool(1);
	private long timeout = Constants.ONE_MINUTE * 5;
	public static int RETRY_TIMES = 2;
	private boolean supportShareZone = false;
	private File shareZone;
	@SuppressWarnings("serial")
	private Set<String> shareZoneFileTypes = new HashSet<String>() {
		{
			add("apk");
			add("ipa");
			add("zip");
		}
	};
	private long shareFileMinSize = 1024L * 1024L;
	private long shareFileAvailableTimeout = Constants.ONE_MINUTE * 10;

	public FileTransferService(MessageHub hub, FileTransferDescriptor descriptor, File workspace) {
		this(hub, descriptor, workspace, null);
	}

	public FileTransferService(MessageHub hub, FileTransferDescriptor descriptor, File workspace, OutputStream os) {
		super((hub == null ? "FileTransferService" : "FileTransferService_" + hub.getHostType()), os);
		this.hub = hub;
		this.descriptor = descriptor;
		this.workspace = workspace;
		introspection();
		timeoutCheckPool.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				checkTimeoutTasks();
			}
		}, 1, 300, TimeUnit.SECONDS);
	}
	
	public FileTransferService(MessageHub hub, FileTransferDescriptor descriptor, File workspace, OutputStream os, File shareZone, Set<String> exts) {
		super((hub == null ? "FileTransferService" : "FileTransferService_" + hub.getHostType()), os);
		this.hub = hub;
		this.descriptor = descriptor;
		this.workspace = workspace;
		if (shareZone != null) {
			this.shareZone = shareZone;
			this.setSupportShareZone(true);
			if(exts != null) {
				this.shareZoneFileTypes.addAll(exts);
			}
		} else {
			this.setSupportShareZone(false);
		}
		introspection();
		timeoutCheckPool.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				checkTimeoutTasks();
			}
		}, 1, 300, TimeUnit.SECONDS);
	}

	private void checkTimeoutTasks() {
		long current = System.currentTimeMillis();
		for (FileTransferTask t : sendTasks.values()) {
			if (current - t.getStart() > timeout) {
				log.warn("Sending FTTask[" + t.getId() + "] suspected OoS, and exceed the max timeout, will be stopped.");
				retrySendTask(t, t.getId());
			}
		}

		for (String ftTaskId : incomingTasks.keySet()) {
			Map<String, Object> taskI = incomingTasks.get(ftTaskId);
			long start = (long) taskI.get(Constants.MSG_HEAD_TIMESTAMP);
			if (current - start > timeout) {
				log.warn("Receiving FTTask[" + ftTaskId + "] suspected OoS, and exceed the max timeout, will be stopped.");
				retryIncomingTask(taskI, "Transfer time over max exceed time:" + CommonUtils.convert(timeout), ftTaskId);
			}
		}

	}
	
	public boolean isSupportShareZone() {
		return supportShareZone;
	}

	public void setSupportShareZone(boolean supportShareZone) {
		this.supportShareZone = supportShareZone;
	}

	public File getShareZone() {
		return shareZone;
	}

	public void setShareZone(File shareZone) {
		this.shareZone = shareZone;
	}

	private void introspection() {
		if (this.descriptor != null) {
			Iterator<FileTransferChannel> it = descriptor.getChannels().iterator();
			FileTransferChannel ftc = null;
			while (it.hasNext()) {
				ftc = it.next();
				if (!ftc.validate(log)) {
					log.warn("FTChannel[" + ftc.getId() + "] validate failed.");
					it.remove();
				} else {
					log.info("FTChannel[" + ftc.getId() + "] validate success.");
				}
			}
		}
	}

	public FileTransferDescriptor getDescriptor() {
		return descriptor;
	}

	public void setDescriptor(FileTransferDescriptor descriptor) {
		this.descriptor = descriptor;
	}

	public MessageHub getHub() {
		return hub;
	}

	public void setHub(MessageHub hub) {
		this.hub = hub;
		if (this.hub == null && hub != null) {
			OutputStream os = log.getOs();
			LogUtils.dispose(this.log);
			this.log = LogUtils.get("FileTransferService_" + hub.getHostType(), os);
		}
	}

	public File getWorkspace() {
		return workspace;
	}

	private String trans(int op) {
		switch (op) {
			case Constants.MSG_HEAD_FT_NEGO:
				return "MSG_HEAD_FT_NEGO";
			case Constants.MSG_HEAD_FT_NEGO_BACK:
				return "MSG_HEAD_FT_NEGO_BACK";
			case Constants.MSG_HEAD_FT_PREPARE:
				return "MSG_HEAD_FT_PREPARE";
			case Constants.MSG_HEAD_FT_CONFIRM:
				return "MSG_HEAD_FT_CONFIRM";
			case Constants.MSG_HEAD_FT_START:
				return "MSG_HEAD_FT_START";
			case Constants.MSG_HEAD_FT_SUCC:
				return "MSG_HEAD_FT_SUCC";
			case Constants.MSG_HEAD_FT_FAIL:
				return "MSG_HEAD_FT_FAIL";
			case Constants.MSG_HEAD_FT_TIMEOUT:
				return "MSG_HEAD_FT_TIMEOUT";
			case Constants.MSG_HEAD_FT_CANCEL:
				return "MSG_HEAD_FT_CANCEL";
			case Constants.MSG_HEAD_FT_SKIP:
				return "MSG_HEAD_FT_SKIP";
			default:
				return "Unknown";
		}
	}

	private void addSendTask(FileTransferTask sendTask) {
		if (sendTask == null) {
			log.error("Can't add NUll Send Task");
		} else {
			String ftTaskId = sendTask.getId();
			if (sendTasks.containsKey(ftTaskId)) {
				log.warn("Add send task covered a old version send task[{}],old:{}, new:{}", ftTaskId, sendTasks.get(ftTaskId), sendTask);
			}
			sendTasks.put(ftTaskId, sendTask);
			log.info("Succ add new sendTask with id:{}", ftTaskId);
		}
	}

	private FileTransferTask getSendTask(String ftTaskId) {
		if (ftTaskId == null) {
			log.error("Can't get Send Task with NUll ftTaskid");
			return null;
		}
		if (sendTasks.containsKey(ftTaskId))
			log.info("Get send FTTask:{}", ftTaskId);
		else
			log.warn("Get send FTTask:{} failed, not exist.", ftTaskId);
		return sendTasks.get(ftTaskId);
	}

	private FileTransferTask removeSendTask(String ftTaskId) {
		if (ftTaskId == null) {
			log.warn("Can't remove send Task with NUll ftTaskid");
			return null;
		}
		FileTransferTask tsk = sendTasks.remove(ftTaskId);
		if (tsk != null) {
			log.info("Remove send FTTask{} success.", ftTaskId);
		} else {
			log.warn("Can't remove send FTTask{}, didn't exist.", ftTaskId);
		}
		return tsk;
	}

	private void addIncomingTask(String ftTaskId, Map<String, Object> taskR) {
		if (ftTaskId == null || taskR == null) {
			log.error("Can't add new incoming task cos null for taskId:" + ftTaskId + ", or taskInstance:" + CommonUtils.toJson(taskR));
		} else {
			if (incomingTasks.containsKey(ftTaskId)) {
				log.warn("Add incoming task covered a old version incoming task[{}]. old:{}, new:{}", ftTaskId,
						CommonUtils.toJson(incomingTasks.get(ftTaskId)), CommonUtils.toJson(taskR));
			}
			incomingTasks.put(ftTaskId, taskR);
			log.info("Succ add new incoming Task with id:{}", ftTaskId);
		}
	}

	private Map<String, Object> getIncomingTask(String ftTaskId) {
		if (ftTaskId == null) {
			log.warn("Can't remove Incoming Task with NUll ftTaskid");
			return null;
		}
		if (incomingTasks.containsKey(ftTaskId))
			log.info("Get incoming FTTask:{}", ftTaskId);
		else
			log.info("Get incoming FTTask:{} failed, not exist.", ftTaskId);
		return incomingTasks.get(ftTaskId);
	}

	private Map<String, Object> removeIncomingTask(String ftTaskId) {
		if (ftTaskId == null) {
			log.warn("Can't remove incoming Task with NUll ftTaskid");
			return null;
		}
		Map<String, Object> tsk = incomingTasks.remove(ftTaskId);
		if (tsk != null) {
			log.info("Remove incoming FTTask{} success.", ftTaskId);
		} else {
			log.warn("Can't remove incoming FTTask{}, didn't exist.", ftTaskId);
		}
		return tsk;
	}

	public void sendTo(FileTransferTask ftTask) throws FileTransferException {
		if (ftTask != null)
			log.info("Start to send Fts Task[{}]", ftTask.getId());
		else {
			log.error("Can't send a null task.");
			throw new FileTransferException("Can't send a null task.");
		}

		if (ftTask.getRetry() == 0)
			ftTask.setStart(System.currentTimeMillis());

		log.info("Add FTTask[{}] to taskList first.", ftTask.getId());
		addSendTask(ftTask);

		Pipe pipe = hub.getPipe(Constants.HUB_FILE_TRANSFER);
		if (ftTask.getFiles() == null || ftTask.getFiles().isEmpty())
			throw new FileTransferException("Given file lists is Empty, " + ftTask.getTargetUri() + "[" + ftTask.getTaskId() + "<" + ftTask.getTestId() + ">].");

		for (File f : ftTask.getFiles())
			if (!f.exists() || !f.isFile())
				throw new FileTransferException("Given file[" + f.getAbsolutePath() + "] not a validated File, " + ftTask.getTargetUri() + "["
						+ ftTask.getTaskId() + "<" + ftTask.getTestId() + ">].");

		log.info("Start to send " + ftTask.getFiles().size() + " files to " + ftTask.getTargetUri() + " with id:" + ftTask.getId() + "[" + ftTask.getTaskId()
				+ "<" + ftTask.getTestId() + ">].");
		try {
			if (!descriptor.getChannels().isEmpty()) {
				Message msg = pipe.createMessage();
				setProperty(msg, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_NEGO);
				setProperty(msg, Constants.MSG_HEAD_FT_DESCRIPTOR, descriptor.toString());
				setProperty(msg, Constants.MSG_HEAD_TARGET, ftTask.getTargetUri());
				setProperty(msg, Constants.MSG_HEAD_FT_TASKID, ftTask.getId());
				setProperty(msg, Constants.MSG_HEAD_TASKID, ftTask.getTaskId());
				setProperty(msg, Constants.MSG_HEAD_TESTID, ftTask.getTestId());
				pipe.send(msg);
				log.info("FTTask[{}] have been published to target:{}", ftTask.getId(), ftTask.getTargetUri());
			} else {
				log.warn("Current descriptor channel is Empty:{}", CommonUtils.toJson(descriptor));
			}
		} catch (Exception ex) {
			throw new FileTransferException("From " + hub.getHostType() + " sendTo " + ftTask.getTargetUri() + " with Id:" + ftTask.getId() + "["
					+ ftTask.getTaskId() + " : Test<" + ftTask.getTestId() + ">] met exception.", ex);
		}
	}

	public FileTransferDescriptor negotiation(FileTransferDescriptor incoming) {
		FileTransferDescriptor reply = new FileTransferDescriptor();
		if (incoming != null && incoming.getChannels() != null) {
			for (FileTransferChannel ftc : incoming.getChannels()) {
				if (ftc.apply(log)) {
					reply.addChannel(ftc);
					log.info("FileTransferChannel[" + ftc.getId() + "] apply success!");
				} else {
					log.warn("FileTransferChannel[" + ftc.getId() + "] apply failed, not supported.");
				}
			}
		}
		return reply;
	}

	private Map<String, Long> convert(Collection<File> files) {
		Map<String, Long> fm = new HashMap<String, Long>();
		for (File f : files) {
			fm.put(f.getName(), f.length());
		}
		return fm;
	}

	private void prepare(final Pipe pipe, final Message msg, final String from, final int op, 
			final String taskId, final String testId, final String ftTaskId, final Map<String, Object> taskI) throws MessageException {
		pool.execute(new Runnable() {
			@Override
			public void run() {
				boolean opSucc = true;
				String errorReason = null;
				log.info("Got a prepare message for FtTask[" + ftTaskId + "]");
				FileTransferChannel channel = null;
				try {
					String channelClass = getProperty(msg, Constants.MSG_HEAD_FT_CHANNEL_CLASS, "");
					String chn = getProperty(msg, Constants.MSG_HEAD_FT_CHANNEL, "");
					log.info("Sender Pickup channel:" + channelClass);
					String artis = getProperty(msg, Constants.MSG_HEAD_FT_ARTIFACTS, "");
					Map<String, Long> artifacts = null;
					List<String> shareFiles = new ArrayList<String>();
					if (artis != null && !artis.isEmpty()) {
						try {
							artifacts = CommonUtils.fromJson(artis, new TypeToken<Map<String, Long>>() {
							}.getType());
							if (artifacts == null || artifacts.isEmpty()) {
								opSucc = false;
								errorReason = "Receive Artifacts info is Empty.";
							} else {
								if (isSupportShareZone()) {
									// check share-zone to filter files didn't need to transfer
									File fuc = null, fucDesc = null;
									long expectFileSize = 0L;
									String fileName = null;
									for (Entry<String, Long> entry : artifacts.entrySet()) {
										fileName = entry.getKey();
										expectFileSize = entry.getValue().longValue();
										String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
										if (expectFileSize > shareFileMinSize && shareZoneFileTypes.contains(ext)) {
											String sfdName = null;
											String prefix = null;
											String postpart = null;
											long sfSize = 0L; 
											Map<Long, File> candidates = new HashMap<Long, File>();
											for(File sf : getShareZone().listFiles()) {
												sfdName = sf.getName();
												prefix = sfdName.substring(0, sfdName.lastIndexOf("-"));
												postpart = sfdName.replace(prefix, "");
												sfSize = CommonUtils.parseLong(postpart.substring(1, postpart.indexOf(".")), 0);
												if(sfdName.endsWith(".desc") && sfdName.contains(fileName) && sfSize == expectFileSize) {
													log.info("Find a potiential match desc file:{}", sfdName);
													fuc = new File(getShareZone(), sfdName.substring(0, sfdName.lastIndexOf(".")));
													boolean entityExist = fuc.exists();
													if(entityExist) {
														fucDesc = sf;
														candidates = null;
														break;
													}else {
														long now = System.currentTimeMillis();
														if((now - sf.lastModified()) > shareFileAvailableTimeout) {
															fuc = null;
															FileUtils.deleteQuietly(sf);
															continue;
														}else {
															File workfolder = new File(workspace, prefix);
															long wmodified = workfolder.lastModified();
															if(workfolder.exists() && (now-wmodified)<shareFileAvailableTimeout) {
																candidates.put(wmodified, sf);
															}else {
																continue;
															}
														}
													}
												}
											}
											
											if(candidates != null && !candidates.isEmpty()) {
												List<Long> cdkeys = new ArrayList<Long>(candidates.keySet());
												Collections.sort(cdkeys);
												fucDesc = candidates.get(cdkeys.get(0));
												fuc = new File(getShareZone(), fucDesc.getName().substring(0, fucDesc.getName().lastIndexOf(".")));
											}
											
											if(fucDesc == null)
												fucDesc = new File(getShareZone(), testId+"-"+expectFileSize+"."+fileName+".desc");
											if(fuc == null)
												fuc = new File(getShareZone(), testId+"-"+expectFileSize+"."+fileName);
											
											long descFileLastUpdate = 0L;
											if (fucDesc.exists()) {
												log.info("Find a potiential match file:{}.", entry.getKey());
												String fTestId = sfdName.substring(0, sfdName.lastIndexOf("-"));
												descFileLastUpdate = fucDesc.lastModified();
												// check whether the file is complete 
												if (fuc.exists() && fuc.isFile()) {
													log.info("Target shareFile:{} from test:{} in share-zone. which lastUpdate@{}",
																fuc.getAbsolutePath(), fTestId, CommonUtils.parse(fuc.lastModified()));
													// copy to workspace
													FileUtils.copyFile(fuc, new File(new File(workspace, testId), fileName));
													shareFiles.add(fileName);
												} else {
													// check lastupdate time whether it in transfering state or not same file.
													long current = System.currentTimeMillis();
													long waitTime = current - descFileLastUpdate;
													if (waitTime > shareFileAvailableTimeout) {// give up 
														log.warn("Maybe the share file[{}] transfer met problem, timeout[{}], give it up.",
																fileName, CommonUtils.convert(waitTime));
														FileUtils.deleteQuietly(fucDesc);
														continue;
													} else {
															File suspectFile = new File(new File(workspace, testId), fileName);
															if (!suspectFile.exists()) {
																if(waitTime < Constants.ONE_SECOND * 15)
																	Thread.sleep(Constants.ONE_SECOND * 15 - waitTime);
															}
															if (!suspectFile.exists()) {
																log.info("Suspect target file[{}] still not exist, continue with normal workflow.",
																	fileName);
																continue;
															} else {
																long step1 = suspectFile.length();
																Thread.sleep(Constants.ONE_SECOND * 3);
																long step2 = suspectFile.length();
																long per = step2 - step1;
																if (per == 0) {
																	log.info("Detect the file:{} not downloading, no need to wait",
																			suspectFile.getAbsolutePath());
																} else {
																	float left = (expectFileSize / per) * 3f;
																	if (left <= 30) {
																		int counter = 0;
																		while (!fuc.exists()) {
																			Thread.sleep(Constants.ONE_SECOND * 10);
																			if (counter == 3) {
																				log.warn("wait over 30 secs. file:{} still not end. break!",
																						fuc.getAbsolutePath());
																				break;
																			}
																		}
																		if (fuc.exists()) {
																			FileUtils.copyFile(fuc, new File(new File(workspace, testId), fileName));
																			shareFiles.add(fileName);
																			log.info("Target shareFile:{} in share-zone. which lastUpdate@{}",
																					fuc.getAbsolutePath(), CommonUtils.parse(fuc.lastModified()));
																		} else {
																			continue;
																		}
																	}
																}
															}
														}
													}
											} else { // set desc flag First
												//CommonUtils.writeFile(fucDesc, "", "UTF-8");
												fucDesc.createNewFile();
												log.info("Create Share Desc File[{}] in shareZone. testId:{} content:{}", fucDesc.getAbsolutePath(),
														testId, testId + "$" + expectFileSize);
											}
										}
									}
									for (String file : shareFiles) {
										artifacts.remove(file);
									}
								}
							}
						} catch (Exception ex) {
							opSucc = false;
							errorReason = "Receive Artifacts info failed.";
							log.error("prepare FtTask:" + ftTaskId + " transfer failed.", ex);
						}
					}
					Map<String, Object> newTask = null;
					if (opSucc) {
						if (!chn.isEmpty() && !channelClass.isEmpty()) {
							try {
								channel = (FileTransferChannel) CommonUtils.fromJson(chn, Class.forName(channelClass));
								if (channel == null) {
									opSucc = false;
									errorReason = "Can't initial Channel Instance:" + chn + "=" + channelClass;
								} else {
									if (taskI != null) {
										log.warn("There already existing a FileTransferTask[" + ftTaskId + "] in incoming task list:["
												+ CommonUtils.toJson(taskI) + "], remove it.");
										taskI.clear();
										taskI.put(Constants.MSG_HEAD_TARGET, from);
										taskI.put(Constants.MSG_HEAD_FT_CHANNEL, channel);
										taskI.put(Constants.MSG_HEAD_FT_ARTIFACTS, artifacts);
										taskI.put(Constants.MSG_HEAD_FT_TASKID, ftTaskId);
										taskI.put(Constants.MSG_HEAD_TESTID, testId);
										taskI.put(Constants.MSG_HEAD_TASKID, taskId);
										taskI.put(Constants.MSG_HEAD_TIMESTAMP, System.currentTimeMillis());
										addIncomingTask(ftTaskId, taskI);
									} else {
										newTask = new HashMap<String, Object>();
										newTask.put(Constants.MSG_HEAD_TARGET, from);
										newTask.put(Constants.MSG_HEAD_FT_CHANNEL, channel);
										newTask.put(Constants.MSG_HEAD_FT_ARTIFACTS, artifacts);
										newTask.put(Constants.MSG_HEAD_FT_TASKID, ftTaskId);
										newTask.put(Constants.MSG_HEAD_TESTID, testId);
										newTask.put(Constants.MSG_HEAD_TASKID, taskId);
										newTask.put(Constants.MSG_HEAD_TIMESTAMP, System.currentTimeMillis());
										addIncomingTask(ftTaskId, newTask);
									}
	
									if (hub.getHostType() == null || hub.getHostType().isEmpty()) {
										log.error("Can't decide where to store received Files.");
										retryIncomingTask(taskI, "HostType is NULL, can't decide where to store received Files.",
												ftTaskId);
									} else {
										if(taskI != null)
											taskI.put(Constants.MSG_HEAD_FT_TARGETFOLDER, getTargetFolder(ftTaskId));
										else
											newTask.put(Constants.MSG_HEAD_FT_TARGETFOLDER, getTargetFolder(ftTaskId));
									}
								}
							} catch (ClassNotFoundException e) {
								log.error("Invalid Channel Class Name Found:" + chn + "=" + channelClass, e);
								opSucc = false;
								errorReason = "Invalid Channel Class Name Found:" + chn + "=" + channelClass;
							} catch (Exception ex) {
								log.error("Exception found when prepare File receiving:" + chn + "=" + channelClass, ex);
								opSucc = false;
								errorReason = "Exception found when prepare File receiving:" + ex.getMessage();
							}
						} else {
							opSucc = false;
							errorReason = "Empty incoming channel[" + chn + "] or channelClass[" + channelClass + "] for ftTask[" + ftTaskId + "]";
						}
					}
					log.info("Extract artifacts list is " + opSucc + ",errorReason:" + errorReason);
					Message m = pipe.createMessage();
					setProperty(m, Constants.MSG_HEAD_TARGET, from);
					setProperty(m, Constants.MSG_HEAD_FT_TASKID, ftTaskId);
					setProperty(m, Constants.MSG_HEAD_TASKID, taskId);
					setProperty(m, Constants.MSG_HEAD_TESTID, testId);
					if (opSucc) {
						setProperty(m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_CONFIRM);
						if(shareFiles != null && !shareFiles.isEmpty())
						setProperty(m, Constants.MSG_HEAD_FT_ARTIFACT_REMOVE, CommonUtils.toJson(shareFiles));
					} else {
						setProperty(m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_FAIL);
						setProperty(m, Constants.MSG_HEAD_ERROR, errorReason);
					}
					pipe.send(m);
				}catch(Exception ex) {
					log.error("FtTask:{} executing prepare step failed.", ex);
					try {
						Message m = pipe.createMessage();
						setProperty(m, Constants.MSG_HEAD_TARGET, from);
						setProperty(m, Constants.MSG_HEAD_FT_TASKID, ftTaskId);
						setProperty(m, Constants.MSG_HEAD_TASKID, taskId);
						setProperty(m, Constants.MSG_HEAD_TESTID, testId);
						setProperty(m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_FAIL);
						setProperty(m, Constants.MSG_HEAD_ERROR, ex.getMessage());
						pipe.send(m);
					}catch(Exception ex1) {
						log.error("send prepare failed message failed. FtTask:{} ", ex1);
					}
				}
			}
		});
	}
	
	@Override
	protected void handleMessage(Message msg) {
		final Pipe pipe = hub.getPipe(Constants.HUB_FILE_TRANSFER);
		try {
			final String from = getProperty(msg, Constants.MSG_HEAD_FROM, "Unknown");
			int op = getProperty(msg, Constants.MSG_HEAD_FT_TRANSACTION, 0);
			final String taskId = getProperty(msg, Constants.MSG_HEAD_TASKID, "");
			final String testId = getProperty(msg, Constants.MSG_HEAD_TESTID, "");
			final String ftTaskId = getProperty(msg, Constants.MSG_HEAD_FT_TASKID, "");
			log.info("Received a incoming ft message from " + from + " with operation code:" + trans(op) + " for FTtaskId:" + ftTaskId);
			final FileTransferTask taskS = getSendTask(ftTaskId);
			final Map<String, Object> taskI = getIncomingTask(ftTaskId);
			String desc = null;
			FileTransferDescriptor descriptor = null;
			FileTransferDescriptor response = null;
			ListenableFuture<Boolean> future = null;
			Message m = null;
			String errorReason = "";
			switch (op) {
				case Constants.MSG_HEAD_FT_NEGO: // RECEIVE SIDE
					desc = getProperty(msg, Constants.MSG_HEAD_FT_DESCRIPTOR, "");
					log.info("Got FT Negotiation message:" + desc);
					descriptor = CommonUtils.fromJson(desc, FileTransferDescriptor.class);
					response = negotiation(descriptor);
					m = pipe.createMessage();
					setProperty(m, Constants.MSG_HEAD_TARGET, from);
					setProperty(m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_NEGO_BACK);
					setProperty(m, Constants.MSG_HEAD_FT_DESCRIPTOR, response.toString());
					setProperty(m, Constants.MSG_HEAD_FT_TASKID, ftTaskId);
					setProperty(m, Constants.MSG_HEAD_TASKID, taskId);
					setProperty(m, Constants.MSG_HEAD_TESTID, testId);
					pipe.send(m);
					log.info("Negotiation finish, send back response.");
					break;
				case Constants.MSG_HEAD_FT_NEGO_BACK: // SEND SIDE
					log.info("Got a NegotiationBack message for FtTask[" + ftTaskId + "]");
					desc = getProperty(msg, Constants.MSG_HEAD_FT_DESCRIPTOR, "");
					log.info("Got FT Negotiation feedback :" + desc);
					FileTransferDescriptor descriptorRemote = CommonUtils.fromJson(desc, FileTransferDescriptor.class);
					List<FileTransferChannel> chns = new ArrayList<FileTransferChannel>();
					for (FileTransferChannel ftc : descriptorRemote.getChannels()) {
						for (FileTransferChannel oftc : this.descriptor.getChannels()) {
							if (ftc.getId().equals(oftc.getId())) {
								chns.add(oftc);
								break;
							}
						}
					}
					Collections.sort(chns);
					descriptorRemote.setChannels(chns);
					taskS.setDescriptor(descriptorRemote);
					if (descriptorRemote != null && !descriptorRemote.getChannels().isEmpty()) {
						Collections.sort(descriptorRemote.getChannels());
						m = pipe.createMessage();
						FileTransferChannel channel = null;
						FileTransferChannel cha = null;
						for (int i = 0; i < descriptorRemote.getChannels().size(); i++) {
							cha = descriptorRemote.getChannels().get(i);
							if (taskS.getCurrentChannel() != null && taskS.getCurrentChannel().getPriority() <= cha.getPriority())
								continue;
							else {
								channel = cha;
								break;
							}
						}
						if (channel != null) {
							log.info("Choose Channel[" + channel.getId() + "] to transfer files for fttask[" + ftTaskId + "]");
							taskS.setCurrentChannel(channel);
							setProperty(m, Constants.MSG_HEAD_TARGET, from);
							setProperty(m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_PREPARE);
							setProperty(m, Constants.MSG_HEAD_FT_CHANNEL, channel.toString());
							setProperty(m, Constants.MSG_HEAD_FT_CHANNEL_CLASS, channel.getClass().getName());
							setProperty(m, Constants.MSG_HEAD_FT_TASKID, ftTaskId);
							setProperty(m, Constants.MSG_HEAD_TASKID, taskId);
							setProperty(m, Constants.MSG_HEAD_TESTID, testId);
							setProperty(m, Constants.MSG_HEAD_FT_ARTIFACTS, CommonUtils.toJson(convert(taskS.getFiles())));
							pipe.send(m);
						} else {
							notifyFtFinish(taskS, false, "Can't find proper FileTransfer channel, no need to retry. Stop transfering.");
						}

					}
					break;
				case Constants.MSG_HEAD_FT_PREPARE: // RECEIVE SIDE
					this.prepare(pipe, msg, from, op, taskId, testId, ftTaskId, taskI);
					break;
				case Constants.MSG_HEAD_FT_CONFIRM: // SEND SIDE
					log.info("Start transfer Files.FtTaskId:" + ftTaskId);
					String removeArtis = getProperty(msg, Constants.MSG_HEAD_FT_ARTIFACT_REMOVE, "");
					log.info("Remove Artifact info:" + removeArtis);
					if(!"".equals(removeArtis)) {
						@SuppressWarnings("unchecked")
						List<String> removeFiles = (List<String>)CommonUtils.fromJson(removeArtis, List.class);
						if(removeFiles != null && !removeFiles.isEmpty()) {
							Iterator<File> it = taskS.getFiles().iterator();
							while(it.hasNext()) {
								File f = it.next();
								if(removeFiles.contains(f.getName())) {
									log.info("Remove Artifact file:{} before transfer.", f.getName());
									it.remove();
								}
							}
						}
					}
					if(taskS.getFiles().isEmpty()) {
						try {
							Message mm = pipe.createMessage();
							setProperty(mm, Constants.MSG_HEAD_TARGET, from);
							setProperty(mm, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_SKIP);
							setProperty(mm, Constants.MSG_HEAD_FT_TASKID, ftTaskId);
							setProperty(mm, Constants.MSG_HEAD_TASKID, taskId);
							setProperty(mm, Constants.MSG_HEAD_TESTID, testId);
							pipe.send(mm);
						} catch (Exception ex) {
							log.error("Notify Receive Side sending finished failed.", ex);
							retrySendTask(taskS, ftTaskId);
						}
						break;
					}
					future = pool.submit(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return taskS.getCurrentChannel().send(ftTaskId, taskS.getFiles(), log);
						}
					});

					Futures.addCallback(future, new FutureCallback<Boolean>() {
						public void onSuccess(Boolean result) {
							if (result) {
								log.info("FileTransferTask[" + ftTaskId + "] have been sent successfully.");
								try {
									Message m = pipe.createMessage();
									setProperty(m, Constants.MSG_HEAD_TARGET, from);
									setProperty(m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_START);
									setProperty(m, Constants.MSG_HEAD_FT_TASKID, ftTaskId);
									setProperty(m, Constants.MSG_HEAD_TASKID, taskId);
									setProperty(m, Constants.MSG_HEAD_TESTID, testId);
									pipe.send(m);
								} catch (Exception ex) {
									log.error("Notify Receive Side sending finished failed.", ex);
									retrySendTask(taskS, ftTaskId);
								}
							} else {
								log.error("FileTransferTask[" + ftTaskId + "] not sent succeed.");
								retrySendTask(taskS, ftTaskId);
							}
						}

						public void onFailure(Throwable thrown) {
							log.error("FileTransferTask[" + ftTaskId + "] sent failed.", thrown);
							retrySendTask(taskS, ftTaskId);
						}
					});
					break;
				case Constants.MSG_HEAD_FT_SKIP: // RECEIVE SIDE
					log.info("Got a SKIP transfer message for FtTask[" + ftTaskId + "]");
					log.info("FileTransferTask[" + ftTaskId + " have been received successfully.");
					try {
						Message message = pipe.createMessage();
						setProperty(message, Constants.MSG_HEAD_TARGET, from);
						setProperty(message, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_SUCC);
						setProperty(message, Constants.MSG_HEAD_FT_TASKID, ftTaskId);
						setProperty(message, Constants.MSG_HEAD_TASKID, taskId);
						setProperty(message, Constants.MSG_HEAD_TESTID, testId);
						pipe.send(message);
						removeIncomingTask(ftTaskId);
						log.info("Receiving FTTask[" + ftTaskId + "] removed from Task queue by reason: success.");
					} catch (Exception ex) {
						log.error("Send File transfer Succ back to SentSide met exception.ftTaskId=" + ftTaskId, ex);
						retryIncomingTask(taskI, "Send File transfer Succ back to SentSide met exception.ftTaskId=" + ftTaskId, ftTaskId);
					}
					break;
				case Constants.MSG_HEAD_FT_START: // RECEIVE SIDE
					log.info("Got a Start message for FtTask[" + ftTaskId + "]");
					if (taskI != null) {
						final FileTransferChannel chl = (FileTransferChannel) taskI.get(Constants.MSG_HEAD_FT_CHANNEL);
						if (chl == null) {
							log.error("FileTransferChannel is NULL!");
							retryIncomingTask(taskI, "FileTransferChannel is NULL", ftTaskId);
						} else {
							future = pool.submit(new Callable<Boolean>() {
								@SuppressWarnings("unchecked")
								@Override
								public Boolean call() throws Exception {
									return chl.receive(ftTaskId, (Map<String, Long>) taskI.get(Constants.MSG_HEAD_FT_ARTIFACTS),
											(File) taskI.get(Constants.MSG_HEAD_FT_TARGETFOLDER), log);
								}
							});

							Futures.addCallback(future, new FutureCallback<Boolean>() {
								public void onSuccess(Boolean result) {
									if (result) {
										log.info("FileTransferTask[" + ftTaskId + " have been received successfully.");
										try {
											Message message = pipe.createMessage();
											setProperty(message, Constants.MSG_HEAD_TARGET, from);
											setProperty(message, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_SUCC);
											setProperty(message, Constants.MSG_HEAD_FT_TASKID, ftTaskId);
											setProperty(message, Constants.MSG_HEAD_TASKID, taskId);
											setProperty(message, Constants.MSG_HEAD_TESTID, testId);
											pipe.send(message);
											removeIncomingTask(ftTaskId);
											log.info("Receiving FTTask[" + ftTaskId + "] removed from Task queue by reason: success.");
											if (supportShareZone) {
												log.info("check sharezone file operation for test:{}", taskId + ":::" + testId);
												File targetFolder = new File(workspace, testId);
												for (File f : targetFolder.listFiles()) {
													String fn = f.getName();
													String ext = fn.substring(fn.lastIndexOf(".") + 1).toLowerCase();
													if (f.isFile() && f.length() >= shareFileMinSize && shareZoneFileTypes.contains(ext)) {
														log.info("file:{} could be share in zone.", fn);
														boolean found = false;
														for(File sf : shareZone.listFiles()) {
															if(sf.getName().endsWith(f.length()+"."+fn) && sf.length() == f.length()) {
																log.info("Found File:{} same as shareFile:{} ignore.", f.getAbsolutePath(), sf.getAbsolutePath());
																File fsd = new File(shareZone, testId+"-"+f.length()+"."+fn+".desc");
																if(fsd.exists())
																	FileUtils.deleteQuietly(fsd);
																found = true;
																break;
															}
														}
														if(!found) {
															File fis = new File(shareZone, testId+"-"+f.length()+"."+fn);
															File fisd = new File(shareZone, fis.getName() + ".desc");
															if (fis.exists()) {
																log.info("File:{} already exists in sharezone.", fn);
																if(!fisd.exists())
																	fisd.createNewFile();
																continue;
															} else {
																if (fisd.exists()) {
																	FileUtils.copyFile(f, fis);
																} else {
																	FileUtils.copyFile(f, fis);
																	fisd.createNewFile();
																}
															}
														}
													}
												}
											}
										} catch (Exception ex) {
											log.error("Send File transfer Succ back to SentSide met exception.ftTaskId=" + ftTaskId, ex);
											retryIncomingTask(taskI, "Send File transfer Succ back to SentSide met exception.ftTaskId=" + ftTaskId, ftTaskId);
										}
									} else {
										log.error("FileTransferTask[" + ftTaskId + "] not sent succeed.");
										retryIncomingTask(taskI, "FileTransferTask[" + ftTaskId + "] not sent succeed.", ftTaskId);
									}
								}

								public void onFailure(Throwable thrown) {
									log.error("FileTransferTask[" + ftTaskId + "] receiving failed.", thrown);
									retryIncomingTask(taskI, "FileTransferTask[" + ftTaskId + "] receiving failed.", ftTaskId);
								}
							});
						}
					} else {
						// retry( taskI );
						log.error("Can't find related incomingTask, ftTaskId:" + ftTaskId + ", incomingTasks:" + CommonUtils.toJson(incomingTasks));
						retryIncomingTask(null, "Can't find related incomingTask, ", ftTaskId);
					}
					break;
				case Constants.MSG_HEAD_FT_SUCC: // SEND SIDE
					log.info("FTTask[" + ftTaskId + "] finished successfully!");
					notifyFtFinish(taskS, true, null);
					this.removeSendTask(ftTaskId);
					log.info("Send FTTask[" + ftTaskId + "] removed from Task queue by reason: success.");
					break;
				case Constants.MSG_HEAD_FT_FAIL:
					errorReason = getProperty(msg, Constants.MSG_HEAD_ERROR, "");
					log.error("Got a Failure message for FtTask[" + ftTaskId + "] for reason:" + errorReason);
					if (taskS != null)
						retrySendTask(taskS, taskS.getId());
					else if (taskI != null)
						retryIncomingTask(taskI, errorReason, ftTaskId);
					break;
				case Constants.MSG_HEAD_FT_TIMEOUT: // RECEIVE SIDE
					log.info("Got a Timout message for FtTask[" + ftTaskId + "]");
					if (taskS != null)
						retrySendTask(taskS, taskS.getId());
					else if (taskI != null)
						retryIncomingTask(taskI, "Got a Timout message for FtTask[" + ftTaskId + "]", ftTaskId);
					break;
				case Constants.MSG_HEAD_FT_CANCEL: // BOTH SIDE
					if (taskS != null)
						cancelSendTask(ftTaskId);
					else
						cancelIncomingTask(ftTaskId);
					break;
			}
		} catch (Exception ex) {
			log.error("FileTransferService got a exception when handle incoming message.", ex);
		}
	}

	public void retrySendTask(FileTransferTask taskS, String ftTaskId) { // SEND
																			// SIDE
		log.warn("Prepare to retry send FTTask:" + ftTaskId + ", detail:" + taskS);
		if (taskS != null) {
			taskS.setRetry(taskS.getRetry() + 1);
			log.info("Begin to retry sending FtTask[" + taskS.getId() + "] again, it's the " + taskS.getRetry() + " times.");
			if (taskS.getRetry() < RETRY_TIMES) {
				taskS.setCurrentChannel(null);
				taskS.setDescriptor(null);
				try {
					this.removeSendTask(ftTaskId);
					sendTo(taskS);
				} catch (FileTransferException e) {
					retrySendTask(taskS, ftTaskId);
				}
			} else {
				notifyFtFinish(taskS, false, "File Transfer failure and exceed the maxium retry times, ftTask:" + taskS);
				this.removeSendTask(ftTaskId);
				log.info("Send FTTask[" + taskS.getId() + "] removed from Task queue by reason: exceed max retry times.");
			}
		} else {
			taskS = this.getSendTask(ftTaskId);
			if (taskS == null) {
				log.warn("Got a Null FileTransferTask, can't retry, FTTaskId:" + ftTaskId);
				notifyFtFinish(taskS, false, "File Transfer failure and can't retry, ftTask:" + ftTaskId);
			} else {
				log.info("ReGet SendTask with ftTaskId:" + ftTaskId);
				retrySendTask(taskS, ftTaskId);
			}
		}
	}

	public void retryIncomingTask(Map<String, Object> taskI, String errorReason, String ftTaskId) { // RECEIVE
																									// SIDE
		if (taskI == null) {
			log.error("Give retry incoming Task is NULL. FTTaskId:" + ftTaskId + ", errorReason:" + errorReason);
			cancelIncomingTask(ftTaskId);
			return;
		}
		if (errorReason == null)
			errorReason = "";
		try {
			Pipe pipe = hub.getPipe(Constants.HUB_FILE_TRANSFER);
			Message message = pipe.createMessage();
			setProperty(message, Constants.MSG_HEAD_TARGET, (String) taskI.get(Constants.MSG_HEAD_TARGET));
			setProperty(message, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_FAIL);
			setProperty(message, Constants.MSG_HEAD_ERROR, errorReason);
			setProperty(message, Constants.MSG_HEAD_FT_TASKID, taskI.get(Constants.MSG_HEAD_FT_TASKID));
			setProperty(message, Constants.MSG_HEAD_TASKID, taskI.get(Constants.MSG_HEAD_TASKID));
			setProperty(message, Constants.MSG_HEAD_TESTID, taskI.get(Constants.MSG_HEAD_TESTID));
			pipe.send(message);
			this.removeIncomingTask(ftTaskId);
		} catch (MessageException ex) {
			log.error("Retry Task[" + taskI.get(Constants.MSG_HEAD_FT_TASKID) + "] from receiving side failed.", ex);
		}
	}

	public void cancelSendTask(String ftTaskId) { // SEND SIDE
		try {
			FileTransferTask taskS = removeSendTask(ftTaskId);
			if (taskS == null) {
				log.warn("Try to cancel send Task[{}] failed, can't find task.", ftTaskId);
				return;
			}
			Pipe pipe = hub.getPipe(Constants.HUB_FILE_TRANSFER);
			log.info("FtTask[" + taskS.getId() + "] will be cancelled.");
			Message message = pipe.createMessage();
			setProperty(message, Constants.MSG_HEAD_TARGET, taskS.getTargetUri());
			setProperty(message, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_CANCEL);
			setProperty(message, Constants.MSG_HEAD_FT_TASKID, taskS.getId());
			setProperty(message, Constants.MSG_HEAD_TASKID, taskS.getTaskId());
			setProperty(message, Constants.MSG_HEAD_TESTID, taskS.getTestId());
			pipe.send(message);
		} catch (MessageException ex) {
			log.error("Cancel Task[" + ftTaskId + "] met exception.", ex);
		}
		log.info("FtTask[" + ftTaskId + "] have been cancelled.");
	}

	public void cancelIncomingTask(String ftTaskId) { // RECEIVE SIDE
		try {
			Map<String, Object> taskI = this.removeIncomingTask(ftTaskId);
			if (taskI == null) {
				log.warn("FtTask[" + ftTaskId + "] not in current incoming FtTask queue, no need to cancel.");
				return;
			}
			log.info("Going to cancel incoming FtTask[" + ftTaskId + "].");
		} catch (Exception ex) {
			log.error("Cancel Task[" + ftTaskId + "] failed.", ex);
		}
	}

	public void notifyFtFinish(FileTransferTask taskS, boolean succ, String failureReason) {
		try {
			Pipe pipe = hub.getBroker(Constants.BROKER_TASK).getPipe(Constants.HUB_TASK_COMMUNICATION);
			Message m = pipe.createMessage();
			setProperty(m, Constants.MSG_HEAD_TASKID, taskS.getTaskId());
			setProperty(m, Constants.MSG_HEAD_TESTID, taskS.getTestId());
			if (hub.getHostType() != null && hub.getHostType().equals(Constants.MSG_TARGET_AGENT)) {
				setProperty(m, Constants.MSG_HEAD_TARGET, taskS.getTargetUri());
				setProperty(m, Constants.MSG_HEAD_FROM, taskS.getFrom());
				// RESULT SEND BACK TO CLIENT
				if (succ) {
					setProperty(m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FINISHED);
				} else {
					setProperty(m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FAIL);
					setProperty(m, Constants.MSG_HEAD_ERROR, failureReason);
				}
			} else if (hub.getHostType() != null && hub.getHostType().equals(Constants.MSG_TARGET_CLIENT)) {
				setProperty(m, Constants.MSG_HEAD_TARGET, taskS.getFrom());
				setProperty(m, Constants.MSG_HEAD_FROM, taskS.getTargetUri());
				// ARTIFACTS SEND TO AGENT
				if (succ) {
					setProperty(m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_READY);
				} else {
					setProperty(m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FAIL);
					setProperty(m, Constants.MSG_HEAD_ERROR, failureReason);
				}
			}
			pipe.send(m);
		} catch (Exception ex) {
			log.error("Notify finished failed. ftTask=[" + taskS + "], result:" + succ + ",failureReason:" + failureReason);
		}
	}

	public File getTargetFolder(String ftTaskId) {
		File target = null;
		Map<String, Object> rTask = this.getIncomingTask(ftTaskId);
		if (rTask != null) {
			String testId = (String) rTask.get(Constants.MSG_HEAD_TESTID);
			if (hub.getHostType() != null && hub.getHostType().equals(Constants.MSG_TARGET_AGENT)) {
				target = new File(workspace, testId);
			} else if (hub.getHostType() != null && hub.getHostType().equals(Constants.MSG_TARGET_CLIENT)) {
				target = new File(workspace, "results/" + testId);
			} else {
				return null;
			}

			if (!target.exists())
				target.mkdirs();
			else if (!target.isDirectory()) {
				target.delete();
				target.mkdirs();
			}
		}
		return target;
	}

	public void dispose() {
		if (descriptor != null) {
			if (descriptor.getChannels() != null) {
				for (FileTransferChannel ftc : this.getDescriptor().getChannels()) {
					if (ftc != null) {
						ftc.dispose();
					}
				}
			}
		}
		sendTasks.clear();
		incomingTasks.clear();
		timeoutCheckPool.shutdown();
		pool.shutdown();
		LogUtils.dispose(log);
	}
}