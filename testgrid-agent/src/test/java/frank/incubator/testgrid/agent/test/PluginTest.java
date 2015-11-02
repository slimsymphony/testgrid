package frank.incubator.testgrid.agent.test;

import java.io.File;
import java.util.Collection;

import com.google.gson.reflect.TypeToken;
import frank.incubator.testgrid.agent.AgentNode;
import frank.incubator.testgrid.agent.plugin.PluginManager;
import frank.incubator.testgrid.agent.plugin.PluginManager.PluginDescriptor;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.plugin.TestGridPlugin;

/**
 * @author Wang Frank
 *
 */
public class PluginTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String pluginConfig = CommonUtils.loadResourcesAsString(Constants.PLUGIN_CONFIG_FILE, CommonUtils.RESOURCE_LOAD_MODE_ONLY_EMBEDED, true);
		Collection<PluginDescriptor> pds = CommonUtils.fromJson(pluginConfig, new TypeToken<Collection<PluginDescriptor>>() {
		}.getType());
		for (PluginDescriptor pd : pds)
			System.out.println(pd);
		AgentNode agent = new AgentNode(null);
		TestGridPlugin<File> plugin = null;
		try {
			plugin = PluginManager.initialize(agent, "ScreenshotPlugin");
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("finished");
	}

}
