package frank.incubator.testgrid.common;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.exec.ExecuteWatchdog;

public class KillProcessTest {

	public static void main(String[] args){
		StringBuilder output = new StringBuilder();
		try {
			ExecuteWatchdog dog = CommonUtils.execASync("sleep 300", (File)null, (Map<String, String>)null, null, null, 3000);
			dog.destroyProcess();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println(output);
		//CommonUtils.killProcess(pid, false);
	}

}
