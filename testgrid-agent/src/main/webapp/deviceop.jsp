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
	for( Device d : dm.listDevices() ){
		if( d.getId().equals(id) )
			dm.removeDevice( d );
	}
}else if( "release".equals(operation) ){
	for( Device d : dm.listDevices() ){
		if( d.getId().equals(id) ){
			d.setTaskStatus( "" );
			d.setState( 0 );
		}
	}
}else if( "maintain".equals(operation)){
	for( Device d : dm.listDevices() ){
		if( d.getId().equals(id) ){
			d.setState( Device.DEVICE_MAINTAIN );
			break;
		}
	}
}
out.print("success");
}catch(Exception ex){
	response.sendError( 500 );
	out.print(ex.getMessage());	
}
%>