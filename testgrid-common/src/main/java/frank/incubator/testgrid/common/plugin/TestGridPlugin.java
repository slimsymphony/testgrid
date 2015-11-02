package frank.incubator.testgrid.common.plugin;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.concurrent.Callable;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Plugin interface for all additional services. Please always inherit from some
 * basic AgentPlugin e.g.:
 * {@link frank.incubator.testgrid.agent.plugin.AbstractAgentPlugin} to create a
 * new Plugin.
 * 
 * @author Wang Frank
 * 
 */
public interface TestGridPlugin<V> extends Callable<V>, FutureCallback<V> {

	/**
	 * Plugin been suspended, and could be restored.
	 */
	public int IDLE = 1;

	/**
	 * Plugin service normally started.
	 */
	public int STARTED = 2;

	/**
	 * Plugin been stopped. If want to reuse it, it should be re-initialized.
	 */
	public int STOPPED = 3;

	/**
	 * Get plugin's Permissions.
	 * 
	 * @return
	 */
	public PluginPermission[] getPermissions();

	/**
	 * Initial steps of plugin. Optional.
	 */
	public void init();

	/**
	 * Resource dispose for the plugin. Optional.
	 */
	public void dispose();

	/**
	 * Get the future of the plugin.
	 * 
	 * @return
	 */
	public ListenableFuture<V> getResult();

	/**
	 * Set the future of the plugin.
	 * 
	 * @return
	 */
	public void setResult(ListenableFuture<V> result);

	/**
	 * Return the plugin name.
	 * 
	 * @return
	 */
	public String getName();

	/**
	 * bootstrap the plugin.
	 */
	public void start();

	/**
	 * yield the plugin.
	 */
	public void suspend();

	/**
	 * Release all the resources.
	 */
	public void deactive();

	/**
	 * Get Plugin's current state.
	 * 
	 * @return
	 */
	public int getState();

	/**
	 * implement FS watch actions;
	 */
	public void doWatch(WatchEvent<Path> event, Path path);

}
