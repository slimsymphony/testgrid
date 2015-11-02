package frank.incubator.testgrid.agent.plugin;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.Device;

public class VncAutoReleasePlugin extends AbstractAgentPlugin<Void> {
	private final Logger log = LogUtils.getLogger("VncAutoReleasePlugin");
	private Map<String, Object[]> reserveList = new HashMap<String, Object[]>();

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public Void call() {
		long now = System.currentTimeMillis();
		for (Device d : this.getDm().allDevices().keySet()) {
			String sn = d.getAttribute(Constants.DEVICE_SN);
			String status = (d.getTaskStatus() == null) ? "" : d.getTaskStatus().toLowerCase().trim();
			if (d.getState() == Device.DEVICE_RESERVED && status.startsWith("vnc")) {
				log.info("Check device[{}] with status:{}", d.getId(), d.getTaskStatus());
				Object[] ar = new Object[2];
				if (status.indexOf(":::") > 0) {
					ar[0] = status.substring(status.indexOf(":::") + 3, status.lastIndexOf(":::"));
					ar[1] = status.substring(status.lastIndexOf(":::"));
				}
				if (reserveList.containsKey(sn)) {
					Object[] rec = reserveList.get(sn);
					if (ar[0].equals(rec[0])) {// same person
						long start = CommonUtils.parseLong((String) rec[1], 0);
						if ((now - start) > (Constants.ONE_MINUTE * 5L)) {
							this.getDm().releaseDevices(d);
							reserveList.remove(sn);
							log.info("Release device[{}]", d.getId());
						}
					} else {
						reserveList.put(sn, ar);
						log.info("Update vnc reserve device[{}] info", d.getId());
					}
				} else {
					reserveList.put(sn, ar);
					log.info("Found new Vnc reserve device[{}]", d.getId());
				}
			}

		}
		return null;
	}
}
