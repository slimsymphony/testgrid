package frank.incubator.testgrid.common;

import org.apache.commons.exec.ExecuteWatchdog;

public class ProcessTreeWatchDog extends ExecuteWatchdog {

	public ProcessTreeWatchDog(long timeout) {
		super(timeout);
	}
	
	private Process rootProcess;
	
	public Process getRootProcess() {
		return rootProcess;
	}
	
	@Override
    public synchronized void start(final Process processToMonitor) {
		rootProcess = processToMonitor;
		super.start(processToMonitor);
    }
	
	@Override
	public void destroyProcess() {
		try {
			if(rootProcess != null) {
				CommonUtils.killProcess(CommonUtils.getPid(rootProcess), true);
			}
		}catch(Throwable e) {
			e.printStackTrace();
		}
		try {
			super.destroyProcess();
		}catch(Throwable e) {
			e.printStackTrace();
		}
	}
}
