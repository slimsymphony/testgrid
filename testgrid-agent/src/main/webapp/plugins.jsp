<%@page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<%@page import="frank.incubator.testgrid.agent.*"%>
<%@page import="frank.incubator.testgrid.common.*"%>
<%@page import="frank.incubator.testgrid.common.plugin.*"%>
<%@page import="frank.incubator.testgrid.agent.plugin.*"%>
<%@page import="java.util.*"%>
<%
Map<String, PluginManager.PluginDescriptor> pds = PluginManager.scanPlugins();
%>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="Agents of S.H.I.E.D :)">
<script src="js/jquery-2.1.1.min.js" type="text/javascript"></script>
<style type="text/css">
body {
	background: #eeeeee;
	font: italic bold 12px/20px arial, sans-seri;
}

.topLabel {
	color: red;
	font-size: 12pt;
	font-family: "Courier New";
}
textarea{
	width: 60%;
height: 80px;
overflow: scroll; 
overflow-y: scroll; 
overflow-x: scroll; 
overflow:-moz-scrollbars-vertical;
}
</style>
<script type="text/javascript">
$(function(){	
});

function plugout(id){
	$.post("pluginop.jsp",
		{
			"pluginName":id,
			"op":"plugout"
		},
		function(data,status){
			alert("Result: " + data + "\nStatus: " + status);
			if( status == 'success' ){
				location.reload();
			}
		}
	);
}

function stop(id){
	$.post("pluginop.jsp",
		{
			"pluginName":id,
			"op":"stop"
		},
		function(data,status){
			alert("Result: " + data + "\nStatus: " + status);
		}
	);
}

function start(id){
	$.post("pluginop.jsp",
		{
			"pluginName":id,
			"op":"start"
		},
		function(data,status){
			alert("Result: " + data + "\nStatus: " + status);
			if( status == 'success' ){
				location.reload();
			}
		}
	);
}

function reconfig(id){
	var atts = $('#atts'+id).val();
	$.post("pluginop.jsp",
		{
			"pluginName":id,
			"op":"reconfig",
			"atts":atts
		},
		function(data,status){
			alert("Result: " + data + "\nStatus: " + status);
		}
	);
}

function changeSchedule(id){
	var delay = $('#delay'+id).val();
	$.post("pluginop.jsp",
		{
			"pluginName":id,
			"op":"changeSchedule",
			"delay":delay*1,
		},
		function(data,status){
			alert("Result: " + data + "\nStatus: " + status);
		}
	);
}
</script>
</head>
<body>
	<h1>
		Current Agent:
		<%= CommonUtils.getHostName()%></h1>
	<div id="main">
		<span>Available Plugins</span>
		<ul>
			<%for( PluginManager.PluginDescriptor pd : pds.values( ) ) {%>
			<li><%= pd.getPluginName() %> <input type="button" value="enable" onclick="start('<%=pd.getPluginName() %>')" /></li>
			<%} %>
		</ul>
		<ul>
			<%
			AbstractAgentPlugin<?> plugin = null;
			for ( String pn : PluginManager.plugins.keySet(  ) ) {
				plugin = (AbstractAgentPlugin<?>)PluginManager.plugins.get( pn );
			%>
			<li id="li<%=pn%>">
				<span id="span<%=pn%>" style="color: blue;font-size:16pt;"><%= pn%></span> 
				<ul>
					<% for( PluginPermission pp : plugin.getPermissions() ){ %>
					<li><%= pp %></li>
					<% } %>
				</ul>
				<span>Delay: </span><input id="delay<%= pn %>" value="<%= plugin.getDelay()%>" /><br/>
				<span>User-Defined:</span><br/>
				<textarea id="atts<%=pn%>"><%=CommonUtils.toJson( plugin.getAttributes() ) %></textarea><br/>
				<input onclick="start('<%=pn%>')" type="button" value="start" /> 
				<input onclick="stop('<%=pn%>')" type="button" value="suspend" />
				<input onclick="reconfig('<%=pn%>')" type="button" value="reconfig" />
				<input onclick="changeSchedule('<%=pn%>')" type="button" value="change Schedule" />
				<input onclick="plugout('<%=pn%>')" type="button" value="deactive" />
			</li>
			<%
			}
			%>
		</ul>
	</div>
	<br />
	<br />
	<a href="index.jsp">back home</a><br/>
	<footer>
		<a href="solarsystem.html">Welcome to use test grid</a>
	</footer>
</body>
</html>