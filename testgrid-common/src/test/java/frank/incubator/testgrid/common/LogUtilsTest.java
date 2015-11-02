package frank.incubator.testgrid.common;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

public class LogUtilsTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LogConnector log = LogUtils.get("ttt", new LogConnector.NullOutputSteam());
		LogConnector log2 = LogUtils.get("ttt2", new LogConnector.NullOutputSteam());
		log.info("log1....");
		log2.info("log2....");
		LogUtils.dispose(log);
		log.info("log1....");
		LogUtils.dispose(log2);
		log2.info("log2....");
		LogConnector log3 = LogUtils.get("ttt3", new LogConnector.NullOutputSteam());
		log3.info("log3");
		LogUtils.dispose();
		log3.info("log3");
	}

}
