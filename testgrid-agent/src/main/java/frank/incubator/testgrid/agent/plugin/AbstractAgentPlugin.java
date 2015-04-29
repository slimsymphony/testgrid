package frank.incubator.testgrid.agent.plugin;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import frank.incubator.testgrid.agent.device.DeviceManager;
import frank.incubator.testgrid.common.StatusChangeNotifier;
import frank.incubator.testgrid.common.file.FileWatchService;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.Pipe;
import frank.incubator.testgrid.common.plugin.PluginPermission;
import frank.incubator.testgrid.common.plugin.TestGridPlugin;

/**
 * Base Class for all the AbstractAgentPlugin. Done basic resource setup and
 * initial process. Inherited classes, please be caution that it should keep the
 * constructor have no parameters.
 * 
 * @author Wang Frank
 * 
 */
public abstract class AbstractAgentPlugin<V> implements TestGridPlugin<V> {

	private String name;

	private long delay;

	private TimeUnit unit;

	private PluginPermission[] permissions;

	protected Map<String, Pipe> pipes;

	protected File workspace;

	protected DeviceManager dm;

	protected Logger log;

	protected ListenableFuture<V> result;

	protected ListeningScheduledExecutorService pluginPool;

	protected String[] events;

	protected StatusChangeNotifier notifier;

	protected Map<String, Object> attributes;

	protected int state = TestGridPlugin.IDLE;
	
	protected FileWatchService watcher;

	public AbstractAgentPlugin() {
	}

	@Override
	public String getName() {
		return name;
	}

	public TimeUnit getSchedule() {
		return unit;
	}

	public void setSchedule(TimeUnit schedule) {
		this.unit = schedule;
	}

	public Pipe getPipe(String pipeName) {
		return pipes.get(pipeName);
	}

	public DeviceManager getDm() {
		return dm;
	}

	public Logger getLog() {
		return log;
	}

	public Map<String, Pipe> getPipes() {
		return pipes;
	}

	public long getDelay() {
		return delay;
	}

	public void setDelay(long delay) {
		this.delay = delay;
	}

	public File getWorkspace() {
		return workspace;
	}

	public void setWorkspace(File workspace) {
		this.workspace = workspace;
	}

	public ListeningScheduledExecutorService getPluginPool() {
		return pluginPool;
	}

	public void setPluginPool(ListeningScheduledExecutorService pluginPool) {
		this.pluginPool = pluginPool;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPermissions(PluginPermission[] permissions) {
		this.permissions = permissions;
	}

	public void setPipes(Map<String, Pipe> pipes) {
		this.pipes = pipes;
	}

	public void setDm(DeviceManager dm) {
		this.dm = dm;
	}

	public void setLog(Logger log) {
		this.log = log;
	}

	public StatusChangeNotifier getNotifier() {
		return notifier;
	}

	public void setNotifier(StatusChangeNotifier notifier) {
		this.notifier = notifier;
	}

	public String[] getEvents() {
		return events;
	}

	public void setEvents(String[] events) {
		this.events = events;
	}

	@Override
	public ListenableFuture<V> getResult() {
		return result;
	}

	public void setResult(ListenableFuture<V> result) {
		this.result = result;
	}

	@Override
	public PluginPermission[] getPermissions() {
		return permissions;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}
	
	public Object getAttribute(String key) {
		return attributes.get(key);
	}
	
	public Object getAttribute(String key, Object defaultValue) {
		Object ret = attributes.get(key);
		if(ret == null)
			return defaultValue;
		return ret;
	}
	
	public TimeUnit getUnit() {
		return unit;
	}

	public void setUnit(TimeUnit unit) {
		this.unit = unit;
	}

	public int getState() {
		return state;
	}

	public void setState(int state) {
		this.state = state;
	}

	public FileWatchService getWatcher() {
		return watcher;
	}

	public void setWatcher(FileWatchService watcher) {
		this.watcher = watcher;
	}

	
	@Override
	public V call() throws Exception {
		return null;
	}
	
	/**
	 * EntryPoint of a plugin.
	 */
	//@SuppressWarnings("unchecked")
	@SuppressWarnings("unchecked")
	public void start() {
		boolean execEnable = false;
		for (PluginPermission p : permissions) {
			if (p.equals(PluginPermission.EXECUTE_SCHEDULED_OPERATION)) {
				execEnable = true;
				break;
			}
		}

		if (execEnable) {
			if (pluginPool == null)
				pluginPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1));
			final AbstractAgentPlugin<V> put = this;
			result = (ListenableFuture<V>) pluginPool.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					pluginPool.schedule(put, 0, unit);
				}}, 1, delay, unit);
			Futures.addCallback(result, this);
		}
		
		if(this.watcher != null) {
			watcher.start();
		}
		this.setState(TestGridPlugin.STARTED);
	}

	/**
	 * Stop the plugin functionality.
	 */
	@Override
	public void suspend() {
		if (pluginPool != null) {
			pluginPool.shutdown();
			int count = 0;
			while (!pluginPool.isTerminated()) {
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					if (count < 3)
						count++;
					else {
						pluginPool.shutdownNow();
					}
				}
			}
			pluginPool = null;
		}
		if(this.watcher != null) {
			watcher.pause();
		}
		this.setState(TestGridPlugin.IDLE);
	}

	/**
	 * Dispose Managed resource after user defined dispose();
	 * 
	 * @see frank.incubator.testgrid.common.plugin.TestGridPlugin#uninstall()
	 */
	@Override
	public void deactive() {
		if(this.watcher != null) {
			watcher.dispose();
		}
		if (pluginPool != null) {
			pluginPool.shutdown();
			int count = 0;
			while (!pluginPool.isTerminated()) {
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					if (count < 3)
						count++;
					else {
						pluginPool.shutdownNow();
					}
				}
			}
			pluginPool = null;
		}

		dm = null;
		workspace = null;
		if (pipes != null)
			pipes.clear();

		if (events != null && notifier != null) {
			@SuppressWarnings("rawtypes")
			Collection<Callable> runs = null;
			for (String event : events) {
				runs = notifier.getEventListeners().get(event);
				runs.remove(this);
			}
		}
		this.permissions = null;
		LogUtils.dispose(log);
		this.setState(TestGridPlugin.STOPPED);
		PluginManager.plugins.remove(this.name);
	}

	/**
	 * Change execution Schedule.
	 * 
	 * @param delay
	 * @param unit
	 */
	public void changeSchedule(long delay, TimeUnit unit) {
		this.setDelay(delay);
		this.setSchedule(unit);
		if (pluginPool != null) {
			if (!pluginPool.isShutdown())
				pluginPool.shutdown();
			int count = 0;
			while (!pluginPool.isTerminated()) {
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					if (count < 3)
						count++;
					else {
						pluginPool.shutdownNow();
					}
				}
			}
			pluginPool = null;
			if (this.getState() == STARTED)
				start();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.agent.plugin.TestGridPlugin#init()
	 */
	@Override
	public void init() {
		if(this.watcher != null) {
			watcher.start();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see frank.incubator.testgrid.agent.plugin.TestGridPlugin#dispose()
	 */
	@Override
	public void dispose() {
	}
	
	@Override
	public void onSuccess(V result) {
	}

	@Override
	public void onFailure(Throwable t) {
	}
	
	/*
	 * (non-Javadoc)
	 * @see frank.incubator.testgrid.common.plugin.TestGridPlugin#doWatch()
	 */
	@Override
	public void doWatch(WatchEvent<Path> event, Path path) {
	}
}