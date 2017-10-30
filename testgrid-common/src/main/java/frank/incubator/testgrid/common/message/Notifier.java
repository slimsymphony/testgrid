package frank.incubator.testgrid.common.message;

import java.util.Map;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;

public class Notifier {
	private boolean enable;
	private String receivers;
	private long lastSendTime = 0;
	private String notifyUrl = "http://127.0.0.1/sendspecificnotification.json";
	private String charset = "GBK";

	public boolean isEnable() {
		return enable;
	}

	public void setEnable(boolean isEnable) {
		this.enable = isEnable;
	}

	public String getReceivers() {
		return receivers;
	}

	public void setReceivers(String receivers) {
		this.receivers = receivers;
	}

	public String getNotifyUrl() {
		return notifyUrl;
	}

	public void setNotifyUrl(String notifyUrl) {
		this.notifyUrl = notifyUrl;
	}

	public String getCharset() {
		return charset;
	}

	public void setCharset(String charset) {
		this.charset = charset;
	}

	public synchronized void sendNotification(Map<String, String> params) {
		if (enable && params != null && !params.isEmpty()) {
			params.put("receivers", receivers);
			long curr = System.currentTimeMillis();
			if ((curr - lastSendTime) > Constants.ONE_SECOND * 10) {
				try {
					StringBuilder sb = new StringBuilder();
					CommonUtils.httpPost(notifyUrl, params, "GBK", sb);
					lastSendTime = System.currentTimeMillis();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
	}
}
