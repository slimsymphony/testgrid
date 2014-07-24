/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frank.incubator.testgrid.monitor;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author larryang
 */
public class MonitorCacheUpdater extends Thread {

    private LogConnector log;
    private boolean running = true;
    private MonitorCache monitorCache;

    public MonitorCacheUpdater(MonitorCache monitorCache) {
        this.monitorCache = monitorCache;
        log = LogUtils.get("MonitorCacheUpdater");
    }

    @Override
    public void run() {
        while (running) {
            try {
                monitorCache.cleanupAgents();
                monitorCache.cleanupClients();
                monitorCache.cleanupDevices();
                monitorCache.cleanupTasks();
                monitorCache.cleanupTests();
                RegionManager.triggerLoopbackHeartbeat();
                TimeUnit.SECONDS.sleep(300);
            } catch (Exception e) {
                log.error("Met exception while update timedout queue elements.", e);
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}
