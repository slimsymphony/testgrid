package frank.incubator.testgrid.common.file;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

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

	private final WatchService watcher;
	private final Map<WatchKey, Path> keys;
	protected final boolean recursive;
	private boolean trace = false;
	@SuppressWarnings("unused")
	private Path rootDir, tmp = null;
	protected LogConnector log;
	private boolean runFlag = true;
	private boolean exitFlag = false;

	public FileWatchService(Path dir, boolean recursive) throws IOException {
		this.setName("FileWatchService");
		this.rootDir = dir;
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<WatchKey, Path>();
		this.recursive = recursive;
		this.log = LogUtils.get("FileWatchService");

		if (recursive) {
			log.debug("Scanning " + dir + " ...\n");
			registerAll(dir);
			log.debug("Done.");
		} else {
			register(dir);
		}
		this.trace = true;
	}

	/**
	 * Handle different type of watch event.
	 * 
	 * @param event
	 * @param path
	 */
	public abstract void handleEvent(WatchEvent<Path> event, Path path);

	private void register(Path dir) throws IOException {
		WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
		if (trace) {
			Path existing = keys.get(key);
			if (existing == null) {
				log.debug("register: " + dir + "\n");
			} else {
				if (!dir.equals(existing)) {
					log.debug("update: " + existing + " -> " + dir + "\n");
				}
			}
		}
		keys.put(key, dir);
	}

	private void registerAll(final Path start) throws IOException {

		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				register(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	void processEvents() {
		for (;;) {
			if (runFlag) {
				WatchKey key;
				try {
					key = watcher.take();
				} catch (InterruptedException x) {
					return;
				}
				Path path = keys.get(key);
				if (path == null) {
					continue;
				}
				for (WatchEvent<?> event : key.pollEvents()) {
					Kind<?> kind = event.kind();
					if (kind == OVERFLOW) {
						continue;
					}
					WatchEvent<Path> evt = cast(event);
					Path name = evt.context();
					Path child = path.resolve(name);
					log.info(child + " trigger event:" + event.kind().name());
					handleEvent(evt, child);
				}
				boolean valid = key.reset();
				if (!valid) {
					keys.remove(key);
					if (keys.isEmpty()) {
						break;
					}
				}
			}

			if (exitFlag)
				break;
		}
	}

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	@Override
	public void start() {
		runFlag = true;
		if (this.getState() == State.NEW)
			super.start();
	}

	public void dispose() {
		exitFlag = true;
	}

	@Override
	public void run() {
		processEvents();
	}

	public void pause() {
		runFlag = false;
	}
}
