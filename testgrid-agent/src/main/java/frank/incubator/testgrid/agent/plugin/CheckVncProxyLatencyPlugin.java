package frank.incubator.testgrid.agent.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.google.gson.reflect.TypeToken;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.Device;

/**
 * Plugin to check the latency from VncProxy to Devices and send back to control center. 
 */
public class CheckVncProxyLatencyPlugin extends AbstractAgentPlugin<Void> {
	private final Logger log = LogUtils.getLogger("CheckVncProxyLatencyPlugin");
	private Map<String, Map<String, Float>> latency = new ConcurrentHashMap<String, Map<String, Float>>();
	private String fetchVncProxyUrl = "http://127.0.0.1/getCacheInfo.json?type=VNCPROXY"; //
	private String resultPostUrl = "http://127.0.0.1/updateCacheInfo.json"; //

	@Override
	public Void call() throws Exception {
		latency.clear();
		log.info("Start checking vnc latency status");
		String fetch = (String) this.getAttribute("fetchVncProxyUrl", "");
		String post = (String) this.getAttribute("resultPostUrl", "");
		if (!fetch.equals(""))
			fetchVncProxyUrl = fetch;
		if (!post.equals(""))
			resultPostUrl = post;
		StringBuilder sb = new StringBuilder();
		int status = CommonUtils.httpGet(fetchVncProxyUrl, sb, log);
		List<Map<String, Object>> proxies = null;
		if (status == 200) {
			Map<String, List<Map<String, Object>>> backData = CommonUtils.fromJson(sb.toString(), new TypeToken<Map<String, List<Map<String, Object>>>>() {
			}.getType());
			if (backData != null && !backData.isEmpty())
				proxies = backData.get("vncProxies");
			else
				log.info("Proxy list: {}", sb.toString());
		} else {
			log.info("Get proxy list from {} failed.", fetch);
		}
		if (proxies != null && !proxies.isEmpty()) {
			for (Map<String, Object> proxy : proxies) {
				String host = (String) proxy.get("hostname");
				if (host != null && !host.trim().isEmpty()) {
					for (Device d : dm.allDevices().keySet()) {
						String sn = (String) d.getAttribute(Constants.DEVICE_SN);
						String ip = (String) d.getAttribute(Constants.DEVICE_IP_WLAN);
						String platform = (String) d.getAttribute(Constants.DEVICE_PLATFORM);
						boolean isRoot = CommonUtils.parseBoolean((String) d.getAttribute("is_root"), false);
						boolean isVnc = CommonUtils.parseBoolean((String) d.getAttribute("vnc"), false);
						if (!isRoot || !isVnc)
							continue;
						StringBuilder execOutput = new StringBuilder();
						List<Float> secs = new ArrayList<Float>();
						if (ip != null && !ip.trim().isEmpty() && !ip.trim().startsWith("192.")) {
							log.info("begin to check device[{}:{}] latency between: {} and {}", sn, platform, host, ip);
							int ret = 0;
							String[] arr = null;
							try {
								if (Constants.PLATFORM_ANDROID.equals(platform)) {
									if (CommonUtils.isWindows()) {
										ret = CommonUtils.execBlocking("adb -s " + sn + " shell ping -c 5 " + host, null, execOutput,
												TimeUnit.SECONDS.toMillis(10));
									} else {
										ret = CommonUtils.execBlocking("adb -s " + sn + " shell ping -c 5 " + host, null, execOutput,
												TimeUnit.SECONDS.toMillis(10));
									}
								} else if (Constants.PLATFORM_IOS.equals(platform)) {
									ret = CommonUtils.execBlocking("curl -s http://" + ip + ":8080/cmd?command=ping%20-c%205%20" + host, null, execOutput,
											TimeUnit.SECONDS.toMillis(10));
								} else {
									log.error("Illegal state for platform:{}", platform);
								}
							} catch (Exception ee) {
								log.error("Execution got exception.", ee);
							}
							if (ret == 0) {
								try {
									arr = execOutput.toString().split("\n");
								} catch (Exception ex) {
									log.error("Split output failed. output:" + execOutput.toString(), ex);
								}
							} else {
								log.error("Execute failed, ret:{}, output:{}", ret, execOutput.toString());
							}
							if (arr != null) {
								for (String line : arr) {
									if (line.indexOf("time=") > 0 && line.indexOf("ms") > 0) {
										line = line.substring(line.indexOf("time=") + 5, line.indexOf("ms"));
										float la = CommonUtils.parseFloat(line.trim(), 0);
										if (la != 0)
											secs.add(la);
										if (secs.size() == 5)
											break;
									}
								}
							} else {
								log.error("Can't find ping response");
							}
							float curLan = 0f;
							if (!secs.isEmpty()) {
								float sum = 0;
								for (float f : secs) {
									sum += f;
								}
								curLan = sum / (float) secs.size();
							}

							log.info("Curlatency is {}", curLan);

							if (curLan != 0) {
								if (!latency.containsKey(sn)) {
									latency.put(sn, new HashMap<String, Float>());
								}
								latency.get(sn).put(host, curLan);
							} else {
								log.info("Can't get valid latency. for sn:{} host:{}", sn, host);
							}

						} else {
							log.info("Won't check latency for device[{}]: {} and {}", sn, host, ip);
						}
					}
				}
			}
		}
		if (!latency.isEmpty()) {
			Map<String, String> params = new HashMap<String, String>();
			params.put("type", "VNCLATENCY");
			params.put("content", CommonUtils.toJson(latency));
			StringBuilder output = new StringBuilder();
			int statusCode = CommonUtils.httpPost(resultPostUrl, params, "UTF-8", output);
			log.info("Post latency check status to server response code:{} ,Params:{}, response output:{}.", statusCode, CommonUtils.toJson(params),
					output.toString());
		} else {
			log.info("latency is NULL.");
		}
		return null;
	}

	@Override
	public void onSuccess(Void result) {
		log.info("Check VncProxy networt latency success.");
	}

	@Override
	public void onFailure(Throwable t) {
		log.error("Check VncProxy networt latency failed.", t);
	}

}
