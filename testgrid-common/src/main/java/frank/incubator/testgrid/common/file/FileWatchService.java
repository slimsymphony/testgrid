package frank.incubator.testgrid.common.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * A File change event watcher service. The child class should implement the
 * {@link handleEvent} Method to handle different
 * {@link java.nio.file.StandardWatchEventKinds} events.
 * 
 * @author Wang Frank
 *
 */
public abstract class FileWatchService extends Thread {

	protected final boolean recursive;
	@SuppressWarnings("unused")
	private File rootDir, tmp = null;
	protected LogConnector log;
	Map<String, Long> fileMonitor = new ConcurrentHashMap<String, Long>();

	public FileWatchService(File dir, boolean recursive) throws IOException {
		this.setName("FileWatchService");
		this.rootDir = dir;
		this.recursive = recursive;
		this.log = LogUtils.get("FileWatchService");
		if (rootDir.exists()) {
			if (rootDir.isFile()) {
				fileMonitor.put(rootDir.getName(), rootDir.lastModified());
			} else if (rootDir.isDirectory()) {
				for (File f : rootDir.listFiles()) {
					fileMonitor.put(f.getName(), f.lastModified());
				}
			}
		}
	}

	/**
	 * Handle different type of watch event.
	 * 
	 * @param event
	 * @param path
	 */
	public abstract void handleEvent(WatchEvent event, File path);

	@Override
	public void run() {

		while (true) {
			if (rootDir.exists()) {
				if (rootDir.isFile()) {
					if(rootDir.lastModified() != fileMonitor.get(rootDir.getName())) {
						handleEvent(WatchEvent.UPDATED, rootDir);
						fileMonitor.put(rootDir.getName(), rootDir.lastModified());
					}
				} else if (rootDir.isDirectory()) {
					for (File f : rootDir.listFiles()) {
						if(!fileMonitor.containsKey(f.getName())) {
							fileMonitor.put(f.getName(), f.lastModified());
							handleEvent(WatchEvent.CREATED, f);
						}else if(f.lastModified() != fileMonitor.get(f.getName())) {
							fileMonitor.put(f.getName(), f.lastModified());
							handleEvent(WatchEvent.UPDATED, f);
						}
					}
					File f = null;
					List<String> dds = new ArrayList<String>();
					for(String name : fileMonitor.keySet()) {
						f = new File(rootDir, name);
						if(!f.exists()) {
							handleEvent(WatchEvent.DELETED, f);
							dds.add(name);
						}
					}
					for(String name: dds) {
						fileMonitor.remove(name);
					}
				}
			} else {
				handleEvent(WatchEvent.DELETED, rootDir);
				fileMonitor.remove(rootDir.getName());
			}

		}
	}
}
