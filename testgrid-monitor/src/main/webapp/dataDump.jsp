<%@page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.monitor.*"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<html>
<head>
<title>overall status</title>
</head>
<body>
<% for (String regionStr : RegionManager.getMQRegions()) { 
    MonitorCache monitorCache = RegionManager.getMQCache(regionStr);
%>
<table>
<tr><th>Tasks</th></tr>
<%for(Task task : monitorCache.getTasks()){%>
<tr><td><%=task.toString() %></td></tr>
<%} %>
<tr><th>Clients</th></tr>
<%for(Client client : monitorCache.getClients()){%>
<tr><td><%=client.toString() %></td></tr>
<%} %>
<tr><th>Agents</th></tr>
<%for(Agent agent : monitorCache.getAgents()){%>
<tr><td><%=agent.toString() %></td></tr>
<%} %>
<tr><th>Tests</th></tr>
<%for(Test test : monitorCache.getTests()){%>
<tr><td><%=test.toString() %></td></tr>
<%} %>
<tr><th>Devices</th></tr>
<%for(Device device : monitorCache.getDevices()){%>
<tr><td><%=device.toString() %></td></tr>
<%} %>
</table>
<% } %>
</body>
</html>