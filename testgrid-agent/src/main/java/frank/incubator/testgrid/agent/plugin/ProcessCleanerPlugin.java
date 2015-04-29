package frank.incubator.testgrid.agent.plugin;

import java.util.Map;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;

/**
 * This plugin is a daemon to keep checking the legacy/dead process which may
 * still have operation with "Free" state devices.
 * 
 * @author Wang Frank
 * 
 */
public class ProcessCleanerPlugin extends AbstractAgentPlugin<Void> {

	private int killed;

	@Override
	public void onSuccess(Void result) {
		log.info("Cleaned " + killed + " processes.");
	}

	/**
	 * 
	 * @param t
	 */
	@Override
	public void onFailure(Throwable t) {
		log.error("Clean process failed.", t);
	}

	@Override
	public Void call() {
		Map<Integer, String> procs = CommonUtils.getAllProcess();
		if (dm != null) {
			for (Device d : dm.listDevices()) {
				if (d.getState() == Device.DEVICE_FREE) {
					clean((String) d.getAttribute(Constants.DEVICE_SN), procs);
				}
			}
		}
		return null;
	}

	/**
	 * Clean the specified sn related
	 * 
	 * @param sn
	 * @param procs
	 */
	private void clean(String sn, Map<Integer, String> procs) {
		killed = 0;
		if (sn != null && !sn.trim().isEmpty()) {
			String cmd = null;
			for (int pid : procs.keySet()) {
				cmd = procs.get(pid);
				if (cmd.contains(sn)) {
					boolean iskilled = CommonUtils.killProcess(pid);
					if (iskilled)
						killed++;
					log.info("Kill Process[" + pid + ":" + cmd + "], result:" + iskilled);
				}
			}
		}
	}
}
