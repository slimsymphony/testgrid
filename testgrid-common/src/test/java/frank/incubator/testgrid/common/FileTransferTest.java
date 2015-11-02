package frank.incubator.testgrid.common;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import frank.incubator.testgrid.common.file.FTPFileTransferSource;
import frank.incubator.testgrid.common.file.FTPFileTransferTarget;
import frank.incubator.testgrid.common.file.FileTransferMode;
import frank.incubator.testgrid.common.file.FileTransferSource;
import frank.incubator.testgrid.common.file.FileTransferTarget;
import frank.incubator.testgrid.common.file.LocalFileTransferSource;
import frank.incubator.testgrid.common.file.LocalFileTransferTarget;
import frank.incubator.testgrid.common.file.DirectSocketTransferSource;
import frank.incubator.testgrid.common.file.DirectSocketTransferTarget;

public class FileTransferTest {

	FileTransferSource source;

	FileTransferTarget target;

	static File folder = new File("c:/temp/receiver");

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		FileTransferTest t = new FileTransferTest();
		FileUtils.deleteDirectory(folder);
		folder.mkdirs();
		// t.testPullMode();
		// t.delete( folder );
		t.testPushMode();

		// t.testFtp();
		// t.testLocalCopy();
	}

	private void testLocalCopy() throws Exception {
		File sourceLocalBase = new File("c:/temp");
		String token = "test";
		File targetLocalBase = new File("C:/develop/eclipse-workspaces/java8_workspace/testgrid/testgrid-agent/workspace/");
		source = new LocalFileTransferSource(FileTransferMode.SOURCE_HOST, sourceLocalBase, null, System.out);
		target = new LocalFileTransferTarget(FileTransferMode.SOURCE_HOST, targetLocalBase, sourceLocalBase, System.out);
		List<File> files = new ArrayList<File>();
		Map<String, Long> filenames = new HashMap<String, Long>();
		File root = new File(sourceLocalBase, token);
		for (File f : root.listFiles()) {
			files.add(f);
			filenames.put(f.getName(), f.length());
		}
		source.publish("test", files);
		System.out.println("source publish finished");
		File dest = new File(targetLocalBase, token);
		target.fetch(token, filenames, dest);

	}

	private void testFtp() throws Exception {
		source = new FTPFileTransferSource("10.220.120.16", 21, "ftp", "ftp", System.out);
		target = new FTPFileTransferTarget("10.220.120.16", 21, "ftp", "ftp", System.out);
		String token = CommonUtils.generateToken(12);
		source.push(token, filelist());
		target.fetch(token, fileNames(), folder);
		target.dispose();
		System.out.println("FTP Test Finished");
	}

	private Map<String, Long> fileNames() {
		Map<String, Long> filenames = new HashMap<String, Long>();
		filenames.put("apache-activemq-5.9.0-bin.zip", 61877545L);
		filenames.put("mRemoteNG-1.72.zip", 4053962L);
		filenames.put("ezjad.dat", 8L);
		filenames.put("install.res.1040.dll", 95248L);
		return filenames;
	}

	private void delete(File f) {
		if (f.exists()) {
			if (f.isDirectory()) {
				for (File file : f.listFiles()) {
					delete(file);
				}
			} else {
				f.delete();
			}
		}
	}

	private List<File> filelist() {
		List<File> files = new ArrayList<File>();
		files.add(new File("C:/develop/mRemoteNG-1.72.zip"));
		files.add(new File("c:/ezjad.dat"));
		files.add(new File("c:/install.res.1040.dll"));
		files.add(new File("c:/develop/apache-activemq-5.9.0-bin.zip"));
		return files;
	}

	protected void testPushMode() throws Exception {
		target = new DirectSocketTransferTarget(null, CommonUtils.availablePort(5451), System.out);
		source = new DirectSocketTransferSource("localhost", ((DirectSocketTransferTarget) target).getPort(), System.out);

		final String token = CommonUtils.generateToken(15);
		Thread t = new Thread("accept") {
			@Override
			public void run() {
				try {
					target.accept(token, fileNames(), folder);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		t.start();
		Thread.sleep(2000);
		source.push(token, filelist());
		System.out.println("Push Test Finished");
	}

	protected void testPullMode() throws Exception {
		source = new DirectSocketTransferSource(null, CommonUtils.availablePort(5451), System.out);
		target = new DirectSocketTransferTarget("localhost", ((DirectSocketTransferSource) source).getPort(), System.out);
		source.publish("1a2s3d4f5g", filelist());
		target.fetch("1a2s3d4f5g", fileNames(), folder);
		System.out.println("Pull Test Finished");
	}

}
