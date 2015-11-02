<%@page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<%@page import="frank.incubator.testgrid.agent.*"%>
<%@page import="frank.incubator.testgrid.common.*"%>
<%@page import="java.util.concurrent.atomic.AtomicInteger"%>
<%@page import="java.util.*"%>
<%@page import="java.sql.Timestamp"%>
<%@page import="java.io.*"%>
<%
	AgentNode agent = (AgentNode)HttpServer.getAppRef();
	AtomicInteger counter = new AtomicInteger(0);
	File screenHome = new File( agent.getWorkspace(), "screenshots" );
	Map<Device,Long> devices = agent.getDm().allDevices();
%>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="Agents of S.H.I.E.D :)">
<meta http-equiv="refresh" content="30" >
<script src="js/jquery-2.1.1.min.js" type="text/javascript"></script>
<style type="text/css">
body {
	background: #eeeeee;
	font: italic bold 12px/20px arial, sans-seri;
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

.topLabel {
	color: red;
	font-size: 12pt;
	font-family: "Courier New";
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
last refresh time:<%=new Date()%>
	<h1>
		Current Agent:
		<%= CommonUtils.getHostName()%> 
		Total Devices: <span style="color:red;font-weight:bold;"><%=devices.size() %></span>
	</h1>
	<div id="main">
		<button onclick="switchAll()">SWITCH</button>
		<div id="devices">
			<span style="font-size: 22pt; color: green;">Devices</span>
			<ul>
				<%for(Map.Entry<Device,Long> entry : devices.entrySet()){ 
					Device d = entry.getKey(); 
					String time = CommonUtils.convert(System.currentTimeMillis()- entry.getValue()); 
					String sn = d.getAttribute( Constants.DEVICE_SN );
					float memsize = 0f;
					if(d.getAttribute( Constants.DEVICE_MEM_SIZE )!= null)
					memsize =(float)(Math.round((float)((Integer)d.getAttribute( Constants.DEVICE_MEM_SIZE ))*10/(1000f*1000f)))/10f;
				%>
				<li><%= CommonUtils.format4Web( CommonUtils.renderToHtml( d, 0, counter ) ) %><br />
					<span>SyncInterval:<font color="orange"><%=time %>&nbsp;</font></span>
					<span>SN:</span><font color="red"><%=sn %>&nbsp;</font> <span>State:<strong><font
							color="red"><%=d.getStateString() %></font></strong></span>&nbsp;&nbsp;
					<%if(d.getAttribute( Constants.DEVICE_MANUFACTURER ) != null){ %>
					<span>Brand:<strong><font color="red"><%=d.getAttribute( Constants.DEVICE_MANUFACTURER ) %></font></strong></span>&nbsp;&nbsp;
					<%} %>
					<%if(d.getAttribute( Constants.DEVICE_PRODUCTCODE ) != null){ %>
					<span>Product:<strong><font color="red"><%=d.getAttribute( Constants.DEVICE_PRODUCTCODE ) %></font></strong></span>
					<%} %>
					<span>Platform:<strong><font color="red"><%=d.getAttribute( Constants.DEVICE_PLATFORM ) %></font></strong></span>&nbsp;&nbsp;
					<span>Platform-Version:<strong><font color="red"><%=d.getAttribute( Constants.DEVICE_PLATFORM_VERSION ) %></font></strong></span>&nbsp;&nbsp;
					<span>Resolution:<strong><font color="red"><%=d.getAttribute( Constants.DEVICE_RESOLUTION ) %></font></strong></span>&nbsp;&nbsp;
					<%if(d.getAttribute( Constants.DEVICE_DPI ) != null){ %>
					<span>DPI:<strong><font color="red"><%=d.getAttribute( Constants.DEVICE_DPI ) %></font></strong></span>&nbsp;&nbsp;
					<%} %>
					<span>WLAN IP:<strong><font color="red">
					<%if(d.getAttribute( Constants.DEVICE_IP_WLAN ) != null){ %>
					<%=d.getAttribute( Constants.DEVICE_IP_WLAN ) %>
					<%} else{%>Out of Wlan Service<%} %>
					</font></strong></span>&nbsp;&nbsp;
					<%if(memsize!=0f){ %>
					<span>Mem:<strong><font color="red"><%= memsize%>G</font></strong></span>&nbsp;&nbsp;					
					<!--<span>ProductName:<strong><font color="red"><%//d.getAttribute( Constants.DEVICE_PRODUCT_NAME ) %></font></strong></span>-->
					<%} %>
					<%if(d.getAttribute( Constants.DEVICE_IMEI ) != null){ %>
					<span>Imei:<strong><font color="red"><%=d.getAttribute( Constants.DEVICE_IMEI )%></font></strong></span>
					<%} %>
					<% if(d.getState() == Device.DEVICE_LOST){%><button
						onclick="removeDevice('<%=d.getId()%>')">Remove</button>
					<%} %>&nbsp; <% if(d.getState() == Device.DEVICE_RESERVED || d.getState() == Device.DEVICE_BUSY || d.getState() == Device.DEVICE_MAINTAIN ){%><button
						onclick="freeDevice('<%=d.getId()%>')">Release</button>
					<%} %> <% if(d.getState() != Device.DEVICE_LOST && d.getState() != Device.DEVICE_LOST_TEMP ){ %><button
						onclick="maintainDevice('<%=d.getId() %>')">Maintain</button>
					<%} %> <%  
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
				%></li>
				<%} %>
			</ul>
		</div>
		<div id="reservetests">
			<span style="font-size: 22pt; color: green;">Reserved Tests</span>
			<ul>
				<%for( Test t: agent.getReservedTests().keySet() ) {%>
				<li><%= CommonUtils.format4Web( CommonUtils.renderToHtml( t, 0, counter ) ) %></li>
				<%} %>
			</ul>
		</div>
		<div id="tests">
			<span style="font-size: 22pt; color: green;">Running Tests</span>
			<ul>
				<%for( Test t: agent.getRunningTests().keySet() ) {%>
				<li><%= CommonUtils.format4Web( CommonUtils.renderToHtml( t, 0, counter ) ) %></li>
				<%} %>
			</ul>
		</div>
		<div id="agent">
			<span style="font-size: 22pt; color: green;">Agent Status</span>
			<%// CommonUtils.format4Web( CommonUtils.renderToHtml( agent.getNodeStatus(), 0, counter ) ) %>
		</div>
	</div>
	<br />
	<br />
	<a href="setConfig.jsp">set config</a>
	<br />
	<a href="plugins.jsp">config plugins</a>
	<br />
	<a href="checkLog.jsp">check logs</a>
	<br />
	<footer>
		<a href="solarsystem.html">Welcome to use test grid</a>
	</footer>
</body>
</html>