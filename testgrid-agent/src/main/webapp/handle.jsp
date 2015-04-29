<%@page language="java" contentType="application/json; charset=utf-8" pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.common.*"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<%@page import="frank.incubator.testgrid.agent.*"%>
<%
String op = request.getParameter("op");
String sn = request.getParameter("sn");
String type = request.getParameter("type");
String owner = request.getParameter("owner");
AgentNode agent = (AgentNode)HttpServer.getAppRef();
boolean result = false;
if(sn != null){
	Device d = agent.getDm().getDeviceBy(Constants.DEVICE_SN, sn);
	if(d != null){
		synchronized(d){
			if("reserve".equals(op) && d.getState() == Device.DEVICE_FREE){
				d.setState(Device.DEVICE_RESERVED);
				if(type == null)
					type = "VNC";
				if(owner == null)
					owner = "User";
				d.setTaskStatus(type+"&"+owner);
				out.print("{\"success\":true}");
				result = true;
			}else if("release".equals(op) && d.getState() == Device.DEVICE_RESERVED){
				d.setState(Device.DEVICE_FREE);
				d.setTaskStatus("");
				out.print("{\"success\":true}");
				result = true;
			}
		}
	}
}
if(!result)
	out.print("{\"success\":false}");
%>