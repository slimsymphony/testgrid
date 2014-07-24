/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frank.incubator.testgrid.monitor;

import static frank.incubator.testgrid.common.message.MessageHub.setProperty;

import javax.jms.Message;

import frank.incubator.testgrid.common.message.MessageHub;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.CommonUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Monitor controller for MQ maintenance messages.
 * @author larryang
 */
public class AdminControler {
    
    private LogConnector log;

    private MessageHub hub;
    
    private String iCaseDir = "";
    
    private String svn = "/usr/bin/svn";
    
    //private final static String templateFileName = "test_manifest_template.info";
    
    private ICaseManager iCaseManager;

    public AdminControler(MessageHub inHub) {
        hub = inHub;
        log = LogUtils.get("AdminControler");
    }
    
    public AdminControler(MessageHub inHub, String inICaseDir) {
        hub = inHub;
        log = LogUtils.get("AdminControler");
        iCaseDir = inICaseDir;
        iCaseManager = new ICaseManager(inICaseDir);
    }

    public ICaseManager getiCaseManager() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy");
        String updateCommand = svn + " update --username autotest " + iCaseDir + "/" + formatter.format(new Date());
        log.info(updateCommand);
        String output = executeCommand(updateCommand);
        return iCaseManager;
    }

    public void setiCaseManager(ICaseManager iCaseManager) {
        iCaseManager = iCaseManager;
    }
     
    public void setICaseDir(String inICaseDir){
        iCaseDir = inICaseDir;
        iCaseManager = new ICaseManager(inICaseDir);
    }
    
    public void setSvn(String inSvn){
        svn = inSvn;
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
    
    protected File findLastModifiedInfoFile(File iCaseLocation, Map<String, String> viewModel){
        
        List<String> outputList = new ArrayList();
        
        try{
        
            String[] command = {
                    "/bin/bash",
                    "-c",
                    //"find " + iCaseLocation.getPath() + " | egrep -v '\\.svn' | xargs egrep -e 'PRODUCT=" + viewModel.get("PRODUCT") + "' | sed -e 's/:.*//' | sort -u | xargs egrep -e 'ICASE_MODE=" + viewModel.get("ICASE_MODE") + "' | sed -e 's/:.*//' | xargs egrep -e 'ICASE_PATH=" + viewModel.get("ICASE_PATH") + "' | sed -e 's/:.*//' | xargs egrep -e 'RESULT=SUCCESS' | sed -e 's/:.*//'"
                    "find " + iCaseLocation.getPath() + " | egrep -v '\\.svn' | egrep -v 'mon_' | egrep -v 'monitor' | xargs egrep -e 'TARGET_PRODUCT=" + viewModel.get("TARGET_PRODUCT") + "' | sed -e 's/:.*//' | sort -u | xargs egrep -e 'ICASE_MODE=" + viewModel.get("ICASE_MODE") + "' | sed -e 's/:.*//' | xargs egrep -e 'RESULT=SUCCESS' | sed -e 's/:.*//'"
            };

            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader =
                        new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine()) != null) {
                    if (StringUtils.isNotEmpty(line)){
                        outputList.add(line);
                    }
            }

            if (outputList.isEmpty()){
                return null;
            }

            String lastFileStr = outputList.get(outputList.size() - 1);

            return new File(lastFileStr);
        }catch(Exception e){
            log.error("Exception encountered when finding the last info file. {}", e);
            return null;
        }  
    }
    
    public String triggerTest(Map<String, String> viewModel, String triggerUser, String year, String week){
        
        try{
            
            if (StringUtils.isEmpty(triggerUser)){
                triggerUser = "anonymous";
            }
            
            if (StringUtils.isEmpty(iCaseDir)){
                return "ICase directory not configured.";
            }
            
            File svnExecutable = new File(svn);
            
            if (!svnExecutable.exists() || svnExecutable.isDirectory() || !svnExecutable.canExecute() ){
                return "SVN executable " + svn + " do not exists or not executable, please contact admin to check that.";
            }
            
            File iCaseLocation = new File(iCaseDir + "/" + year + "/" + week);
            
            if (!iCaseLocation.exists() || !iCaseLocation.isDirectory() || !iCaseLocation.canWrite() ){
                return "ICase directory " + iCaseDir + " do not exists or not readable, please contact admin to check that.";
            }
            
            String updateCommand = svn + " update --username autotest " + iCaseLocation.getPath();
            log.info(updateCommand);
            String output = executeCommand(updateCommand);
            //log.info(output);
            
            File lastModifiedInfoFile = findLastModifiedInfoFile(iCaseLocation, viewModel);
            
            if (lastModifiedInfoFile == null){
                return "Can not found latest info file with the parameters as you specified.";
            }
            
            log.info("Found last modified info file: " + lastModifiedInfoFile.getName());
            
            File currentTrigerInfoFile = new File(lastModifiedInfoFile.getParentFile().getPath() + "/" + "job.mon_" + System.currentTimeMillis() + "." + lastModifiedInfoFile.getName());
            
            log.info("Target trigger info file: " + currentTrigerInfoFile.getName());
            
            FileUtils.copyFile(lastModifiedInfoFile, currentTrigerInfoFile);
            
            Charset utfEncoding = Charset.forName("UTF-8");
            String content = IOUtils.toString(new FileInputStream(currentTrigerInfoFile), utfEncoding);
            
            if (content.length() < 10){
                return "Something wrong with newly generated new info file.";
            }
            
            Iterator iterator = viewModel.entrySet().iterator();
            
            while (iterator.hasNext()){
                Map.Entry mapEntry = (Map.Entry) iterator.next();
                try{
                    content = content.replaceAll("(?m)^" + mapEntry.getKey().toString() + "=.*$", mapEntry.getKey().toString() + "=" + (mapEntry.getValue() == null ? "" : mapEntry.getValue().toString()));
                }catch(Exception contentEx){
                    log.error("Exception in triggerTest: {}.", contentEx);
                }
            }
            
            IOUtils.write(content, new FileOutputStream(currentTrigerInfoFile), utfEncoding);
            
            String addCommand = svn + " add " + currentTrigerInfoFile.getPath();
            log.info(addCommand);
            output = executeCommand(addCommand);
            log.info(output);
            
            String commitCommand = svn + " commit --username autotest --non-interactive --trust-server-cert -m \"user:" + triggerUser + ";message:monitor_trigger\" " + currentTrigerInfoFile.getPath();
            log.info(commitCommand);
            output = executeCommand(commitCommand);
            log.info(output);
            
            return "";
        }catch (Exception ex){
            log.error("Exception in triggerTest: {}", ex);
            return ex.getMessage();
        }
    }
    
    private String executeCommand(String command) {

        StringBuilder output = new StringBuilder();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

        } catch (Exception ex) {
            log.error("Exception in executeCommand: {}", ex);
        }

        return output.toString();

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
