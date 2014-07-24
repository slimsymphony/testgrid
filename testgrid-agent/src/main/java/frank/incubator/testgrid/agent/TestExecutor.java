package frank.incubator.testgrid.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.Test.Phase;

/**
 * Test executor, actual test activity executor which control a process to
 * execute the assigned test activity.
 * 
 * @author Wang Frank
 * 
 */
public class TestExecutor implements Runnable {

	public TestExecutor( Test test, AgentNode agent, Collection<Device> devices, OutputStream tracker, long timeout,
			String clientTarget ) {
		this.test = test;
		//test.setStartTime( System.currentTimeMillis() );
		this.agent = agent;
		this.devices = devices;
		if( agent.getBackupWorkspace().containsKey( test.getId() ) ) {
			this.workspace = new File( agent.getWorkspace(), agent.getBackupWorkspace().remove( test.getId() ) );
		} else {
			this.workspace = new File( agent.getWorkspace(), test.getId() );
		}
		
		if ( tracker == null )
			tracker = System.out;
		this.tracker = tracker;
		this.timeout = timeout;
		this.clientTarget = clientTarget;
	}
	
	private OutputStream tracker;
	private String clientTarget;
	private Test test;
	private File workspace;
	private AgentNode agent;
	private Collection<File> artifacts = new ArrayList<File>();
	private Collection<Device> devices;
	private long timeout = Constants.DEFAULT_TEST_TIMEOUT;
	private String failureReason = null;
	private ExecuteWatchdog watchdog;

	private boolean running = true;

	public Test getTest() {
		return test;
	}

	public void setTest( Test test ) {
		this.test = test;
	}

	public Collection<File> getArtifacts() {
		return artifacts;
	}

	public void setArtifacts( Collection<File> artifacts ) {
		this.artifacts = artifacts;
	}

	public AgentNode getAgent() {
		return agent;
	}

	public void setAgent( AgentNode agent ) {
		this.agent = agent;
	}

	public Collection<Device> getDevices() {
		return devices;
	}

	public void setDevices( Collection<Device> devices ) {
		this.devices = devices;
	}

	public File getWorkspace() {
		return workspace;
	}

	public void setWorkspace( File workspace ) {
		this.workspace = workspace;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout( long timeout ) {
		this.timeout = timeout;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning( boolean running ) {
		this.running = running;
	}

	public String getClientTarget() {
		return clientTarget;
	}

	public void setClientTarget( String clientTarget ) {
		this.clientTarget = clientTarget;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void setFailureReason( String failureReason ) {
		this.failureReason = failureReason;
	}

	public OutputStream getTracker() {
		return tracker;
	}

	public ExecuteWatchdog getWatchDog() {
		return watchdog;
	}
	
	private void p( String str ) {
		try {
			tracker.write( str.getBytes( "UTF-8" ) );
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}
	
	private void p( String str, Throwable t ) {
		try {
			tracker.write( (str + "\n" ).getBytes( "UTF-8" ) );
			tracker.write( CommonUtils.getErrorStack( t ).getBytes() );
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Stop the running test.
	 * 
	 * @param reason
	 */
	public void stop( String reason ) {
		p( "Task is to be stop externally because of:" + reason );
		failureReason = reason;
		notifyFinish( Phase.FAILED );
		if( watchdog != null ) {
			try {
				watchdog.destroyProcess();
			}catch( Exception ex ) {
				p( "Stop process met exception.", ex );
			}
		}
	}

	@Override
	public void run() {
		try {
			TimeUnit.SECONDS.sleep( 5 );
		} catch ( InterruptedException e1 ) {
			p( "Wait before started failed.", e1 );
		}
		p( "Test [" + test.getId() + "] begin ..." );

		p( "Verify Artifacts" );
		boolean verified = verifyArtifacts();
		if ( !verified ) {
			p( "Verify failed. stop current test execution." );
			stop( "Artifacts received not consistence with needed." );
			return;
		}
		p( "Verify finished" );
		
		p( "Create device config file" );
		File deviceConfigFile = new File( workspace, Constants.DEVICE_CONFIG_FILE );
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream( deviceConfigFile );
			fos.write( CommonUtils.toJson( devices ).getBytes() );
			fos.flush();
		} catch ( Exception ex ) {
			p( "Create Device Config File faild.", ex );
		} finally {
			CommonUtils.closeQuietly( fos );
		}

		p( "Generate bootstrap app/script" );
		String app = test.getExecutorApplication();
		String script = test.getExecutorScript();
		Map<String, String> envs = test.getExecutorEnvparams();
		if ( envs == null )
			envs = new HashMap<String, String>();
		for ( Device d : devices ) {
			if ( d.getRole() == Device.ROLE_MAIN )
				envs.put( Constants.ENV_DEVICE_MAIN_SN, ( String ) d.getAttribte( Constants.DEVICE_SN ) );
			else if ( d.getRole() == Device.ROLE_REF )
				envs.put( Constants.ENV_DEVICE_REF_SN, ( String ) d.getAttribte( Constants.DEVICE_SN ) );
		}

		List<String> appParams = test.getExecutorParameters();
		StringBuffer executionList = new StringBuffer();
		if ( app != null && !app.trim().isEmpty() ) {
			// application available. If script also available, call the app to
			// execute the script; if not, call application directly.
			if ( script != null && !script.trim().isEmpty() ) {
				executionList.append( app ).append( " " ).append( script ).append( " " );
			} else {
				executionList.append( app ).append( " " );
			}
		} else {
			// application not available, call script with shell, depends on OS
			// platform.
			if ( script != null && !script.trim().isEmpty() ) {
				executionList.append( script ).append( " " );
			} else {
				stop( "Didn't provide valid executor application&script, stop. test:" + test );
				return;
			}
		}

		if ( appParams != null && !appParams.isEmpty() ) {
			for ( String p : appParams ) {
				executionList.append( p ).append( " " );
			}
		}

		p( "Executing Process" );
		try {
			if ( !CommonUtils.isWindows() ) {
				p( "Linux system should set app/script executable first" );
				// set script or app executable.
				if ( app != null ) {
					File appFile = new File( workspace, app );
					if ( appFile.exists() ) {
						p( "Set app executable start." );
						String executable = "chmod +x " + appFile.getAbsolutePath();
						String output = CommonUtils.exec( executable, null );
						p( "executable:" + executable + ", result:" + output );
					}
				}

				if ( script != null ) {
					File scriptFile = new File( workspace, script );
					if ( scriptFile.exists() ) {
						p( "Set script executable start." );
						String executable = "chmod +x " + scriptFile.getAbsolutePath();
						String output = CommonUtils.exec( executable, null );
						p( "executable:" + executable + ", result:" + output );
					}
				}
			}

			String finalExec = executionList.toString();
			if ( CommonUtils.isWindows() ) {
				if( app == null || app.trim().isEmpty() || new File( workspace, app ).exists() )
					finalExec = workspace.getAbsolutePath() + "/" + finalExec;
				else if( app != null && !app.trim().isEmpty() && new File( workspace, app ).exists() ) {
					finalExec =  "cmd /c " + finalExec;
				}
			}
			p( "begin formal execution: " + finalExec );
			watchdog = CommonUtils.execASync( finalExec, new File( workspace.getAbsolutePath() ), envs, tracker,
					new ExecuteResultHandler() {

						@Override
						public void onProcessComplete( int exitValue ) {
							p( "Process executing finished successfully. The exit value is " + exitValue );
							notifyFinish( Phase.FINISHED );
							running = false;
						}

						@Override
						public void onProcessFailed( ExecuteException e ) {
							p( "Process executing failed.", e );
							failureReason = e.getMessage() + "\n Stack:" + CommonUtils.getErrorStack( e );
							notifyFinish( Phase.FAILED );
							running = false;
						}
					}, timeout );

		} catch ( Exception e ) {
			p( "Test execution exception.", e );
		}
		while ( running ) {
			try {
				TimeUnit.SECONDS.sleep( 5 );
			} catch ( InterruptedException e ) {
				p( "Got exception while Waiting for test process fininshed.", e );
				failureReason = "Test["+ test.getId() + "] execution been interrupted. " + e.getMessage() + "\n Stack:" + CommonUtils.getErrorStack( e );
				notifyFinish( Phase.FAILED );
				running = false;
			}
		}
		watchdog = null;
		p( "Test [" + test.getId() + "] finished ..." );

	}

	/**
	 * When some test finished or failed. this method will be called to notify
	 * the agent that current test finished.
	 * 
	 * @param phase
	 *            The actual test phase . Could be Finished or Failed.
	 */
	public void notifyFinish( Phase phase ) {
		this.test.setPhase( phase );
		File resultFile = new File( workspace, test.getResultsFilename() );
		if ( resultFile.exists() ) {
			agent.finishTest( this );
		} else {
			p( "Cannot find the result File:" + resultFile.getAbsolutePath() + ", change result to "
					+ Phase.FAILED.toString() );
			this.test.setPhase( Phase.FAILED );
			this.setFailureReason( "Cannot find the result File:" + resultFile.getAbsolutePath() );
			agent.finishTest( this );
		}
		CommonUtils.closeQuietly( tracker );
	}

	/**
	 * Verify the artifacts was OK before test start.
	 * 
	 * @return
	 */
	private boolean verifyArtifacts() {
		for ( String fn : test.getArtifacts().keySet() ) {
			boolean r = false;
			long fl = test.getArtifacts().get( fn );
			for ( File file : this.workspace.listFiles() ) {
				if ( file.getName().equals( fn ) && file.length() == fl ) {
					artifacts.add( file );
					r = true;
					break;
				}
			}
			if ( !r ) {
				File f = new File(workspace, fn );
				long len = 0L;
				if( f.exists() )
					len = f.length();
				p( "Artifact[" + fn + "] verify failed. expect length:"+fl +", actual, exist:" + f.exists() +",length :" + len );
				return false;
			}
		}
		return true;
	}

	/**
	 * Get the test failed info. NULL is the test didn't failed.
	 * 
	 * @return
	 */
	public String getFailureInfo() {
		return failureReason;
	}
}
