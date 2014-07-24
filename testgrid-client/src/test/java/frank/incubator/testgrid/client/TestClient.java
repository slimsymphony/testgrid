package frank.incubator.testgrid.client;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import com.google.gson.reflect.TypeToken;
import frank.incubator.testgrid.client.TaskClient;
import frank.incubator.testgrid.client.TestStrategy;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.file.FileTransferDescriptor;
import frank.incubator.testgrid.common.message.BrokerDescriptor;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.DeviceRequirement;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.TestSuite;

public class TestClient {

	/**
	 * @param args
	 */
	public static void main( String[] args ) {
		Task task = new Task();
		//Task task2 = new Task();
		task.setTaskOwner( "Chen Evan" );
		//task2.setTaskOwner( "Jacky Zhou" );
		task.setTestsuite( createTestSuite() );
		//task2.setTestsuite( createTestSuite() );
		//task.setRequirements( getRequirement() );
		task.setRequirements( getRequirement() );
		//task2.setRequirements( getRequirement2() );
		InputStream in = null;
		BrokerDescriptor[] bds = null;
		try {
			in = CommonUtils.loadResources( "../testgrid-agent/" + Constants.MQ_CONFIG_FILE, true );
			StringWriter sw = new StringWriter();
			IOUtils.copy( in, sw );
			bds = CommonUtils.fromJson( sw.toString(), new TypeToken<BrokerDescriptor[]>(){}.getType() );
			FileTransferDescriptor descriptor = CommonUtils.fromJson( "{'channels':[{'priority':0,'id':'SOCKET'},{'priority':3,'properties':{'port':21,'pwd':'ftp','host':'10.220.120.16','user':'ftp'},'id':'FTP'}]}", FileTransferDescriptor.class );
			//TaskClient client = new TaskClient( null, task, TestStrategy.EXEC_ASAP, Paths.get( "client_workspace" ).toFile(), System.out, descriptor, false , bds );
			TaskClient client = new TaskClient( "tcp://localhost:61617", task, TestStrategy.EXEC_ASAP, Paths.get( "client_workspace" ).toFile(), System.out, descriptor, false , bds );
			client.begin();
			while( !client.isFinished() ) {
				System.out.println("Still waiting for task finished.");
				try {
					TimeUnit.SECONDS.sleep( 30 );
				} catch ( InterruptedException e ) {
					e.printStackTrace();
				}
			}
		}catch( Exception ex ) {
			ex.printStackTrace();
		}finally {
			CommonUtils.closeQuietly( in );
		}
	}

	@SuppressWarnings( "serial" )
	private static DeviceRequirement getRequirement() {
		DeviceRequirement dr = new DeviceRequirement();
		dr.setMain( Device.createRequirement( new HashMap<String, Object>() {
			{
				//this.put( Constants.DEVICE_SN, "c2a4313" );
				//this.put( "exclude.owner", "frank" );
				//this.put( "simcount", "2" );
			}
		} ) );
		return dr;
	}
	
	@SuppressWarnings( "serial" )
	private static DeviceRequirement getRequirement2() {
		DeviceRequirement dr = new DeviceRequirement();
		dr.setMain( Device.createRequirement( new HashMap<String, Object>() ) );
		return dr;
	}

	private static TestSuite createTestSuite() {
		TestSuite ts = new TestSuite();
		ts.addTest( createTest1() );
		//ts.addTest( createTest2() );
		//ts.addTest( createTest3() );
		return ts;
	}

	private static Test createTest1() {
		Test test = new Test();
		test.setDisconnectionTimeout( Constants.ONE_HOUR );
		test.setExecutorApplication( "" );
		test.setExecutorScript( "execute.bat" );
		test.setExecutorEnvparams( null );
		test.setExecutorParameters( "" );
		test.setResultsFilename( "out.log" );
		test.setArtifacts( createArtifacts() );
		return test;
	}
	
	private static Test createTest2() {
		Test test = new Test();
		test.setDisconnectionTimeout( Constants.ONE_HOUR );
		test.setExecutorApplication( "sh" );
		test.setExecutorScript( "execute.sh" );
		test.setExecutorEnvparams( null );
		test.setExecutorParameters( "" );
		test.setResultsFilename( "out.log" );
		test.setArtifacts( createArtifacts() );
		return test;
	}
	
	private static Test createTest3() {
		Test test = new Test();
		test.setDisconnectionTimeout( Constants.ONE_HOUR );
		test.setExecutorApplication( "" );
		test.setExecutorScript( "execute.bat" );
		test.setExecutorEnvparams( new HashMap<String,String>(){{this.put("JACKY","1");}} );
		test.setExecutorParameters( "" );
		test.setResultsFilename( "out.log" );
		test.setArtifacts( createArtifacts() );
		return test;
	}

	private static Map<String, Long> createArtifacts() {
		final Map<String, Long> ats = new HashMap<String, Long>();
		File file = new File("client_workspace");
		for( File f : file.listFiles() ) {
			if( f.isFile() ) {
				ats.put( f.getName(), f.length() );
			}
		}
		/*Path from = Paths.get( "client_workspace" );
		try {
			Files.walkFileTree( from, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException {
					ats.put( file.toFile().getName(), file.toFile().length() );
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs ) throws IOException {
					return FileVisitResult.SKIP_SUBTREE;
				}

				@Override
				public FileVisitResult postVisitDirectory( Path dir, IOException e ) throws IOException {
					if ( e == null ) {
						// return FileVisitResult.CONTINUE;
						return FileVisitResult.SKIP_SUBTREE;
					} else {
						// directory iteration failed
						throw e;
					}
				}
			} );
		} catch ( IOException e ) {
			e.printStackTrace();
		}*/
		return ats;
	}

}
