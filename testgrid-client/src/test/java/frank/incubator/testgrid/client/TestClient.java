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
		CommonUtils.DISABLE_HOSTNAME = true;
		Task task = new Task();
		//Task task2 = new Task();
		task.setTaskOwner( "Chen Evan" );
		//task2.setTaskOwner( "Jacky Zhou" );
		task.setTestsuite( createTestSuite() );
		//task2.setTestsuite( createTestSuite() );
		//task.setRequirements( getRequirement() );
		//task2.setRequirements( getRequirement2() );
		InputStream in = null;
		BrokerDescriptor[] bds = null;
		try {
			in = CommonUtils.loadResources( "../testgrid-client/" + Constants.MQ_CONFIG_FILE, true );
			StringWriter sw = new StringWriter();
			IOUtils.copy( in, sw );
			bds = CommonUtils.fromJson( sw.toString(), new TypeToken<BrokerDescriptor[]>(){}.getType() );
			bds = null;//,{'priority':1,'id':'FTP','properties':{'user':'ftpuser','pwd':'ali88','host':'slm.alipay.net'}}
			FileTransferDescriptor descriptor = CommonUtils.fromJson( "{'channels':[{'priority':0,'id':'SOCKET'}]}", FileTransferDescriptor.class );
			task.setSendOutput(true);
			//TaskClient client = new TaskClient( null, task, TestStrategy.EXEC_ASAP, Paths.get( "client_workspace" ).toFile(), System.out, descriptor, false , bds );
			TaskClient client = new TaskClient( "tcp://localhost:61616", task, TestStrategy.EXEC_ASAP, Paths.get( "client_workspace" ).toFile(), System.out, descriptor, true, false, bds );
			//client.setTaskTimeout(30000);
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
				this.put( Constants.DEVICE_SN, "0224111208e4c60e" );
				//this.put( "exclude.owner", "frank" );
				//this.put( "simcount", "2" );
			}
		} ) );
		return dr;
	}
	
	private static DeviceRequirement getRequirement2() {
		DeviceRequirement dr = new DeviceRequirement();
		dr.setMain( Device.createRequirement( new HashMap<String, Object>()) );//{{put("manufacturer","LGE%OR%HUAWEI");}}
		return dr;
	}
	
	private static DeviceRequirement getRequirementIos() {
		DeviceRequirement dr = new DeviceRequirement();
		dr.setMain( Device.createRequirement( new HashMap<String, Object>(){{this.put("exclude.platform", "ios");}} ) );
		return dr;
	}
	
	private static DeviceRequirement getRequirementAndroidMonkey() {
		DeviceRequirement dr = new DeviceRequirement();
		dr.setMain( Device.createRequirement( new HashMap<String, Object>(){{this.put(Constants.DEVICE_SN, "0224111208e4c60e");}} ) );
		return dr;
	}

	private static TestSuite createTestSuite() {
		TestSuite ts = new TestSuite();
		//ts.addTest( createTest1() );
		//ts.addTest( createTest2() );
		ts.addTest( createTest3() );
		ts.addTest( createTest3() );
		ts.addTest( createTest3() );
		//ts.addTest( createTestIos() );
		//ts.addTest(createTestAndroidMonkey());
		return ts;
	}
	
	private static Test createTestAndroidMonkey() {
		Test test = new Test();
		test.setDisconnectionTimeout( Constants.ONE_HOUR * 72 );
		test.setExecutorApplication( "" );
		test.setExecutorScript( "monkeyAndroid.sh" );
		test.addExecutorEnvParam("PKG_UT", "client.apk");
		test.addExecutorEnvParam("TIME", "180");
		test.setExecutorParameters( "" );
		File sh = new File("client_workspace","monkeyAndroid.sh");
		test.addArtifact(sh.getName(), sh.length());
		File gry = new File("client_workspace","monitor.groovy");
		test.addArtifact(gry.getName(), gry.length());
		File apk = new File("client_workspace","client.apk");
		test.addArtifact(apk.getName(), apk.length());
		test.setRequirements(getRequirementAndroidMonkey());
		return test;
	}

	private static Test createTestIos() {
		Test test = new Test();
		test.setDisconnectionTimeout( Constants.ONE_HOUR * 72 );
		test.setExecutorApplication( "" );
		test.setExecutorScript( "monkey.sh" );
		test.addExecutorEnvParam("PKG_UT", "alipay.ipa");
		test.addExecutorEnvParam("TIME", "5");
		test.setExecutorParameters( "" );
		File sh = new File("client_workspace","monkey.sh");
		test.addArtifact(sh.getName(), sh.length());
		test.setRequirements(getRequirementIos());
		return test;
	}

	private static Test createTest1() {
		Test test = new Test();
		test.setDisconnectionTimeout( Constants.ONE_HOUR );
		test.setExecutorApplication( "" );
		test.setExecutorScript( "execute.bat" );
		test.setExecutorEnvparams( null );
		test.setExecutorParameters( "" );
		test.addResultFile("out.log", true).addResultFile("a.log", false);
		test.setArtifacts( createArtifacts() );
		return test;
	}
	
	private static Test createTest2() {
		Test test = new Test();
		test.setDisconnectionTimeout( Constants.ONE_HOUR );
		test.setExecutorApplication( "c:/Python27/python.exe" );
		test.setExecutorScript( "execute.py" );
		test.setExecutorEnvparams( new HashMap<String,String>(){{put("APK_NAME","client.apk");}} );
		test.setExecutorParameters( "-s ADB_HOME -d DEVICE_MAIN_SN -k APK_NAME -v" );
		test.addResultFile("robotium_log.txt", true).addResultFile("automation.mp4", false);
		test.setArtifacts( createArtifacts() );
		test.setRequirements(getRequirement());
		return test;
	}
	
	private static Test createTest3() {
		Test test = new Test();
		test.setPreScript("cmd /c cd");
		test.setPostScript("cmd /c %ADB_HOME%/adb -s %DEVICE_MAIN_SN% uninstall com.eg.android.AlipayGphone");
		test.setDisconnectionTimeout( Constants.ONE_HOUR );
		test.setExecutorApplication( "python" );
		test.setExecutorScript( "execute.py" );
		test.setExecutorEnvparams( new HashMap<String,String>(){{put("APK_NAME","client.apk");}} );
		test.setExecutorParameters( "-s ADB_HOME -d DEVICE_MAIN_SN -k APK_NAME -v" );
		test.addResultFile("robotium_log.txt", true).addResultFile("automation.mp4", false).addResultFile("junit-report.xml", false);
		test.setArtifacts( createArtifacts() );
		test.setRequirements(getRequirement2());
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
