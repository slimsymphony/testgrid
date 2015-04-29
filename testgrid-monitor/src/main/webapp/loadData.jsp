<%@page import="org.apache.commons.lang3.StringUtils"%>
<%@page import="java.util.Arrays"%>
<%@page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.monitor.*"%>
<%@page import="frank.incubator.testgrid.dm.*"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<%@page import="frank.incubator.testgrid.common.CommonUtils"%>
<%@page import="java.util.Date"%>
<%@page import="java.util.List"%>
<%@page import="java.net.InetAddress"%>
<%@page import="org.json.simple.*"%>
<%@page import="org.json.simple.parser.JSONParser"%>

<%
    String action = request.getParameter("action");
    if (action == null || action.equals("")) {
        action = "Agents";
    }
    
    String filterValue = request.getParameter("filterValue");
    String mqRegionStr = request.getParameter("mqRegion");
    
    MonitorCache monitorCache = RegionManager.getMQCache(mqRegionStr);

    JSONObject overallJson = new JSONObject();
    JSONParser parser = new JSONParser();

    if (action.equals("Regions")){
        
        List<String> mqRegions = RegionManager.getMQRegions();
        String currentRegion = RegionManager.getCurrentRegion();
        
        JSONArray infoList = new JSONArray();
        boolean needDefaultSelect = true;
        if (StringUtils.isEmpty(currentRegion)){
            needDefaultSelect = true;
        }else{
            needDefaultSelect = false;
        }

        for (String mqRegion : mqRegions){
            JSONObject info = new JSONObject();
            String[] mqParams = mqRegion.split("\\*");
            if (mqParams.length < 2){
                continue;
            }
            info.put("id", mqRegion);
            info.put("text", mqParams[0]);
            if (needDefaultSelect){
                info.put("selected", true);
                needDefaultSelect = false;
            }else if (mqRegion.equals(currentRegion)) {
                info.put("selected", true);
            }
            infoList.add(info);
        }
        out.println(infoList);  
        return;
        
    }else if (action.equals("Agents")) {
        JSONArray recordList = new JSONArray();
        int nodeCounter = 1; 
        for (Agent agent : monitorCache.getAgents()) {
            //Add agent node
            JSONObject json = new JSONObject();
            json.put("id", nodeCounter);
            json.put("name", agent.getId());
            if (StringUtils.isEmpty(agent.getIp()) && !StringUtils.isEmpty(agent.getHost())) {
                try {
                    InetAddress address = InetAddress.getByName(agent.getHost());
                    agent.setIp(address.getHostAddress());
                } catch (Exception e) {
                    agent.setIp("NA");
                }
            }
            json.put("ip", agent.getIp());
            json.put("host", agent.getHost());
            json.put("hangtime", agent.calcTimeNotUpdatedTillNow());
            json.put("status", agent.getStatus().name());
            json.put("load", agent.getLoadPercentage());
            json.put("details", agent.toString());
            json.put("iconCls", "icon-agent");
            json.put("nodetype", "agent");
            recordList.add(json);

            //Add device categories
            JSONObject inUseDevicesJson = new JSONObject();
            inUseDevicesJson.put("id", nodeCounter + 1);
            inUseDevicesJson.put("name", "InUseDevices");
            inUseDevicesJson.put("_parentId", nodeCounter);
            inUseDevicesJson.put("iconCls", "icon-devices-busy");
            inUseDevicesJson.put("state", "closed");
            recordList.add(inUseDevicesJson);

            JSONObject idleDevicesJson = new JSONObject();
            idleDevicesJson.put("id", nodeCounter + 2);
            idleDevicesJson.put("name", "IdleDevices");
            idleDevicesJson.put("_parentId", nodeCounter);
            idleDevicesJson.put("iconCls", "icon-devices-idle");
            idleDevicesJson.put("state", "closed");
            recordList.add(idleDevicesJson);

            //Associate device.
            int deviceCounter = 1;
            for (Device device : agent.getDevices()) {
                JSONObject deviceJson = new JSONObject();
                deviceJson.put("id", nodeCounter + 2 + deviceCounter);
                deviceJson.put("name", device.getAttribute("sn"));
                deviceJson.put("status", device.getStateString());
                deviceJson.put("details", device.toString());
                deviceJson.put("nodetype", "device");
                //deviceJson.put("host", json.get("host"));
                deviceJson.put("hangtime", device.calcTimeNotUpdatedTillNow());
                if (device.getState() == Device.DEVICE_BUSY || device.getState() == Device.DEVICE_RESERVED) {
                    deviceJson.put("_parentId", nodeCounter + 1);
                    inUseDevicesJson.put("state", "opened");
                    deviceJson.put("iconCls", "icon-device-inuse");
                } else {
                    deviceJson.put("_parentId", nodeCounter + 2);
                    //idleDevicesJson.put("state", "opened");
                    deviceJson.put("iconCls", "icon-device-idle");
                }
                recordList.add(deviceJson);
                deviceCounter++;

                //System.out.println(device.getStatus());
                //Associate test to device.
                if (device.getTaskStatus() != null && device.getTaskStatus().length() > 0) {
                    if (device.getTaskStatus() == null) {
                        continue;
                    } else if (device.getTaskStatus().startsWith("Test_")) {

                        //It is a test ID.
                        Test test = monitorCache.findTestById(device.getTaskStatus());

                        JSONObject testJson = new JSONObject();
                        testJson.put("id", nodeCounter + 2 + deviceCounter);
                        testJson.put("name", device.getTaskStatus());
                        testJson.put("iconCls", "icon-test");
                        testJson.put("_parentId", nodeCounter + 2 + deviceCounter - 1);
                        //testJson.put("host", json.get("host"));
                        testJson.put("nodetype", "test");

                        if (test != null) {
                            //More info if found from cache.
                            testJson.put("status", test.getPhase().toString());
                            testJson.put("hangtime", test.calcTimeNotUpdatedTillNow());
                            testJson.put("details", test.toString());
                        }
                        recordList.add(testJson);
                    }
                    deviceCounter++;
                }
            }

            nodeCounter += (2 + deviceCounter);
        }

        overallJson.put("rows", recordList);
    } else if (action.equals("Devices")) {
        JSONArray recordList = new JSONArray();
        int recordCounter = 0;
        for (Device device : monitorCache.getDevices()) {
            recordCounter++;
            //Add agent node
            JSONObject deviceJson = new JSONObject();
            deviceJson.put("id", recordCounter);
            deviceJson.put("name", device.getAttribute("sn"));
            deviceJson.put("nodetype", "device");
            deviceJson.put("imei", device.getAttribute("imei"));
            deviceJson.put("tag", device.getAttribute("tag"));
            deviceJson.put("productcode", device.getAttribute("productcode"));
            deviceJson.put("productname", device.getAttribute("productname"));
            deviceJson.put("rmcode", device.getAttribute("rmcode"));
            deviceJson.put("hw", device.getAttribute("hw"));
            deviceJson.put("sn", device.getAttribute("sn"));
            deviceJson.put("status", device.getStateString());
            deviceJson.put("loadedtask", device.getTaskStatus());
            deviceJson.put("hangtime", device.calcTimeNotUpdatedTillNow());
            deviceJson.put("state", "closed");
            if (device.getState() == Device.DEVICE_BUSY || device.getState() == Device.DEVICE_RESERVED) {
                deviceJson.put("iconCls", "icon-device-inuse");
            } else {
                deviceJson.put("iconCls", "icon-device-idle");
            }
            deviceJson.put("details", device.toString());
            recordList.add(deviceJson);

            int parentDeviceCounter = recordCounter;
            for (Agent agent : monitorCache.getAgents()) {
                if (agent.getDevices() != null && agent.getDevices().contains(device)) {
                    recordCounter++;
                    JSONObject agentJson = new JSONObject();
                    agentJson.put("_parentId", parentDeviceCounter);
                    agentJson.put("id", recordCounter);
                    agentJson.put("nodetype", "agent");
                    agentJson.put("name", agent.getId());
                    agentJson.put("hangtime", agent.calcTimeNotUpdatedTillNow());
                    if (agent.getStatus() != null) {
                        agentJson.put("status", agent.getStatus().toString());
                    }
                    if (StringUtils.isEmpty(agent.getIp()) && !StringUtils.isEmpty(agent.getHost())) {
                        try {
                            InetAddress address = InetAddress.getByName(agent.getHost());
                            agent.setIp(address.getHostAddress());
                        } catch (Exception e) {
                            agent.setIp("NA");
                        }
                    }
                    agentJson.put("ip", agent.getIp());
                    agentJson.put("host", agent.getHost());
                    agentJson.put("iconCls", "icon-agent");
                    agentJson.put("details", agent.toString());
                    recordList.add(agentJson);
                }
            }
        }

        overallJson.put("total", recordCounter);
        overallJson.put("rows", recordList);
    } else if (action.equals("Tasks")) {
        JSONArray recordList = new JSONArray();
        int recordCounter = 0;
        for (Task task : monitorCache.getTasks()) {
            recordCounter++;
            //Add task node
            JSONObject taskJson = new JSONObject();
            taskJson.put("id", recordCounter);
            taskJson.put("name", task.getId());
            taskJson.put("owner", task.getTaskOwner());
            taskJson.put("nodetype", "task");
            if (task.getStartTime() != 0L){
                taskJson.put("starttime", CommonUtils.parseTimestamp(task.getStartTime()).toString());
            }else{
                taskJson.put("starttime", "NA");
            }
            if (task.getEndTime() != 0L){
                taskJson.put("endtime", CommonUtils.parseTimestamp(task.getEndTime()).toString());
            }else{
                 taskJson.put("endtime", "NA");
            }
            if (task.getPhase() != null) {
                taskJson.put("status", task.getPhase().toString());
            }
            taskJson.put("iconCls", "icon-task");
            taskJson.put("hangtime", task.calcTimeNotUpdatedTillNow());
            taskJson.put("details", task.toString());
            recordList.add(taskJson);

            //Add test node if there are
            int parentTaskCounter = recordCounter;
            for (Test test : monitorCache.getTests()) {
                if (test.getTaskID() != null && test.getTaskID().equals(task.getId())) {
                    recordCounter++;
                    JSONObject testJson = new JSONObject();
                    testJson.put("_parentId", parentTaskCounter);
                    testJson.put("id", recordCounter);
                    testJson.put("name", test.getId());
                    testJson.put("nodetype", "test");
                    testJson.put("hangtime", test.calcTimeNotUpdatedTillNow());
                    if (test.getPhase() != null) {
                        testJson.put("status", test.getPhase().toString());
                    }
                    if (!StringUtils.isEmpty(test.getUrl())){
                        taskJson.put("url", test.getUrl());
                    }
                    testJson.put("iconCls", "icon-test");
                    testJson.put("state", "closed");
                    testJson.put("details", test.toString());
                    recordList.add(testJson);

                    //Add running device if there is.
                    int parentTestCounter = recordCounter;
                    for (Device device : monitorCache.getDevices()) {
                        if (device.getTaskStatus() != null && device.getTaskStatus().equals(test.getId())) {
                            recordCounter++;
                            JSONObject deviceJson = new JSONObject();
                            deviceJson.put("_parentId", parentTestCounter);
                            deviceJson.put("id", recordCounter);
                            deviceJson.put("name", device.getId());
                            deviceJson.put("nodetype", "device");
                            deviceJson.put("hangtime", device.calcTimeNotUpdatedTillNow());
                            deviceJson.put("status", device.getStateString());
                            deviceJson.put("iconCls", "icon-device-inuse");
                            deviceJson.put("details", device.toString());
                            recordList.add(deviceJson);

                            //Add associated agent if there is.
                            int parentDeviceCounter = recordCounter;
                            for (Agent agent : monitorCache.getAgents()) {
                                if (agent.getDevices().contains(device)) {
                                    recordCounter++;
                                    JSONObject agentJson = new JSONObject();
                                    agentJson.put("_parentId", parentDeviceCounter);
                                    agentJson.put("id", recordCounter);
                                    agentJson.put("name", agent.getId());
                                    agentJson.put("nodetype", "agent");
                                    agentJson.put("hangtime", agent.calcTimeNotUpdatedTillNow());
                                    if (agent.getStatus() != null) {
                                        agentJson.put("status", agent.getStatus().toString());
                                    }
                                    if (StringUtils.isEmpty(agent.getIp()) && !StringUtils.isEmpty(agent.getHost())) {
                                        try {
                                            InetAddress address = InetAddress.getByName(agent.getHost());
                                            agent.setIp(address.getHostAddress());
                                        } catch (Exception e) {
                                            agent.setIp("NA");
                                        }
                                    }
                                    agentJson.put("ip", agent.getIp());
                                    agentJson.put("host", agent.getHost());
                                    agentJson.put("iconCls", "icon-agent");
                                    agentJson.put("details", agent.toString());
                                    recordList.add(agentJson);
                                }
                            }
                        }
                    }
                }
            }
        }

        overallJson.put("total", recordCounter);
        overallJson.put("rows", recordList);
    } else if (action.equals("Clients")) {
        JSONArray recordList = new JSONArray();
        int recordCounter = 0;
        for (Client client : monitorCache.getClients()) {
            recordCounter++;
            //Add agent node
            JSONObject clientJson = new JSONObject();
            clientJson.put("id", recordCounter);
            clientJson.put("name", client.getId());
            clientJson.put("nodetype", "client");
            clientJson.put("host", client.getHost());
            clientJson.put("iconCls", "icon-client");
            if (client.getStatus() != null) {
                clientJson.put("status", client.getStatus().toString());
            }
            if (!StringUtils.isEmpty(client.getHost())) {
                try {
                    InetAddress address = InetAddress.getByName(client.getHost());
                    clientJson.put("ip", address.getHostAddress());
                } catch (Exception e) {
                    clientJson.put("ip", "NA");
                }
            }
            clientJson.put("hangtime", client.calcTimeNotUpdatedTillNow());
            clientJson.put("details", client.toString());
            recordList.add(clientJson);

            int parentClientCounter = recordCounter;
            for (Task task : monitorCache.getTasks()) {
                if (client.getTaskId() != null && client.getTaskId().equals(task.getId())) {

                    recordCounter++;
                    //Add task node
                    JSONObject taskJson = new JSONObject();
                    taskJson.put("id", recordCounter);
                    taskJson.put("_parentId", parentClientCounter);
                    taskJson.put("name", task.getId());
                    taskJson.put("nodetype", "task");
                    taskJson.put("owner", task.getTaskOwner());
                    if (task.getPhase() != null) {
                        taskJson.put("status", task.getPhase().toString());
                    }
                    taskJson.put("iconCls", "icon-task");
                    taskJson.put("hangtime", task.calcTimeNotUpdatedTillNow());
                    taskJson.put("details", task.toString());
                    recordList.add(taskJson);

                    //Add test node if there are
                    int parentTaskCounter = recordCounter;
                    for (Test test : monitorCache.getTests()) {
                        if (test.getTaskID() != null && test.getTaskID().equals(task.getId())) {
                            recordCounter++;
                            JSONObject testJson = new JSONObject();
                            testJson.put("_parentId", parentTaskCounter);
                            testJson.put("id", recordCounter);
                            testJson.put("name", test.getId());
                            testJson.put("nodetype", "test");
                            testJson.put("hangtime", test.calcTimeNotUpdatedTillNow());
                            if (test.getPhase() != null) {
                                testJson.put("status", test.getPhase().toString());
                            }
                            testJson.put("iconCls", "icon-test");
                            testJson.put("state", "closed");
                            testJson.put("details", test.toString());
                            recordList.add(testJson);
                        }

                        //Add running device if there is.
                        int parentTestCounter = recordCounter;
                        for (Device device : monitorCache.getDevices()) {
                            if (device.getTaskStatus() != null && device.getTaskStatus().equals(test.getId())) {
                                recordCounter++;
                                JSONObject deviceJson = new JSONObject();
                                deviceJson.put("_parentId", parentTestCounter);
                                deviceJson.put("id", recordCounter);
                                deviceJson.put("name", device.getId());
                                deviceJson.put("nodetype", "device");
                                deviceJson.put("hangtime", device.calcTimeNotUpdatedTillNow());
                                deviceJson.put("status", device.getStateString());
                                deviceJson.put("iconCls", "icon-device-inuse");
                                deviceJson.put("details", device.toString());
                                recordList.add(deviceJson);

                                //Add associated agent if there is.
                                int parentDeviceCounter = recordCounter;
                                for (Agent agent : monitorCache.getAgents()) {
                                    if (agent.getDevices().contains(device)) {
                                        recordCounter++;
                                        JSONObject agentJson = new JSONObject();
                                        agentJson.put("_parentId", parentDeviceCounter);
                                        agentJson.put("id", recordCounter);
                                        agentJson.put("name", agent.getId());
                                        agentJson.put("nodetype", "agent");
                                        agentJson.put("hangtime", agent.calcTimeNotUpdatedTillNow());
                                        if (agent.getStatus() != null) {
                                            agentJson.put("status", agent.getStatus().toString());
                                        }
                                        if (StringUtils.isEmpty(agent.getIp()) && !StringUtils.isEmpty(agent.getHost())) {
                                            try {
                                                InetAddress address = InetAddress.getByName(agent.getHost());
                                                agent.setIp(address.getHostAddress());
                                            } catch (Exception e) {
                                                agent.setIp("NA");
                                            }
                                        }
                                        agentJson.put("ip", agent.getIp());
                                        agentJson.put("host", agent.getHost());
                                        agentJson.put("iconCls", "icon-agent");
                                        agentJson.put("details", agent.toString());
                                        recordList.add(agentJson);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        overallJson.put("total", recordCounter);
        overallJson.put("rows", recordList);
    } else if (action.equals("Tests")) {
        JSONArray recordList = new JSONArray();
        int recordCounter = 0;
        for (Test test : monitorCache.getTests()) {
            recordCounter++;
            //Add agent node
            JSONObject testJson = new JSONObject();
            testJson.put("id", recordCounter);
            testJson.put("name", test.getId());
            testJson.put("nodetype", "test");
            testJson.put("url", test.getUrl());
            if (test.getStartTime() != 0L){
                testJson.put("starttime", CommonUtils.parseTimestamp(test.getStartTime()).toString());
            }else{
                testJson.put("starttime", "NA");
            }
            if (test.getEndTime() != 0L){
                testJson.put("endtime", CommonUtils.parseTimestamp(test.getEndTime()).toString());
            }else{
                 testJson.put("endtime", "NA");
            }
            testJson.put("status", test.getPhase().toString());
            testJson.put("hangtime", test.calcTimeNotUpdatedTillNow());
            testJson.put("iconCls", "icon-test");
            testJson.put("state", "closed");
            testJson.put("details", test.toString());
            recordList.add(testJson);

            int parentTestCounter = recordCounter;
            for (Task task : monitorCache.getTasks()) {
                if (task.getTestsuite() != null && task.getTestsuite().contains(test)) {
                    recordCounter++;
                    //Add task node
                    JSONObject taskJson = new JSONObject();
                    taskJson.put("id", recordCounter);
                    taskJson.put("_parentId", parentTestCounter);
                    taskJson.put("name", task.getId());
                    taskJson.put("nodetype", "task");
                    taskJson.put("owner", task.getTaskOwner());
                    if (task.getPhase() != null) {
                        taskJson.put("status", task.getPhase().toString());
                    }
                    taskJson.put("iconCls", "icon-task");
                    taskJson.put("hangtime", task.calcTimeNotUpdatedTillNow());
                    taskJson.put("details", task.toString());
                    recordList.add(taskJson);
                }
            }
        }

        overallJson.put("total", recordCounter);
        overallJson.put("rows", recordList);
    } else if (action.equals("Users")) {
        JSONArray recordList = new JSONArray();
        int recordCounter = 0;

        UserManager userManager = new UserManagerImpl();
        List<SysUser> users = userManager.queryUsers(null);

        for (SysUser user : users) {
            recordCounter++;
            //Add user record
            JSONObject json = new JSONObject();
            json.put("username", user.getUserName());
            json.put("userrole", (user.getUserRole() == null ? "" : user.getUserRole().toString()));
            recordList.add(json);
        }

        overallJson.put("total", recordCounter);
        overallJson.put("rows", recordList);
    } else if (action.equals("UserRoles")) {
        JSONArray recordList = new JSONArray();

        List<UserRole> userRoles = Arrays.asList(UserRole.values());

        for (UserRole userRole : userRoles) {
            JSONObject json = new JSONObject();
            json.put("userrole", userRole.toString());
            recordList.add(json);
        }

        out.println(recordList);
    }

    if (!action.equals("UserRoles")) {
        
        if (!StringUtils.isEmpty(filterValue)){     
            JSONArray recordList = (JSONArray)overallJson.get("rows");
            JSONArray recordFilteredList = new JSONArray();
            JSONArray recordFilteredGroup = new JSONArray();

            boolean foundFilterValue = false;
            for (Object object : recordList){
                JSONObject jsonObject = (JSONObject)object;
                //System.out.println(jsonObject.get("_parentId"));
                if (jsonObject.get("_parentId") == null){
                    if (foundFilterValue){
                        recordFilteredList.addAll(recordFilteredGroup);
                    }
                    foundFilterValue = false;
                    recordFilteredGroup.clear();
                }
                
                recordFilteredGroup.add(jsonObject);
                
                String details = (String)jsonObject.get("details");
                jsonObject.put("details", "");
                
                if (jsonObject.toJSONString().contains(filterValue)){
                    //System.out.println(jsonObject.toJSONString());
                    foundFilterValue = true;
                }
                
                jsonObject.put("details", details);
            }
            
            //For the last group
            if (foundFilterValue){
                recordFilteredList.addAll(recordFilteredGroup);
            }
            
            overallJson.put("rows", recordFilteredList);
            
        }
        
        out.println(overallJson);

    }
%>


