package frank.incubator.testgrid.agent.plugin;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.reflect.TypeToken;

import frank.incubator.testgrid.agent.AgentNode;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.file.FileWatchService;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.message.Pipe;
import frank.incubator.testgrid.common.plugin.PluginPermission;
import frank.incubator.testgrid.common.plugin.TestGridPlugin;

/**
 * TestGrid plugin Creator Factory and Management center.
 * 
 * @author Wang Frank
 *
 */
public final class PluginManager {

	final static Logger log = LogUtils.getLogger("PluginManager");

	/**
	 * Descriptor of a special testGrid Plugin.
	 * 
	 * @author Wang Frank
	 *
	 */
	public static class PluginDescriptor {

		private String className;
		private String pluginName;
		private PluginPermission[] permissions;
		private long scheduleSecs;
		private String[] pipes;
		private String[] events;
		private Map<String, Object> attributes;

		public String getClassName() {
			return className;
		}

		public void setClassName(String className) {
			this.className = className;
		}

		public String getPluginName() {
			return pluginName;
		}

		public void setPluginName(String pluginName) {
			this.pluginName = pluginName;
		}

		public PluginPermission[] getPermissions() {
			return permissions;
		}

		public void setPermissions(PluginPermission[] permissions) {
			this.permissions = permissions;
		}

		public long getScheduleSecs() {
			return scheduleSecs;
		}

		public void setScheduleSecs(long scheduleSecs) {
			this.scheduleSecs = scheduleSecs;
		}

		public String[] getPipes() {
			return pipes;
		}

		public void setPipes(String[] pipes) {
			this.pipes = pipes;
		}

		public String[] getEvents() {
			return events;
		}

		public void setEvents(String[] events) {
			this.events = events;
		}

		public Map<String, Object> getAttributes() {
			return attributes;
		}

		public void setAttributes(Map<String, Object> attributes) {
			this.attributes = attributes;
		}

		@Override
		public String toString() {
			return CommonUtils.toJson(this);
		}

		@Override
		public boolean equals(Object o) {
			if (super.equals(o))
				return true;
			else if (o != null && o instanceof PluginDescriptor)
				if (pluginName.equalsIgnoreCase(((PluginDescriptor) o).getPluginName()))
					return true;
			return false;
		}

		@Override
		public int hashCode() {
			int ret = 0;
			int r = 0;
			for (char c : this.pluginName.toCharArray()) {
				r++;
				ret += c * r;
			}
			r = 0;
			for (char c : this.className.toCharArray()) {
				r++;
				ret += c * r * 10;
			}
			return ret;
		}
	}

	public static Map<String, PluginDescriptor> pds = scanPlugins();

	@SuppressWarnings("rawtypes")
	public static Map<String, TestGridPlugin> plugins = new ConcurrentHashMap<String, TestGridPlugin>();

	/**
	 * Scan and load all the adapted plugins descriptors.
	 * 
	 * @return
	 */
	public static Map<String, PluginDescriptor> scanPlugins() {
		Map<String, PluginDescriptor> pds = new HashMap<String, PluginDescriptor>();
		String internalPluginStr = CommonUtils.loadResourcesAsString(Constants.PLUGIN_CONFIG_FILE, CommonUtils.RESOURCE_LOAD_MODE_ONLY_EMBEDED, false);
		try {
			if (internalPluginStr != null && !internalPluginStr.trim().isEmpty()) {
				PluginDescriptor[] ps = CommonUtils.fromJson(internalPluginStr, new TypeToken<PluginDescriptor[]>() {
				}.getType());
				if (ps != null) {
					for (PluginDescriptor pd : ps) {
						if (!pds.containsKey(pd.getPluginName()))
							pds.put(pd.getPluginName(), pd);
					}
				}
			}
		} catch (Exception ex) {
			log.error("PluginManager scan internal Plugin met exception:" + internalPluginStr, ex);
		}
		String externalPluginStr = CommonUtils.loadResourcesAsString(Constants.PLUGIN_CONFIG_FILE, CommonUtils.RESOURCE_LOAD_MODE_ONLY_EXTERNAL, false);
		try {
			if (externalPluginStr != null && !externalPluginStr.trim().isEmpty()) {
				PluginDescriptor[] ps = CommonUtils.fromJson(externalPluginStr, new TypeToken<PluginPermission[]>() {
				}.getType());
				if (ps != null) {
					for (PluginDescriptor pd : ps) {
						if (!pds.containsKey(pd.getPluginName()))
							pds.put(pd.getPluginName(), pd);
					}
				}
			}
		} catch (Exception ex) {
			log.error("PluginManager scan external Plugin met exception:" + externalPluginStr, ex);
		}
		return pds;
	}

	@SuppressWarnings("rawtypes")
	public static TestGridPlugin getPlugin(String pluginName) {
		if (pluginName == null)
			return null;
		if (plugins.get(pluginName) == null)
			scanPlugins();
		return plugins.get(pluginName);
	}

	/**
	 * Instantiation assigned {@link TestGridPlugin}( actually it should be
	 * inherit from {@link AbstractAgentPlugin}) based on the given plugin name.
	 * 
	 * @param agent
	 * @param pluginName
	 * @return
	 * @throws Exception
	 */
	public synchronized static <V> TestGridPlugin<V> initialize(AgentNode agent, String pluginName) throws Exception {
		PluginDescriptor pd = pds.get(pluginName);
		if (pd == null)
			throw new Exception("Plugin [" + pluginName + "] not found.");
		if (pd.getPermissions() == null || pd.getPermissions().length == 0)
			throw new Exception("Plugin [" + pluginName + "] should have at least one permission.");

		@SuppressWarnings("unchecked")
		final AbstractAgentPlugin<V> plugin = (AbstractAgentPlugin<V>) Class.forName(pd.getClassName()).newInstance();
		plugin.setName(pluginName);
		plugin.setLog(LogUtils.getLogger(pluginName));
		plugin.setPermissions(pd.getPermissions());
		plugin.setAttributes(pd.getAttributes());
		for (PluginPermission pp : pd.getPermissions()) {
			switch (pp) {
				case ALLOCATE_DEVICE_INFO:
					plugin.setDm(agent.getDm());
					break;
				case FSIO_WORKSPACE:
					plugin.setWorkspace(agent.getWorkspace());
					break;
				case UTILIZE_MESSAGE_HUB:
					if (pd.getPipes() != null) {
						Map<String, Pipe> pipes = new HashMap<String, Pipe>();
						MessageHub hub = agent.getHub();
						Pipe pipe = null;
						for (String pipeName : pd.getPipes()) {
							pipe = hub.getPipe(pipeName);
							if (pipe != null)
								pipes.put(pipeName, pipe);
						}
					}
					break;
				case REGISTER_EVENT_NOTIFY:
					if (pd.getEvents() != null) {
						for (String event : pd.getEvents()) {
							agent.getNotifier().addListener(event, plugin);
						}
					}
				case EXECUTE_SCHEDULED_OPERATION:
					plugin.setSchedule(TimeUnit.SECONDS);
					plugin.setDelay(pd.getScheduleSecs());
					plugin.setPluginPool(MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)));
					break;
				case FS_WATCH:
					plugin.setWorkspace(agent.getWorkspace());
					if (plugin.getAttribute("path") != null) {
						File f = new File(agent.getWorkspace(), (String) plugin.getAttribute("path"));
						if (!f.exists())
							f.mkdirs();
						final Path path = f.toPath();
						plugin.setWatcher(new FileWatchService(path, false) {
							@Override
							public void handleEvent(WatchEvent<Path> event, Path path) {
								plugin.doWatch(event, path);
							}
						});
					}
					break;
				default:
					break;
			}
		}
		plugin.init();
		plugins.put(pluginName, plugin);
		plugin.start();
		return plugin;
	}
}
