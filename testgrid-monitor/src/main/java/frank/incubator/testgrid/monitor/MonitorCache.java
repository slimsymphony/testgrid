package frank.incubator.testgrid.monitor;

import frank.incubator.testgrid.common.CommonUtils;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.Agent;
import frank.incubator.testgrid.common.model.BaseObject;
import frank.incubator.testgrid.common.model.Client;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.Device;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class MonitorCache {

    private ConcurrentLinkedQueue<Agent> agents = new ConcurrentLinkedQueue<Agent>();
    private ConcurrentLinkedQueue<Client> clients = new ConcurrentLinkedQueue<Client>();
    private ConcurrentLinkedQueue<Task> tasks = new ConcurrentLinkedQueue<Task>();
    private ConcurrentLinkedQueue<Test> tests = new ConcurrentLinkedQueue<Test>();
    private ConcurrentLinkedQueue<Device> devices = new ConcurrentLinkedQueue<Device>();
    private Semaphore sa = new Semaphore(1);
    private Semaphore sc = new Semaphore(1);
    private Semaphore sta = new Semaphore(1);
    private Semaphore ste = new Semaphore(1);
    private Semaphore sde = new Semaphore(1);
    private final long QUEUE_ELEMENT_TIMEOUT = 1 * 3600 * 1000;
    private LogConnector log = LogUtils.get("MonitorCache");
    private MonitorCacheUpdater monitorCacheUpdater;
    
    public MonitorCache(){
        monitorCacheUpdater = new MonitorCacheUpdater(this);
    }

    public ConcurrentLinkedQueue<Client> getClients() {
        return clients;
    }

    public ConcurrentLinkedQueue<Test> getTests() {
        return tests;
    }

    public ConcurrentLinkedQueue<Agent> getAgents() {
        return agents;
    }

    public ConcurrentLinkedQueue<Task> getTasks() {
        return tasks;
    }

    public ConcurrentLinkedQueue<Device> getDevices() {
        return devices;
    }
    
    public void startUpdater(){
        monitorCacheUpdater.start();
    }

    public void updateClient(Client client) {
        try {
            sc.acquire();
            clients.remove(client);
            clients.add(client);
        } catch (Exception e) {
            log.error("Update Client status failed", e);
        } finally {
            sc.release();
        }
    }

    public void updateTest(Test test) {
        try {
            ste.acquire();
            tests.remove(test);
            tests.add(test);
        } catch (Exception e) {
            log.error("Update Test status Failed.", e);
        } finally {
            ste.release();
        }
    }

    public void updateTask(Task task) {
        try {
            sta.acquire();
            tasks.remove(task);
            tasks.add(task);
        } catch (Exception e) {
            log.error("Update Task status Failed.", e);
        } finally {
            sta.release();
        }
    }

    public void updateAgent(Agent agent) {
        try {
            sa.acquire();
            agents.remove(agent);
            agents.add(agent);
        } catch (Exception e) {
            log.error("Update Agent status Failed.", e);
        } finally {
            sa.release();
        }

        try{
            List<Device> devices = agent.getDevices();
            for (Device device : devices) {
                updateDevice(device);

                try{
                    if (!StringUtils.isEmpty(device.getTaskStatus())){
                        Test test = CommonUtils.fromJson(device.getTaskStatus(), Test.class);
                        updateTest(test);
                    }
                }catch(Exception e){
                    log.info(device.getTaskStatus() + "can not be converted to test object.");
                }
            }
        }catch(Exception e){
            log.error("Update agent related devices and tests failed.", e);
        }
    }

    public void updateDevice(Device device) {
        try {
            sde.acquire();
            devices.remove(device);
            devices.add(device);
        } catch (Exception e) {
            log.error("Update Device status failed", e);
        } finally {
            sde.release();
        }
    }

    public void calcAgentLoad() {

        for (Agent agent : agents) {
            int busyDevices = 0;
            for (Device device : agent.getDevices()) {
                if (device.getState() == Device.DEVICE_BUSY || device.getState() == Device.DEVICE_RESERVED) {
                    busyDevices++;
                }
            }

            agent.setLoadPercentage(agent.getDevices().size() > 0 ? busyDevices * 100 / agent.getDevices().size() : 0);
        }
    }
    
    public final Test findTestById(String testId){
        
        try {
            ste.acquire();

            Iterator<Test> testsIterator = tests.iterator();

            while (testsIterator.hasNext()){
                Test currentTest = testsIterator.next();

                if (currentTest.getId().equals(testId)){
                    return currentTest;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Update Test status Failed.", e);
            return null;
        } finally {
            ste.release();
        }

    }
    
    public void cleanupAgents(){
        try {
            sa.acquire();
            cleanupTimedOutQueueElement(agents);
        } catch (Exception e) {
            log.error("Clean timedout agents failed", e);
        } finally {
            sa.release();
        }
    }
    
    public void cleanupTests(){
        try {
            ste.acquire();
            cleanupTimedOutQueueElement(tests);
        } catch (Exception e) {
            log.error("Clean timedout tests failed", e);
        } finally {
            ste.release();
        }
    }
    
    public void cleanupClients(){
        try {
            sc.acquire();
            cleanupTimedOutQueueElement(clients);
        } catch (Exception e) {
            log.error("Clean timedout clients failed", e);
        } finally {
            sc.release();
        }
    }
    
    public void cleanupTasks(){
        try {
            sta.acquire();
            cleanupTimedOutQueueElement(tasks);
        } catch (Exception e) {
            log.error("Clean timedout tasks failed", e);
        } finally {
            sta.release();
        }
    }
    
    public void cleanupDevices(){
        try {
            sde.acquire();
            cleanupTimedOutQueueElement(devices);
        } catch (Exception e) {
            log.error("Clean timedout devices failed", e);
        } finally {
            sde.release();
        }
    }
    
    
    private <T extends BaseObject> void cleanupTimedOutQueueElement(ConcurrentLinkedQueue<T> queue){

        try {
            ArrayList<T> timedOutItemList = new ArrayList();
            Iterator<T> testsIterator = queue.iterator();

            while (testsIterator.hasNext()){
                T current = testsIterator.next();
                
                if (System.currentTimeMillis() - current.getLastUpdated() > QUEUE_ELEMENT_TIMEOUT){
                    log.info("Found timeout element, remove it.");
                    timedOutItemList.add(current);
                }
            }
            
            for (T current : timedOutItemList){
                queue.remove(current);
            }

        } catch (Exception e) {
            log.error("Update Test status Failed.", e);
        }
    }
}
