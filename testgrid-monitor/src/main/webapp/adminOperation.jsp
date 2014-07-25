<%@page import="org.apache.commons.lang3.StringUtils"%>
<%@page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.monitor.*"%>
<%@page import="frank.incubator.testgrid.dm.*"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<%@page import="frank.incubator.testgrid.common.CommonUtils"%>
<%@page import="org.json.simple.*"%>
<%@page import="org.json.simple.parser.JSONParser"%>
<%@page import="java.util.HashMap"%>
<%@page import="java.util.Map"%>

<%
	String type = request.getParameter("type");
    String id = request.getParameter("id");
    String host = request.getParameter("host");
    String details = request.getParameter("details");
    String users = request.getParameter("users");
    String operation = request.getParameter("operation");
    String mqRegionStr = RegionManager.getCurrentRegion();
    AdminController adminControler = RegionManager.getControler(mqRegionStr);
    if (StringUtils.isEmpty(operation)){
        operation = "Release";
    }
    String properties = request.getParameter("properties");
    String responseMsg = "";
    
    Object userRoleAttr = session.getAttribute("userrole");
    String userRole = "";
    if (userRoleAttr != null){
        userRole = userRoleAttr.toString();
    }
    
    Object userNameAttr = session.getAttribute("username");
    String userName = "";
    if (userNameAttr != null){
        userName = userNameAttr.toString();
    }
    
    //General post handlers
    if (type.equals("region")){
        String currentRegion = request.getParameter("mqRegion");
        RegionManager.setCurrentRegion(currentRegion);
        return;
    }
    
    //Admin post handlers
    if (userRole.equals("ADMINISTRATOR")){
        if (type.equals("agent")) {
            if (operation.equals("SetMaintainMode") || operation.equals("SetNormalMode")){
                adminControler.setModeForAgent(host, operation);
            }else if (operation.equals("InjectProperties")){
                adminControler.injectPropertiesToAgent(host, properties);
            }
        }else if (type.equals("test")) {
            responseMsg = adminControler.cancelTestOnAgent(host, id);
        } else if (type.equals("device")){
            responseMsg = adminControler.releaseDeviceOnAgent(host, details);
        } else if (type.equals("modUsers")){

            JSONParser jsonParser = new JSONParser();
            JSONArray jsonUsers = (JSONArray)jsonParser.parse(users);

            UserManager userManager = new UserManagerImpl();
            for (Object o : jsonUsers){
                JSONObject jsonUser = (JSONObject) o;
                SysUser sysUser = new SysUser();
                sysUser.setUserName(jsonUser.get("username").toString());
                sysUser.setUserRole(UserRole.valueOf(jsonUser.get("userrole").toString()));
                userManager.updateUser(sysUser);
            }
            
        } else{
            responseMsg = "not supported yet.";
        }
    }else{
        responseMsg = "Prohibited. Either you have no admin privilege, or your login session has expired, please try logout and login again.";
    }
    
    if (responseMsg.isEmpty()) {
        responseMsg = "Operation is successful";
    } else {
        responseMsg = "Operation failed due to: " + responseMsg;
    }
%>

<%=responseMsg%>