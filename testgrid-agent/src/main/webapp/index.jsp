<%@page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<%@page import="frank.incubator.testgrid.agent.*"%>
<%@page import="frank.incubator.testgrid.common.*"%>
<%@page import="java.util.concurrent.atomic.AtomicInteger"%>
<%@page import="java.util.*"%>
<%@page import="java.io.*"%>
<%
	AgentNode agent = (AgentNode)HttpServer.getAppRef();
	AtomicInteger counter = new AtomicInteger(0);
	File screenHome = new File( agent.getWorkspace(), "screenshots" );
%>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="Agents of S.H.I.E.D :)">
<script src="js/jquery-2.1.1.min.js" type="text/javascript"></script>
<style type="text/css">
body{
	background: #eeeeee;
	font:italic bold 12px/20px arial,sans-seri;
}
.level0 {
	background: #7ecadb;
}
.level1 {
}
.level2 {
}
.level3 {
}
.keylevel0 {
	background: #a78587;
}
.keylevel1 {
	background: #6370c9;
}
.keylevel2 {
	background: #aabb71;
}
.keylevel3 {
}
.vallevel0 {
}
.vallevel1 {
}
.vallevel2 {
}
.vallevel3 {
}
.topLabel{
 color: red;
 font-size: 12pt;
 font-family:"Courier New";
}
</style>
<script type="text/javascript">
	function switcher(id){
		var node = document.getElementById(id);
		if( node.style.display == 'none' ){ 
			node.style.display = 'block'; 
		} else { 
			node.style.display = 'none'; 
		}
	}
	var current = "none";
	function switchAll(){
		var divs = document.getElementsByTagName("div");
		if(current == 'none')
			current = "block";
		else
			current = "none";
		
		for( div in divs ){
			if( !divs[div].className || divs[div].className == '' )
				continue;
			divs[div].style.display = current;
		}
	}
	function removeDevice( did ){
		$.post("deviceop.jsp",
				{
					"id":did,
					"op":"remove"
				},
				function(data,status){
					if( status == 'success' ){
						window.location.reload();
					}else{
						alert("Result: " + data + "\nStatus: " + status);
					}
				}
		);
	}
	function freeDevice( did ){
		$.post("deviceop.jsp",
				{
					"id":did,
					"op":"release"
				},
				function(data,status){
					if( status == 'success' ){
						window.location.reload();
					}else{
						alert("Result: " + data + "\nStatus: " + status);
					}
				}
		);
	}
	
	function maintainDevice(did){
		$.post("deviceop.jsp",
				{
					"id":did,
					"op":"maintain"
				},
				function(data,status){
					if( status == 'success' ){
						window.location.reload();
					}else{
						alert("Result: " + data + "\nStatus: " + status);
					}
				}
		);
	}
</script>
</head>
<body>
<h1>Current Agent: <%= CommonUtils.getHostName()%></h1>
<div id="main">
	<button onclick="switchAll()">SWITCH</button>
	<div id="devices">
		<span style="font-size:22pt;color:green;">Devices</span>
		<ul>
			<%for(Device d: agent.getDm().listDevices()){ String sn = d.getAttribte( Constants.DEVICE_SN );%>
			<li><%= CommonUtils.format4Web( CommonUtils.renderToHtml( d, 0, counter ) ) %><br/>
				<span>SN:</span><font color="red"><%=sn %>&nbsp;</font>
				<span>State:<strong><font color="red"><%=d.getStateString() %></font></strong></span>&nbsp;&nbsp;&nbsp;&nbsp;
				<span>ProductName:<strong><font color="red"><%=d.getAttribte( Constants.DEVICE_PRODUCT_NAME ) %></font></strong></span>
				<span>SimCount:<strong><font color="red"><%=d.getAttribte( "simcount" )%></font></strong></span>
				<span>Imei:<strong><font color="red"><%=d.getAttribte( Constants.DEVICE_IMEI )%></font></strong></span>
				<% if(d.getState() == Device.DEVICE_LOST){%><button onclick="removeDevice('<%=d.getId()%>')">Remove</button><%} %>&nbsp;
				<% if(d.getState() == Device.DEVICE_RESERVED || d.getState() == Device.DEVICE_BUSY || d.getState() == Device.DEVICE_MAINTAIN ){%><button onclick="freeDevice('<%=d.getId()%>')">Release</button><%} %>
				<% if(d.getState() != Device.DEVICE_LOST && d.getState() != Device.DEVICE_LOST_TEMP ){ %><button onclick="maintainDevice('<%=d.getId() %>')">Maintain</button><%} %>
				<%  
				File folder = new File( screenHome, sn );
				File screenshot = CommonUtils.getLastmodifiedFileInFolder( folder, new FileFilter(){
					public  boolean accept(File pathname){
						if( pathname.getName().toLowerCase().endsWith( "png" ) ) {
							return true;
						}
						return false;
					}
				} );
				if( screenshot != null && screenshot.exists() ){
					out.print( "<img width='120px' src='getScreenshot.jsp?path=" + screenshot.getAbsolutePath() +"' />" );
				}
				%>
			</li>
			<%} %>
		</ul>
	</div>
	<div id="reservetests">
		<span style="font-size:22pt;color:green;">Reserved Tests</span>
		<ul>
			<%for( Test t: agent.getReservedTests().keySet() ) {%>
			<li><%= CommonUtils.format4Web( CommonUtils.renderToHtml( t, 0, counter ) ) %></li>
			<%} %>
		</ul>
	</div>
	<div id="tests">
		<span style="font-size:22pt;color:green;">Running Tests</span>
		<ul>
			<%for( Test t: agent.getRunningTests().keySet() ) {%>
			<li><%= CommonUtils.format4Web( CommonUtils.renderToHtml( t, 0, counter ) ) %></li>
			<%} %>
		</ul>
	</div>
	<div id="agent">
	<span style="font-size:22pt;color:green;">Agent Status</span>
		<%= CommonUtils.format4Web( CommonUtils.renderToHtml( agent.getNodeStatus(), 0, counter ) ) %>
	</div>
</div>
<br/>
<br/>
<a href="setConfig.jsp">set config</a><br/>
<a href="plugins.jsp">config plugins</a><br/>
<a href="checkLog.jsp">check logs</a><br/>
<footer><a href="solarsystem.html">Welcome to use test grid</a></footer>
</body>
</html>