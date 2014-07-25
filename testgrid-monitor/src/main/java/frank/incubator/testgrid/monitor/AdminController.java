/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frank.incubator.testgrid.monitor;

import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import java.io.File;

import javax.jms.Message;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.model.Device;

/**
 * Monitor controller for MQ maintenance messages.
 */
public class AdminController {
    
    private LogConnector log;

    private MessageHub hub;
    
    public AdminController(MessageHub inHub) {
        hub = inHub;
        log = LogUtils.get("AdminControler");
    }
    
    public String cancelTestOnAgent(String agentHost, String testId) {
        try {
            Message msg = hub.createMessage(Constants.MSG_TARGET_MONITOR);
            setProperty( msg, Constants.MSG_HEAD_TARGET, Constants.MSG_TARGET_AGENT + agentHost);
            setProperty( msg, Constants.MSG_HEAD_TESTID, testId);
            setProperty( msg, Constants.MSG_HEAD_NOTIFICATION_TYPE, Constants.NOTIFICATION_TEST);
            setProperty( msg, Constants.MSG_HEAD_NOTIFICATION_ACTION, Constants.ACTION_TEST_CANCEL);
            hub.getPipe(Constants.HUB_AGENT_NOTIFICATION).send(msg);
            return "";
        } catch (Exception ex) {
            log.error("Exception in cancelTestOnAgent: {}", ex);
            return ex.getMessage();
        }
    }
    
    public String releaseDeviceOnAgent(String agentHost, String details){
        try {
            Device device = CommonUtils.fromJson(details, Device.class);
            Message msg = hub.createMessage(Constants.MSG_TARGET_MONITOR);
            setProperty( msg,Constants.MSG_HEAD_TARGET, Constants.MSG_TARGET_AGENT + agentHost);
            setProperty( msg,Constants.MSG_HEAD_DEVICE_CONDITION, CommonUtils.toJson(device.getAttributes()));
            setProperty( msg,Constants.MSG_HEAD_NOTIFICATION_TYPE, Constants.NOTIFICATION_DEVICE);
            setProperty( msg,Constants.MSG_HEAD_NOTIFICATION_ACTION, Constants.ACTION_DEVICE_RELEASE);
            hub.getPipe(Constants.HUB_AGENT_NOTIFICATION).send(msg);
            return "";
        } catch (Exception ex) {
            log.error("Exception in releaseDeviceOnAgent: {}", ex);
            return ex.getMessage();
        }
    }
    
    public String setModeForAgent(String agentHost, String mode) {
        try {
            Message msg = hub.createMessage(Constants.MSG_TARGET_MONITOR);
            setProperty( msg,Constants.MSG_HEAD_TARGET, Constants.MSG_TARGET_AGENT + agentHost);
            setProperty( msg,Constants.MSG_HEAD_NOTIFICATION_TYPE, Constants.NOTIFICATION_SYSTEM);
            if (mode.equals("SetMaintainMode")){
                setProperty( msg,Constants.MSG_HEAD_NOTIFICATION_ACTION, Constants.ACTION_AGENT_MAINTAIN);
            }else{
                setProperty( msg,Constants.MSG_HEAD_NOTIFICATION_ACTION, Constants.ACTION_AGENT_NORMAL);
            }
            hub.getPipe(Constants.HUB_AGENT_NOTIFICATION).send(msg);
            return "";
        } catch (Exception ex) {
            log.error("Exception in setModeForAgent: {}", ex);
            return ex.getMessage();
        }
    }
    
    public String injectPropertiesToAgent(String agentHost, String properties) {
        try {
            Message msg = hub.createMessage(Constants.MSG_TARGET_MONITOR);
            setProperty( msg,Constants.MSG_HEAD_TARGET, Constants.MSG_TARGET_AGENT + agentHost);
            setProperty( msg,Constants.MSG_HEAD_NOTIFICATION_TYPE, Constants.NOTIFICATION_SYSTEM);
            setProperty( msg,Constants.MSG_HEAD_NOTIFICATION_ACTION, Constants.ACTION_AGENT_CONFIG_UPDATE);
            setProperty( msg,Constants.MSG_HEAD_CONFIG_CHANGES, properties);
            hub.getPipe(Constants.HUB_AGENT_NOTIFICATION).send(msg);
            return "";
        } catch (Exception ex) {
            log.error("Exception in injectPropertiesToAgent: {}", ex);
            return ex.getMessage();
        }
    }
    
    public void sendLoopbackHeartbeat() {
        try {
            Message msg = hub.createMessage(Constants.MSG_TARGET_MONITOR);
            hub.getPipe(Constants.HUB_MONITOR_STATUS).send(msg);
        } catch (Exception ex) {
            log.error("Exception in injectPropertiesToAgent: {}", ex);
        }
    }
    
    
    protected void createFileIfNotExists(String fileName){
        try{
            File templateFile = new File(fileName);
            if (!templateFile.exists()){
                templateFile.createNewFile();
            }   
        }catch (Exception ex){
            log.error("Exception in createFileIfNotExists: {}", ex);
        }
    }
}
