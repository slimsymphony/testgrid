<%@page import="com.google.gson.reflect.TypeToken"%><%@page import="java.util.concurrent.TimeUnit"%><%@page import="java.util.Map"%><%@page import="frank.incubator.testgrid.agent.*"%><%@page import="frank.incubator.testgrid.common.*"%><%@page import="frank.incubator.testgrid.agent.plugin.*"%><%@page import="frank.incubator.testgrid.common.plugin.*"%><%AgentNode agent = (AgentNode)HttpServer.getAppRef();
String pluginName = request.getParameter("pluginName");
AbstractAgentPlugin plugin = (AbstractAgentPlugin)PluginManager.getPlugin( pluginName );
String op = request.getParameter("op");
try{
	if( "start".equals(op) ){
		if( plugin != null ) {
			switch( plugin.getState() ) {
				case TestGridPlugin.IDLE:
					plugin.start();
					break;
				case TestGridPlugin.STOPPED:
					PluginManager.initialize( agent, plugin.getName() );
					break;
			}
		}else {
			PluginManager.initialize( agent, pluginName );
		}
	}else if( "stop".equals(op) ){
		if( plugin != null )
			plugin.suspend();
	}else if( "reconfig".equals(op) ){
		Map<String,Object> atts = CommonUtils.fromJson( request.getParameter("atts"), new TypeToken<Map<String,Object>>(){}.getType( ) );
		for( String att : atts.keySet() ) {
			plugin.getAttributes().put( att, atts.get( att ) );
		}
	}else if( "changeSchedule".equals(op) ){
		if( plugin != null )
			plugin.changeSchedule( CommonUtils.parseLong( request.getParameter("delay"), 60 ), TimeUnit.SECONDS );
	}else if( "plugout".equals(op) ){
		if( plugin != null ) 
			plugin.deactive();
	}
	response.setStatus( 200 );
	out.print("success");
}catch(Exception ex){
	response.sendError( 500, ex.getMessage() );
}%>
