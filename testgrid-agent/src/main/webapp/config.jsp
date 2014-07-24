<%@page import="frank.incubator.testgrid.agent.*"%><%@page import="frank.incubator.testgrid.common.*"%><%
AgentNode agent = (AgentNode)HttpServer.getAppRef();
String operation = request.getParameter("op");
String key = request.getParameter("key");
String val = request.getParameter("value");
try{
if("update".equals(operation)){
	if( AgentNode.getConfig( key ) != null )
		agent.setConfigItem( key, val );
}else if("delete".equals(operation)){
	agent.getConfig().remove( key );
}else if("add".equals(operation)){
	agent.setConfigItem( key, val );
}
	out.print("true");
}catch(Exception ex){
	out.print(ex.getMessage());	
}
%>