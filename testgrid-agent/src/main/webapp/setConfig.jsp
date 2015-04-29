<%@page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<%@page import="frank.incubator.testgrid.agent.*"%>
<%@page import="frank.incubator.testgrid.common.*"%>
<%@page import="java.util.*"%>
<%
	AgentNode agent = (AgentNode)HttpServer.getAppRef();
	Properties props = agent.getConfig();
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
</style>
<script type="text/javascript">
$(function(){
	$('#btnAdd').click(function(){
		var key = window.prompt("key");
		if(key == null || key == ''){
			alert("property can't be null");
			return false;
		}
			
		var val = window.prompt("value");
		if(val == null || val == ''){
			alert("property value can't be null");
			return false;
		}
		
		$.post("config.jsp",
				{
					"key":key,
					"value":val,
					"op":"add"
				},
				function(data,status){
					alert("Result: " + data + "\nStatus: " + status);
				}
			);
		
	});
});

function update(id){
	var val = $('#prop'+id).val();
	$.post("config.jsp",
		{
			"key":id,
			"value":val,
			"op":"update"
		},
		function(data,status){
			alert("Result: " + data + "\nStatus: " + status);
		}
	);
}

function del(id){
	$.post("config.jsp",
		{
			"key":id,
			"op":"delete"
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
		<button id="btnAdd">Add</button>
		<ul>
			<%
				for ( Object obj : props.keySet() ) {
			%>
			<li id="li<%=obj%>"><span id="span<%=obj%>"
				style="font-color: grey"><%= obj%></span> - <input id="prop<%=obj%>"
				type="text" value="<%=props.getProperty( ( String ) obj )%>" /> <input
				onclick="update('<%=obj%>')" type="button" value="update" /> <input
				onclick="del('<%=obj%>')" type="button" value="delete" /></li>
			<%
				}
			%>
		</ul>
	</div>
	<br />
	<br />
	<a href="index.jsp">back home</a>
	<br />
	<footer>
		<a href="solarsystem.html">Welcome to use test grid</a>
	</footer>
</body>
</html>