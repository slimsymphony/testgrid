package frank.incubator.testgrid.agent.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.util.Properties;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;

/**
 * PLugin to response application package install request.
 * 
 * @author peiyang.wy
 *
 */
public class PkgInstallPlugin extends AbstractAgentPlugin<Void> {

	@Override
	public void doWatch(WatchEvent<Path> event, Path path) {
		Kind<Path> kind = event.kind();
		if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
			boolean result = false;
			File f = path.toFile();
			String token = "";
			if (f.getName().endsWith(".properties")) {
				String sn = f.getName().substring(0, f.getName().lastIndexOf("."));
				Properties props = new Properties();
				try {
					props.load(new StringReader(CommonUtils.readFileContent(f)));
					String pkgPath = (String) props.get("path");
					if (pkgPath != null) {
						token = pkgPath.substring(0, pkgPath.indexOf("_"));
						File base = new File(workspace, "install/" + sn);
						File pkgFile = new File(base, pkgPath);
						if (pkgFile.isFile() && pkgFile.exists()) {
							Device d = this.getDm().getDeviceBy(Constants.DEVICE_SN, sn);
							if (d != null) {
								if (d.getAttribute(Constants.DEVICE_PLATFORM).equals(Constants.PLATFORM_ANDROID)) {
									StringBuilder output = new StringBuilder();
									String cmd = "adb -s " + sn + " install -r " + pkgFile.getAbsolutePath();
									log.info("Begin to install android app:" + cmd);
									int ret = CommonUtils.execBlocking(cmd, null, null, output, 2L * Constants.ONE_MINUTE);
									if (ret != 0) {
										log.error("Execute install app failed. ret:{}, output:{}", ret, output.toString());
									} else {
										if (output.toString().toLowerCase().indexOf("success") >= 0) {
											result = true;
										}
									}
								} else {
									String cmd = "fruitstrap -i " + sn + " -b " + pkgFile.getAbsolutePath();
									log.info("Begin to install ios app:" + cmd);
									StringBuilder isFinish = new StringBuilder();
									String out = CommonUtils.exec(cmd, null, 2L * Constants.ONE_MINUTE, isFinish, log);
									boolean finish = CommonUtils.parseBoolean(isFinish.toString(), false);
									// int ret = CommonUtils.execBlocking(cmd,
									// null, null, output,
									// 2L*Constants.ONE_MINUTE);
									if (!finish) {
										log.error("Execute install app failed. output:{}", out);
									} else {
										if (out.contains("100%"))
											result = true;
										else
											log.error("Execute install app failed. output:{}", out);
									}
								}
							}
						}
					}
				} catch (IOException e) {
					this.getLog().error("Execute Package install failed.", e);
				} finally {
					log.info("Delete task file:{}", f.getAbsolutePath());
					f.delete();
				}

				// create result file
				File resultFolder = new File(this.getWorkspace(), "install/results");
				if (!resultFolder.exists())
					resultFolder.mkdirs();
				File resultFile = new File(resultFolder, token + "_" + sn + ".properties");
				FileOutputStream fos = null;
				try {
					fos = new FileOutputStream(resultFile);
					StringBuilder sb = new StringBuilder();
					sb.append("success=").append(result);
					fos.write(sb.toString().getBytes());
					fos.flush();
				} catch (Exception ex) {
					this.getLog().error("Write to result file failed. file:" + path.toString(), ex);
				} finally {
					CommonUtils.closeQuietly(fos);
				}
			}
		}
	}

	@Override
	public void suspend() {

	}

	@Override
	public void deactive() {

	}
}
