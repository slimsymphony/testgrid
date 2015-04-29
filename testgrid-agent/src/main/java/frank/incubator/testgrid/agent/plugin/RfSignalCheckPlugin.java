package frank.incubator.testgrid.agent.plugin;

import static frank.incubator.testgrid.common.CommonUtils.exec;
import static frank.incubator.testgrid.common.CommonUtils.isWindows;

import java.io.BufferedReader;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.util.Calendar;

import frank.incubator.testgrid.agent.device.AndroidDevice;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;

/**
 * @author Wang Frank
 *
 */
public class RfSignalCheckPlugin extends AbstractAgentPlugin<Void> {

	public RfSignalCheckPlugin() {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public Void call() {
		File parent = new File(this.getWorkspace(), "signal");
		if (!parent.exists())
			parent.mkdirs();
		File f = null;
		RandomAccessFile raf = null;
		for (Device d : this.getDm().listDevices()) {
			try {
				if (d instanceof AndroidDevice && d.getState() != Device.DEVICE_LOST
						&& d.getState() != Device.DEVICE_LOST_TEMP) {
					String sn = d.getAttribute(Constants.DEVICE_SN);
					f = new File(parent, sn + ".txt");
					if (!f.exists())
						f.createNewFile();
					else {
						Calendar cal = Calendar.getInstance();
						int day = cal.get(Calendar.DAY_OF_MONTH);
						cal.setTimeInMillis(f.lastModified());
						int day2 = cal.get(Calendar.DAY_OF_MONTH);
						if (day != day2) {
							f.renameTo(new File(parent, f.getName() + "." + cal.get(Calendar.YEAR)
									+ cal.get(Calendar.MONTH) + cal.get(Calendar.DAY_OF_MONTH)));
							f = new File(parent, sn + ".txt");
							f.createNewFile();
						}
					}
					try {
						raf = new RandomAccessFile(f.getAbsolutePath(), "rw");
						raf.seek(f.length());
						raf.write(CommonUtils.getCurrentTime("yyyy-MM-dd HH:mm:ss:SSS\t").getBytes());
						String val = d.getAttribute(Constants.DEVICE_SIM1_SIGNAL);
						String val2 = d.getAttribute(Constants.DEVICE_SIM1_SIGNAL);
						if (val != null) {
							raf.write((" Sim1:" + val).getBytes());
						}
						if (val2 != null) {
							raf.write((" Sim2:" + val2).getBytes());
						}
						if (val == null && val2 == null) {
							BufferedReader br = null;
							String line = null;
							String signalStrength = "";
							if (CommonUtils.isWindows())
								signalStrength = CommonUtils.grep(
										exec(adb() + " -s " + sn + " shell dumpsys telephony.msim.registry", null),
										"mSignalStrength", false);
							else
								signalStrength = exec(adb() + " -s " + sn
										+ " shell dumpsys telephony.msim.registry|grep mSignalStrength", null);

							br = new BufferedReader(new StringReader(signalStrength.trim()));
							int simCn = 0;
							while ((line = br.readLine()) != null) {
								line = line.trim();
								if (line.indexOf("mSignalStrength") >= 0) {
									line = line.substring(line.indexOf(":") + 1).trim();
									line = line.substring(0, line.indexOf(" "));
									try {
										int aus = Integer.parseInt(line);
										int dBm = 0;
										if (aus != 99)
											dBm = aus * 2 - 113;
										raf.write((" Sim" + (++simCn) + ":").getBytes());
										raf.write((String.valueOf(dBm) + "dBm").getBytes());
									} catch (Exception e) {
									}
								}
							}
						}
						raf.write('\r');
						raf.write('\n');
					} finally {
						CommonUtils.closeQuietly(raf);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.google.common.util.concurrent.FutureCallback#onSuccess(java.lang.
	 * Object)
	 */
	@Override
	public void onSuccess(Void v) {
		log.info("Detect rf signal success.");
	}

	@Override
	public void onFailure(Throwable t) {
		log.error("Detect rf signal met exception.", t);
	}

	private String adb() {
		if (isWindows())
			return "%ADB_HOME%\\adb";
		else
			return "$ADB_HOME/adb";
	}
}
