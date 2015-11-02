<%@page import="frank.incubator.testgrid.agent.*"%><%@page
	import="frank.incubator.testgrid.agent.device.*"%><%@page
	import="frank.incubator.testgrid.common.*"%><%@page
	import="frank.incubator.testgrid.common.model.*"%>
<%
AgentNode agent = (AgentNode)HttpServer.getAppRef();
DeviceManager dm = agent.getDm();
String operation = request.getParameter("op");
String id = request.getParameter("id");
try{
if("remove".equals(operation)){
	dm.removeDevice(dm.getDeviceById(id));
}else if( "release".equals(operation) ){
	dm.releaseDevices(dm.getDeviceById(id));
}else if( "maintain".equals(operation)){
	dm.setDeviceState(id, Device.DEVICE_MAINTAIN);
}
out.print("success");
}catch(Exception ex){
	response.sendError( 500 );
	out.print(ex.getMessage());	
}
%>