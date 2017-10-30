package frank.incubator.testgrid.common;

import static frank.incubator.testgrid.common.CommonUtils.exec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import frank.incubator.testgrid.common.file.FileTransferChannel;
import frank.incubator.testgrid.common.file.FileTransferDescriptor;
import frank.incubator.testgrid.common.file.FtpFTChannel;
import frank.incubator.testgrid.common.file.NfsFTChannel;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.model.DeviceRequirement;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.TestSuite;

@SuppressWarnings({ "unused", "serial" })
public class CommonUtilsTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		// testPortFind();
		// testJson();
		// testPrintTime();
		// testGrep();
		// testSplitBlank();
		// System.out.println( CharSequence.class.isAssignableFrom( String.class
		// ) );
		// testRender2Html();
		// testReadConfig();
		// testProcessHandle();
		// testTimeoutExec();
		// testBlocking();
		// defaultCharset();
		// System.out.println(f);
		// f = CommonUtils.checkLatency("10.62.0.76");
		// System.out.println(f);
		// filterTest();
		//testHttps();
		testDrEqual();
	}
	
	public static void testDrEqual() {
		DeviceRequirement dr = new DeviceRequirement();
		dr.setMain(Device.createRequirement(new HashMap<String,Object>(){{put("aaa","bbbb");}}));
		DeviceRequirement dr2 = new DeviceRequirement();
		dr2.setMain(Device.createRequirement(new HashMap<String,Object>(){{put("aaa","bbbb");}}));
		System.out.println(dr.equals(dr2));
	}
	
	public static void testHttps() {
		CommonUtils.httpPost("", getDefaultCharEncoding(), getDefaultCharEncoding(), null);
	}

	public static void filterTest() {
		String resultFile = "adbhelper.py";
		FileFilter fileFilter = new WildcardFileFilter(resultFile);
		File parent = new File("D:/develop/workspaces/eclipse_workspace/testgrid/testgrid-client/client_workspace");
		for (File f : parent.listFiles(fileFilter)) {
			System.out.println(f.getName());
		}
	}

	public static String getDefaultCharEncoding() {
		byte[] bArray = { 'w' };
		InputStream is = new ByteArrayInputStream(bArray);
		InputStreamReader reader = new InputStreamReader(is);
		String defaultCharacterEncoding = reader.getEncoding();
		return defaultCharacterEncoding;
	}

	public static void defaultCharset() {
		String defaultCharacterEncoding = System.getProperty("file.encoding");
		System.out.println("defaultCharacterEncoding by property: " + defaultCharacterEncoding);
		System.out.println("defaultCharacterEncoding by code: " + getDefaultCharEncoding());
		System.out.println("defaultCharacterEncoding by charSet: " + Charset.defaultCharset());

		System.setProperty("file.encoding", "UTF-16");

		System.out.println("defaultCharacterEncoding by property after updating file.encoding : " + System.getProperty("file.encoding"));

		System.out.println("defaultCharacterEncoding by code after updating file.encoding : " + getDefaultCharEncoding());

		System.out.println("defaultCharacterEncoding by java.nio.Charset after updating file.encoding : " + Charset.defaultCharset());
	}

	public static void testBlocking() throws IOException {
		System.out.println(System.getProperty("file.encoding"));
		StringBuilder sb = new StringBuilder();
		Map<String, String> env = new HashMap<String, String>();
		env.put("DEVICE_MAIN_SN", "0224111208e4c60e");
		env.put("ADB_HOME", "D:\\Android\\android-sdk\\platform-tools");
		// %ADB_HOME%/adb -s %DEVICE_MAIN_SN% uninstall
		CommonUtils.execBlocking("cmd.exe /c echo %ADB_HOME%", env, sb, 30000);
		System.out.println("execBlocking:" + sb.toString());
		try {
			System.out.println("exec:" + CommonUtils.exec("echo %ADB_HOME%", null));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("executeCmd：" + CommonUtils.executeCmd("cmd.exe /c echo %ADB_HOME%"));
		final ByteArrayOutputStream bao = new ByteArrayOutputStream();
		CommonUtils.execASync("cmd /C echo %ADB_HOME%", env, bao, new ExecuteResultHandler() {
			@Override
			public void onProcessComplete(int exitValue) {
				try {
					System.out.println("execASync:" + bao.toString(Charset.defaultCharset().name()));
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onProcessFailed(ExecuteException e) {
				try {
					System.err.println("execASync:" + bao.toString(Charset.defaultCharset().name()) + ", exception:" + CommonUtils.getErrorStack(e));
				} catch (UnsupportedEncodingException e1) {
					e1.printStackTrace();
				}
			}
		}, 3000);
	}

	public static void testTimeoutExec() {
		long start = System.currentTimeMillis();
		System.out.println("Start:" + start);
		String output = CommonUtils.exec("cd & sleep 1", null, 3000, LogUtils.getLogger("test"));
		System.out.println(output);
		long end = System.currentTimeMillis();
		System.out.println("End:" + end);
		System.out.println("Duration:" + TimeUnit.SECONDS.convert((end - start), TimeUnit.MILLISECONDS));
	}

	private static void testProcessHandle() {
		Map<Integer, String> processes = CommonUtils.getAllProcess();
		for (int pid : processes.keySet()) {
			System.out.println(pid + ":" + processes.get(pid));
		}

		String kw = "";
		String cmd = "";
		for (int pid : processes.keySet()) {
			cmd = processes.get(pid);
			if (cmd.contains("MySQL")) {
				CommonUtils.killProcess(pid);
			}
		}

	}

	private static void testReadConfig() {
		String s = CommonUtils.loadResourcesAsString("agent.config", CommonUtils.RESOURCE_LOAD_MODE_BOTH, false);
		System.out.println(s);
		System.out.println("=====================");

		s = CommonUtils.loadResourcesAsString("agent.config", CommonUtils.RESOURCE_LOAD_MODE_BOTH, true);
		System.out.println(s);
		System.out.println("=====================");

		Properties prop = new Properties();
		try {
			prop.load(new StringReader(s));
			for (Object key : prop.keySet()) {
				System.out.println(key + ":" + prop.get(key));
			}
			System.out.println("=====================");
			prop = new Properties();
			prop.load(CommonUtils.loadResources("agent.config", false));
			for (Object key : prop.keySet()) {
				System.out.println(key + ":" + prop.get(key));
			}
			System.out.println("=====================");
			prop.load(CommonUtils.loadResources("agent.config", true));
			for (Object key : prop.keySet()) {
				System.out.println(key + ":" + prop.get(key));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void testRender2Html() {
		Task task = new Task();
		TestSuite ts = new TestSuite();
		Test test = new Test();
		test.setArtifacts(new HashMap<String, Long>() {
			{
				this.put("aaa", 100010l);
			}
		});
		ts.addTest(test);
		task.setTestsuite(ts);
		System.out.println(CommonUtils.renderToHtml(task));
	}

	private static void testSplitBlank() {
		String sam = "   79  1   0% S     1   \n \r  0K \t     0K     root     irq/286-msm_iom ";
		Collection<String> arr = CommonUtils.splitBlanks(sam);
		for (String a : arr) {
			System.out.println(a);
		}
	}

	private static void testJson() {
		/*
		 * Task task = new Task(); TestSuite ts = new TestSuite(); Test test =
		 * new Test(); test.setArtifacts( new
		 * HashMap<String,Long>(){{this.put("aaa",100010l);}} ); ts.addTest(
		 * test ); task.setTestsuite( ts ); String tStr = task.toString();
		 * System.out.println( tStr ); System.out.println(CommonUtils.fromJson(
		 * tStr, Task.class ));
		 */
		FileTransferDescriptor descriptor = new FileTransferDescriptor();
		FileTransferChannel channel = new NfsFTChannel("c:/temp/sender/", "c:/temp/receiver/");
		descriptor.addChannel(channel);
		channel = new FtpFTChannel("10.220.120.16", "ftp", "ftp", 21);
		descriptor.addChannel(channel);
		System.out.println(descriptor.toString());
		descriptor = CommonUtils.fromJson(descriptor.toString(), FileTransferDescriptor.class);
		System.out.println(descriptor.toString());
		String p = "{'channels':[{'priority':3,'properties':{'port':21,'pwd':'ftp','host':'10.220.120.16','user':'ftp'},'id':'FTP'}]}";
		descriptor = CommonUtils.fromJson(p, FileTransferDescriptor.class);
		System.out.println(descriptor.toString());
	}

	private static void testPrintTime() {
		System.out.println(CommonUtils.getTime());
	}

	private static void testPortFind() {
		int availablePort = CommonUtils.availablePort(625);
		System.out.println(availablePort);
	}

	private static void testGrep() throws Exception {
		String imei = CommonUtils.grep(exec("%ADB_HOME%/adb" + " -s " + "1acc4f6c" + " shell dumpsys iphonesubinfo", null), "Device ID", true);
		System.out.println(imei);
		imei = CommonUtils.grep(exec("%ADB_HOME%/adb" + " -s " + "1acc4f6c" + " shell dumpsys iphonesubinfo", null), "Device ID", false);
		System.out.println(imei);
		imei = imei.split("=")[1].trim();
		System.out.println(imei);
	}

}
