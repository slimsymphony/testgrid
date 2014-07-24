package frank.incubator.testgrid.common.file;

import static frank.incubator.testgrid.common.message.MessageHub.getProperty;
import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.jms.Message;

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
	final private List<FileTransferTask> sendTasks = Collections.synchronizedList( new ArrayList<FileTransferTask>() );
	final private Map<String,Map<String,Object>> incomingTasks = new ConcurrentHashMap<String,Map<String,Object>>();
	private ListeningExecutorService pool = MoreExecutors.listeningDecorator( Executors.newCachedThreadPool() );
	private ScheduledExecutorService timeoutCheckPool =  Executors.newScheduledThreadPool( 1 );
	private long timeout = Constants.ONE_MINUTE * 10;
	public static int RETRY_TIMES = 2;
	
	public FileTransferService( MessageHub hub, FileTransferDescriptor descriptor, File workspace ) {
		this( hub, descriptor, workspace, null );
	}

	public FileTransferService( MessageHub hub, FileTransferDescriptor descriptor, File workspace, OutputStream os ) {
		super( ( hub == null ? "FileTransferService" : "FileTransferService_" + hub.getHostType() ), os );
		this.hub = hub;
		this.descriptor = descriptor;
		this.workspace = workspace;
		introspection();
		timeoutCheckPool.scheduleAtFixedRate( new Runnable() {
			@Override
			public void run() {
				checkTimeoutTasks();
			}}, 1, 300, TimeUnit.SECONDS );
	}
	
	private void checkTimeoutTasks() {
		long current = System.currentTimeMillis();
		for( FileTransferTask t : sendTasks ) {
			if( current - t.getStart() > timeout ) {
				log.warn( "Sending FTTask[" + t.getId()+"] suspected OoS, and exceed the max timeout, will be stopped." );
				retry( t );
			}
		}
		
		for( String ftTaskId : incomingTasks.keySet() ) {
			Map<String,Object> taskI = incomingTasks.get( ftTaskId );
			long start = (long)taskI.get( Constants.MSG_HEAD_TIMESTAMP );
			if( current - start > timeout ) {
				log.warn( "Receiving FTTask[" + ftTaskId+"] suspected OoS, and exceed the max timeout, will be stopped." );
				retry( taskI );
			}
		}
		
	}
	
	private void introspection() {
		if ( this.descriptor != null ) {
			Iterator<FileTransferChannel> it = descriptor.getChannels().iterator();
			FileTransferChannel ftc = null;
			while ( it.hasNext() ) {
				ftc = it.next();
				if ( !ftc.validate() ) {
					log.warn( "FTChannel[" + ftc.getId() + "] validate failed." );
					it.remove();
				}else {
					log.info( "FTChannel[" + ftc.getId() + "] validate success." );
				}
			}
		}
	}

	public FileTransferDescriptor getDescriptor() {
		return descriptor;
	}

	public void setDescriptor( FileTransferDescriptor descriptor ) {
		this.descriptor = descriptor;
	}

	public MessageHub getHub() {
		return hub;
	}

	public void setHub( MessageHub hub ) {
		this.hub = hub;
		if( this.hub == null && hub != null ) {
			OutputStream os = log.getOs();
			LogUtils.dispose( this.log );
			this.log = LogUtils.get( "FileTransferService_" + hub.getHostType(), os );
		}
	}

	public File getWorkspace() {
		return workspace;
	}

	private FileTransferTask getSendTask( String ftTaskId ) {
		for( FileTransferTask task : sendTasks ) {
			if( task.getId().equals( ftTaskId ) ) {
				return task;
			}
		}
		return null;
	}
	
	private String trans( int op ) {
		switch( op ){
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
			default:
				return "Unknown";
		}
	}
	
	private Map<String,Object> getIncomingTask( String ftTaskId ){
		return incomingTasks.get( ftTaskId );
	}
	
	public void sendTo( FileTransferTask ftTask ) throws FileTransferException {
		if( ftTask.getRetry() == 0 )
			ftTask.setStart( System.currentTimeMillis() );
		Pipe pipe = hub.getPipe( Constants.HUB_FILE_TRANSFER );
		if( ftTask.getFiles() == null || ftTask.getFiles().isEmpty() )
			throw new FileTransferException( "Given file lists is Empty, " + ftTask.getTargetUri() + "[" + ftTask.getTaskId() + "<" + ftTask.getTestId() + ">]." );
		
		for( File f : ftTask.getFiles() )
			if( !f.exists() || !f.isFile() )
				throw new FileTransferException( "Given file[" + f.getAbsolutePath() + "] not a validated File, " + ftTask.getTargetUri() + "[" + ftTask.getTaskId() + "<" + ftTask.getTestId() + ">]." );
		
		log.info( "Start to send " + ftTask.getFiles().size() + " files to " + ftTask.getTargetUri() + "[" + ftTask.getTaskId() + "<" + ftTask.getTestId() + ">]." );
		try {
			if ( !descriptor.getChannels().isEmpty() ) {
				sendTasks.add( ftTask );
				Message msg = pipe.createMessage();
				setProperty( msg, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_NEGO );
				setProperty( msg, Constants.MSG_HEAD_FT_DESCRIPTOR, descriptor.toString() );
				setProperty( msg, Constants.MSG_HEAD_TARGET, ftTask.getTargetUri() );
				setProperty( msg, Constants.MSG_HEAD_FT_TASKID, ftTask.getId() );
				setProperty( msg, Constants.MSG_HEAD_TASKID, ftTask.getTaskId() );
				setProperty( msg, Constants.MSG_HEAD_TESTID, ftTask.getTestId() );
				pipe.send( msg );
				Collections.sort( sendTasks );
				log.info( "FTTask : " + ftTask.getId() + " have been added into FTtaskList" );
			}
		} catch ( Exception ex ) {
			throw new FileTransferException( "From " + hub.getHostType() + " sendTo " + ftTask.getTargetUri() + " with Task[" + ftTask.getTaskId() + " : Test<" + ftTask.getTestId() + ">] met exception.", ex );
		}
	}
	
	public FileTransferDescriptor negotiation( FileTransferDescriptor incoming ) {
		FileTransferDescriptor reply = new FileTransferDescriptor();
		if( incoming != null && incoming.getChannels() != null ) {
			for( FileTransferChannel ftc : incoming.getChannels() ) {
				if( ftc.apply() ) {
					reply.addChannel( ftc );
					log.info( "FileTransferChannel[" + ftc.getId() + "] apply success!" );
				}else {
					log.warn( "FileTransferChannel[" + ftc.getId() + "] apply failed, not supported." );
				}
			}
		}
		return reply;
	}

	private Map<String,Long> convert( Collection<File> files ) {
		Map<String,Long> fm = new HashMap<String,Long>();
		for( File f : files ) {
			fm.put( f.getName(), f.length() );
		}
		return fm;
	}
	
	@Override
	protected void handleMessage( Message msg ) {
		final Pipe pipe = hub.getPipe( Constants.HUB_FILE_TRANSFER );
		try {
			final String from = getProperty( msg, Constants.MSG_HEAD_FROM, "Unknown" );
			int op = getProperty( msg, Constants.MSG_HEAD_FT_TRANSACTION, 0 );
			final String taskId = getProperty( msg, Constants.MSG_HEAD_TASKID, "" );
			final String testId = getProperty( msg, Constants.MSG_HEAD_TESTID, "" );
			final String ftTaskId = getProperty( msg, Constants.MSG_HEAD_FT_TASKID, "" );
			log.info( "Received a incoming ft message from " + from + " with operation code:" + trans( op ) );
			final FileTransferTask taskS = getSendTask( ftTaskId );
			final Map<String,Object> taskI = getIncomingTask( ftTaskId );
			String desc = null;
			FileTransferDescriptor descriptor = null;
			FileTransferDescriptor response = null;
			ListenableFuture<Boolean> future = null;
			Message m = null;
			boolean opSucc = true;
			String errorReason = "";
			switch( op ) {
				case Constants.MSG_HEAD_FT_NEGO: //RECEIVE SIDE
					desc = getProperty( msg, Constants.MSG_HEAD_FT_DESCRIPTOR, "" );//msg.getStringProperty( Constants.MSG_FT_DESCRIPTOR );
					log.info( "Got FT Negotiation message:" + desc );
					descriptor = CommonUtils.fromJson( desc, FileTransferDescriptor.class );
					response = negotiation( descriptor );
					m = pipe.createMessage();
					setProperty( m, Constants.MSG_HEAD_TARGET, from );
					setProperty( m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_NEGO_BACK );
					setProperty( m, Constants.MSG_HEAD_FT_DESCRIPTOR, response.toString() );
					setProperty( m, Constants.MSG_HEAD_FT_TASKID, ftTaskId );
					setProperty( m, Constants.MSG_HEAD_TASKID, taskId );
					setProperty( m, Constants.MSG_HEAD_TESTID, testId );
					pipe.send( m );
					log.info( "Negotiation finish, send back response." );
					break;
				case Constants.MSG_HEAD_FT_NEGO_BACK: //SEND SIDE
					log.info( "Got a NegotiationBack message for FtTask[" + ftTaskId + "]" );
					desc = getProperty( msg, Constants.MSG_HEAD_FT_DESCRIPTOR, "" );//msg.getStringProperty( Constants.MSG_FT_DESCRIPTOR );
					log.info( "Got FT Negotiation feedback :" + desc );
					descriptor = CommonUtils.fromJson( desc, FileTransferDescriptor.class );
					List<FileTransferChannel> chns = new ArrayList<FileTransferChannel>();
					for( FileTransferChannel ftc : descriptor.getChannels() ) {
						for( FileTransferChannel oftc : this.descriptor.getChannels() ) {
							if( ftc.getId().equals( oftc.getId() ) ) {
								chns.add( oftc );
								break;
							}
						}
					}
					Collections.sort( chns );
					descriptor.setChannels( chns );
					taskS.setDescriptor( descriptor );
					if( descriptor != null && !descriptor.getChannels().isEmpty() ) {
						Collections.sort( descriptor.getChannels() );
						m = pipe.createMessage();
						FileTransferChannel channel = null;
						FileTransferChannel cha = null;
						for( int i = 0; i< descriptor.getChannels().size(); i++ ) {
							cha = descriptor.getChannels().get( i );
							if( taskS.getCurrentChannel() != null && taskS.getCurrentChannel().getPriority() <= cha.getPriority() )
								continue;
							else {
								channel = cha;
								break;
							}
						}
						if( channel != null ) {
							log.info( "Choose Channel[" + channel.getId() + "] to transfer files for fttask[" + ftTaskId + "]" );
							taskS.setCurrentChannel( channel );
							setProperty( m, Constants.MSG_HEAD_TARGET, from );
							setProperty( m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_PREPARE );
							setProperty( m, Constants.MSG_HEAD_FT_CHANNEL, channel.toString() );
							setProperty( m, Constants.MSG_HEAD_FT_CHANNEL_CLASS, channel.getClass().getName() );
							setProperty( m, Constants.MSG_HEAD_FT_TASKID, ftTaskId );
							setProperty( m, Constants.MSG_HEAD_TASKID, taskId );
							setProperty( m, Constants.MSG_HEAD_TESTID, testId );
							setProperty( m, Constants.MSG_HEAD_FT_ARTIFACTS, CommonUtils.toJson( convert( taskS.getFiles() ) ) );
							pipe.send( m );
						} else {
							notifyFtFinish( taskS, false, "Can't find proper FileTransfer channel, no need to retry. Stop transfering." );
						}
						
					}
					break;
				case Constants.MSG_HEAD_FT_PREPARE: //RECEIVE SIDE
					log.info( "Got a prepare message for FtTask[" + ftTaskId + "]" );
					FileTransferChannel channel = null;
					String channelClass = getProperty( msg, Constants.MSG_HEAD_FT_CHANNEL_CLASS, "" );
					String chn = getProperty( msg, Constants.MSG_HEAD_FT_CHANNEL, "" );
					log.info( "Sender Pickup channel:" + channelClass );
					String artis = getProperty( msg, Constants.MSG_HEAD_FT_ARTIFACTS, "" );
					Map<String,Long> artifacts = null;
					if( artis != null && !artis.isEmpty() ) {
						try {
							artifacts = CommonUtils.fromJson( artis, new TypeToken<Map<String,Long>>(){}.getType() );
							if( artifacts == null || artifacts.isEmpty() ) {
								opSucc = false;
								errorReason = "Receive Artifacts info is Empty.";
							}
						}catch( Exception ex ) {
							opSucc = false;
							errorReason = "Receive Artifacts info failed.";
						}
					}
					
					if( opSucc ) {
						if( !chn.isEmpty() && !channelClass.isEmpty() ) {
							try {
								channel = ( FileTransferChannel ) CommonUtils.fromJson( chn, Class.forName( channelClass ) );
								if( channel == null ) {
									opSucc = false;
									errorReason = "Can't initial Channel Instance:" + chn + "=" + channelClass;
								}else {
									if( taskI != null )
										log.warn( "There already existing a FileTransferTask[" + ftTaskId + "] in incoming task list:[" + CommonUtils.toJson( incomingTasks.get( ftTaskId ) ) +"], remove it." );
									incomingTasks.put( ftTaskId, new HashMap<String,Object>() );
									incomingTasks.get( ftTaskId ).put( Constants.MSG_HEAD_TARGET, from );
									incomingTasks.get( ftTaskId ).put( Constants.MSG_HEAD_FT_CHANNEL, channel );
									incomingTasks.get( ftTaskId ).put( Constants.MSG_HEAD_FT_ARTIFACTS, artifacts );
									incomingTasks.get( ftTaskId ).put( Constants.MSG_HEAD_FT_TASKID, ftTaskId );
									incomingTasks.get( ftTaskId ).put( Constants.MSG_HEAD_TESTID, testId );
									incomingTasks.get( ftTaskId ).put( Constants.MSG_HEAD_TASKID, taskId );
									incomingTasks.get( ftTaskId ).put( Constants.MSG_HEAD_TIMESTAMP, System.currentTimeMillis() );
									if( hub.getHostType() == null || hub.getHostType().isEmpty() ) {
										log.error( "Can't decide where to store received Files." );
										retry( incomingTasks.get( ftTaskId ) );
									}else {
										incomingTasks.get( ftTaskId ).put( Constants.MSG_HEAD_FT_TARGETFOLDER, getTargetFolder( ftTaskId ) );
									}
								}
							} catch ( ClassNotFoundException e ) {
								log.error( "Invalid Channel Class Name Found:" + chn + "=" + channelClass, e );
								opSucc = false;
								errorReason = "Invalid Channel Class Name Found:" + chn + "=" + channelClass;
							} catch ( Exception ex ) {
								log.error( "Exception found when prepare File receiving:" + chn + "=" + channelClass, ex );
								opSucc = false;
								errorReason = "Exception found when prepare File receiving:" + ex.getMessage();
							}
						} else {
							opSucc = false;
							errorReason = "Empty incoming channel[" + chn + "] or channelClass[" + channelClass + "] for ftTask[" + ftTaskId + "]";
						}
					}
					
					m = pipe.createMessage();
					setProperty( m, Constants.MSG_HEAD_TARGET, from );
					setProperty( m, Constants.MSG_HEAD_FT_TASKID, ftTaskId );
					setProperty( m, Constants.MSG_HEAD_TASKID, taskId );
					setProperty( m, Constants.MSG_HEAD_TESTID, testId );
					if( opSucc ) {
						setProperty( m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_CONFIRM );
					}else{
						setProperty( m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_FAIL );
						setProperty( m, Constants.MSG_HEAD_ERROR, errorReason );
					}
					pipe.send( m );
					break;
				case Constants.MSG_HEAD_FT_CONFIRM: //SEND SIDE
					log.info( "Start transfer Files.FtTaskId:" + ftTaskId );
					future = pool.submit( new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return taskS.getCurrentChannel().send( ftTaskId, taskS.getFiles(), log );
						}
					} );
					
					Futures.addCallback(future, new FutureCallback<Boolean>() {
						public void onSuccess( Boolean result ) {
							if( result ) {
								log.info( "FileTransferTask[" + ftTaskId + "] have been sent successfully." );
								try {
									Message m = pipe.createMessage();
									setProperty( m, Constants.MSG_HEAD_TARGET, from );
									setProperty( m, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_START );
									setProperty( m, Constants.MSG_HEAD_FT_TASKID, ftTaskId );
									setProperty( m, Constants.MSG_HEAD_TASKID, taskId );
									setProperty( m, Constants.MSG_HEAD_TESTID, testId );
									pipe.send( m );
								}catch( Exception ex ) {
									log.error( "Notify Receive Side sending finished failed.", ex );
									retry( taskS );
								}
							}else {
								log.error( "FileTransferTask[" + ftTaskId + "] not sent succeed." );
								retry( taskS );
							}
						}
						
						public void onFailure( Throwable thrown ) {
							log.error( "FileTransferTask[" + ftTaskId + "] sent failed.", thrown );
							retry( taskS );
						}
					});
					break;
				case Constants.MSG_HEAD_FT_START: //RECEIVE SIDE
					log.info( "Got a Start message for FtTask[" + ftTaskId + "]" );
					if( taskI != null && taskI.containsKey( Constants.MSG_HEAD_FT_CHANNEL ) ) {
						final FileTransferChannel chl = (FileTransferChannel)taskI.get( Constants.MSG_HEAD_FT_CHANNEL );
						future = pool.submit( new Callable<Boolean>() {
							@SuppressWarnings( "unchecked" )
							@Override
							public Boolean call() throws Exception {
								return chl.receive( ftTaskId, (Map<String,Long>)taskI.get( Constants.MSG_HEAD_FT_ARTIFACTS ), (File)taskI.get( Constants.MSG_HEAD_FT_TARGETFOLDER ), log );
							}
						} );
						
						Futures.addCallback(future, new FutureCallback<Boolean>() {
							public void onSuccess( Boolean result ) {
								if( result ) {
									log.info( "FileTransferTask[" + ftTaskId + " have been received successfully." );
									try {
										Message message = pipe.createMessage();
										setProperty( message, Constants.MSG_HEAD_TARGET, from );
										setProperty( message, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_SUCC );
										setProperty( message, Constants.MSG_HEAD_FT_TASKID, ftTaskId );
										setProperty( message, Constants.MSG_HEAD_TASKID, taskId );
										setProperty( message, Constants.MSG_HEAD_TESTID, testId );
										pipe.send( message );
										incomingTasks.remove( ftTaskId );
										log.info( "Receiving FTTask[" + ftTaskId+"] removed from Task queue by reason: success." );
									}catch( Exception ex ) {
										log.error( "Send File transfer Succ back to SentSide met exception.ftTaskId=" + ftTaskId, ex );
										retry( taskI );
									}
								} else {
									log.error( "FileTransferTask[" + ftTaskId + "] not sent succeed." );
									retry( taskI );
								}
							}
							
							public void onFailure( Throwable thrown ) {
								log.error( "FileTransferTask[" + ftTaskId + "] receiving failed.", thrown );
								retry( taskI );
							}
						});
					} else {
						//retry( taskI );
						log.error( "Can't find related incomingTask, ftTaskId:" + ftTaskId );
					}
					break;
				case Constants.MSG_HEAD_FT_SUCC: //SEND SIDE
					log.info( "FTTask[" + ftTaskId +"] finished successfully!" );
					notifyFtFinish( taskS, true, null );
					sendTasks.remove( taskS );
					log.info( "Send FTTask[" + ftTaskId+"] removed from Task queue by reason: success." );
					break;
				case Constants.MSG_HEAD_FT_FAIL:
					errorReason = getProperty( msg, Constants.MSG_HEAD_ERROR, "" );
					log.info( "Got a Failure message for FtTask[" + ftTaskId + "] for reason:" + errorReason );
					if( taskS != null )
						retry( taskS );
					else if( taskI != null )
						retry( taskI );
					break;
				case Constants.MSG_HEAD_FT_TIMEOUT: //RECEIVE SIDE
					log.info( "Got a Timout message for FtTask[" + ftTaskId + "]" );
					if( taskS != null )
						retry( taskS );
					else if( taskI != null )
						retry( taskI );
					break;
				case Constants.MSG_HEAD_FT_CANCEL: // BOTH SIDE
					if( taskS != null )
						cancel( taskS );
					else
						cancel( ftTaskId );
					break;
			}
		} catch( Exception ex ) {
			log.error( "FileTransferService got a exception when handle incoming message.", ex );
		}
	}

	public void retry( FileTransferTask taskS ) { // SEND SIDE
		if( taskS != null ) {
			taskS.setRetry( taskS.getRetry() + 1 );
			log.info( "Begin to retry sending FtTask[" + taskS.getId()+"] again, it's the " + taskS.getRetry() + " times." );
			if( taskS.getRetry() < RETRY_TIMES ) {
				taskS.setCurrentChannel( null );
				taskS.setDescriptor( null );
				try {
					sendTo( taskS );
				} catch ( FileTransferException e ) {
					retry( taskS );
				}
			}else {
				notifyFtFinish( taskS, false, "File Transfer failure and exceed the maxium retry times, ftTask:" + taskS );
				sendTasks.remove( taskS );
				log.info( "Send FTTask[" + taskS.getId()+"] removed from Task queue by reason: exceed max retry times." );
			}
		} else {
			log.warn( "Got a Null FileTransferTask, can't retry" );
			notifyFtFinish( taskS, false, "File Transfer failure and can't retry, ftTask:" + taskS );
			//sendTasks.remove( taskS );
		}
	}
	
	public void retry( Map<String,Object> taskI ){ // RECEIVE SIDE
		try {
			Pipe pipe = hub.getPipe( Constants.HUB_FILE_TRANSFER );
			Message message = pipe.createMessage();
			setProperty( message, Constants.MSG_HEAD_TARGET, (String)taskI.get( Constants.MSG_HEAD_TARGET ) );
			setProperty( message, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_FAIL );
			setProperty( message, Constants.MSG_HEAD_FT_TASKID, taskI.get( Constants.MSG_HEAD_FT_TASKID ) );
			setProperty( message, Constants.MSG_HEAD_TASKID, taskI.get( Constants.MSG_HEAD_TASKID ) );
			setProperty( message, Constants.MSG_HEAD_TESTID, taskI.get( Constants.MSG_HEAD_TESTID ) );
			pipe.send( message );
		}catch( MessageException ex ) {
			log.error( "Retry Task[" + taskI.get( Constants.MSG_HEAD_FT_TASKID ) + "] from receiving side failed.", ex );
		}
	}
	
	public void cancel( FileTransferTask taskS ) { // SEND SIDE
		try {
			Pipe pipe = hub.getPipe( Constants.HUB_FILE_TRANSFER );
			log.info( "FtTask[" + taskS.getId() + "] will be cancelled." );
			Message message = pipe.createMessage();
			setProperty( message, Constants.MSG_HEAD_TARGET, taskS.getTargetUri() );
			setProperty( message, Constants.MSG_HEAD_FT_TRANSACTION, Constants.MSG_HEAD_FT_CANCEL );
			setProperty( message, Constants.MSG_HEAD_FT_TASKID, taskS.getId() );
			setProperty( message, Constants.MSG_HEAD_TASKID, taskS.getTaskId() );
			setProperty( message, Constants.MSG_HEAD_TESTID, taskS.getTestId() );
			pipe.send( message );
		}catch( MessageException ex ) {
			log.error( "Cancel Task[" + taskS.getId() + "] met exception.", ex );
		}
		sendTasks.remove( taskS );
		log.info( "FtTask[" + taskS.getId() + "] have been cancelled." );
	}
	
	public void cancel( String ftTaskId ) { // RECEIVE SIDE
		try {
			Map<String,Object> taskI = incomingTasks.remove( ftTaskId );
			if( taskI == null ) {
				log.warn( "FtTask[" + ftTaskId + "] not in current incoming FtTask queue, no need to cancel." );
				return;
			}
			log.info( "Going to cancel incoming FtTask[" + ftTaskId + "]." );
		}catch( Exception ex ) {
			log.error( "Cancel Task[" + ftTaskId + "] failed.", ex );
		}
	}
	
	public void notifyFtFinish( FileTransferTask taskS, boolean succ, String failureReason ) {
		try {
			Pipe pipe = hub.getBroker( Constants.BROKER_TASK ).getPipe( Constants.HUB_TASK_COMMUNICATION );
			Message m = pipe.createMessage();
			setProperty( m, Constants.MSG_HEAD_TASKID, taskS.getTaskId() );
			setProperty( m, Constants.MSG_HEAD_TESTID, taskS.getTestId() );
			if( hub.getHostType() != null && hub.getHostType().equals( Constants.MSG_TARGET_AGENT ) ) {
				setProperty( m, Constants.MSG_HEAD_TARGET, taskS.getTargetUri() );
				setProperty( m, Constants.MSG_HEAD_FROM, taskS.getFrom() );
				// RESULT SEND BACK TO CLIENT
				if( succ ) {
					setProperty( m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FINISHED );
				} else {
					setProperty( m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FAIL );
					setProperty( m, Constants.MSG_HEAD_ERROR, failureReason );
				}
			} else if( hub.getHostType() != null && hub.getHostType().equals( Constants.MSG_TARGET_CLIENT ) ) {
				setProperty( m, Constants.MSG_HEAD_TARGET, taskS.getFrom() );
				setProperty( m, Constants.MSG_HEAD_FROM, taskS.getTargetUri() );
				// ARTIFACTS SEND TO AGENT
				if( succ ) {
					setProperty( m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_READY );
				} else {
					setProperty( m, Constants.MSG_HEAD_TRANSACTION, Constants.MSG_TEST_FAIL );
					setProperty( m, Constants.MSG_HEAD_ERROR, failureReason );
				}
			}
			pipe.send( m );
		} catch( Exception ex ) {
			log.error( "Notify finished failed. ftTask=[" + taskS + "], result:" + succ + ",failureReason:" + failureReason );
		}
	}
	
	public File getTargetFolder( String ftTaskId ) {
		File target = null;
		Map<String,Object> rTask = this.getIncomingTask( ftTaskId );
		if( rTask != null ) {
			String testId = (String)rTask.get( Constants.MSG_HEAD_TESTID );
			if( hub.getHostType() != null && hub.getHostType().equals( Constants.MSG_TARGET_AGENT ) ) {
				target = new File( workspace, testId );
			} else if( hub.getHostType() != null && hub.getHostType().equals( Constants.MSG_TARGET_CLIENT ) ) {
				target = new File( workspace, "results/" + testId );
			} else {
				return null;
			}
			
			if( !target.exists() )
				target.mkdirs();
			else if( !target.isDirectory() ) {
				target.delete();
				target.mkdirs();
			}
		}
		return target;
	}
	
	public void dispose() {
		sendTasks.clear();
		incomingTasks.clear();
		timeoutCheckPool.shutdown();
		pool.shutdown();
		LogUtils.dispose( log );
	}
}
