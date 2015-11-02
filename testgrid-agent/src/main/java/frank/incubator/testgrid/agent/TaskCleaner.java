package frank.incubator.testgrid.agent;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogUtils;

public class TaskCleaner extends Thread {
	private static final Logger log = LogUtils.getLog("cleaner");

	public TaskCleaner(File workspaceDir, long keepDays) {
		super("TaskCleaner");
		this.setDaemon(true);
		this.workspaceDir = workspaceDir;
		this.preserve_interval = keepDays * Constants.ONE_DAY;
	}

	private File workspaceDir;
	private long preserve_interval;
	private long preserve_interval_sharefiles = Constants.ONE_HOUR * 2;

	@Override
	public void run() {
		int deleteFile = 0, deleteFolder = 0;
		while (true) {
			deleteFile = 0;
			deleteFolder = 0;
			long current = System.currentTimeMillis();
			try {
				// 1. clean workspace & runtime log file
				for (File f : workspaceDir.listFiles()) {
					if (f.isDirectory() && f.getName().startsWith("Test_")) {
						if ((current - f.lastModified()) > preserve_interval) {
							try {
								if (f.isFile()) {
									FileUtils.deleteQuietly(f);
									deleteFile++;
								} else if(f.isDirectory())  {
									FileUtils.deleteDirectory(f);
									deleteFolder++;
								}
							} catch (Throwable t) {
								log.error("Delete " + f.getAbsolutePath() + " failed.", t);
							}
						}
					}
					if(f.isDirectory() && f.getName().equals("shareZone")) {
						for(File sf : f.listFiles()) {
							if ((current - sf.lastModified()) > preserve_interval_sharefiles) {
								try {
									if (sf.isFile()) {
										FileUtils.deleteQuietly(sf);
										deleteFile++;
									} else if(sf.isDirectory()) {
										FileUtils.deleteDirectory(sf);
										deleteFolder++;
									}
								} catch (Throwable t) {
									log.error("Delete share data " + sf.getAbsolutePath() + " failed.", t);
								}
							}
						}
					}
				}
				log.info("Schedule clean finished, clean Folder:{}, clean File:{}", deleteFolder, deleteFile);
				TimeUnit.MINUTES.sleep(30);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
