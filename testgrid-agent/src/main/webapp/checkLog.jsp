<%@page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.agent.*"%>
<%@page import="java.io.*"%>
<%@page import="frank.incubator.testgrid.common.*"%>
<%@page
	import="org.apache.commons.io.comparator.LastModifiedFileComparator"%>
<%@page import="java.util.*"%>
<%
	AgentNode agent = (AgentNode)HttpServer.getAppRef();
	File logFolder = new File(agent.getWorkspace().getParentFile(), "logs");
	File[] arr = logFolder.listFiles();
	Arrays.sort( arr, LastModifiedFileComparator.LASTMODIFIED_REVERSE );
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

#logContent {
	width: 90%;
	height: 800px;
	overflow: scroll;
	overflow-y: scroll;
	overflow-x: scroll;
	overflow: -moz-scrollbars-vertical;
	white-space: nowrap;
}
</style>
<script type="text/javascript">
$(function(){
	$('#btnOpen').click(function(){
		$.post("readFile.jsp",
			{
				"filename":$('#logSelector').val()
			},
			function(data,status){
				$('#logContent').val(data);
			}
		);
	});
	$('#iptWrap').click(function(){
		var $this = $(this);
	    if ($this.is(':checked')) {
	    	$('#logContent').css("white-space",'normal');
	    } else {
	    	$('#logContent').css("white-space",'nowarp');
	    }
	});
});

</script>
</head>
<body>
	<h1>
		Current Agent:
		<%= CommonUtils.getHostName()%></h1>
	<div id="main">
		<select id="logSelector">
			<% for( File f : arr ){ if(f.isFile()){%><option
				value='<%=f.getName() %>'><%=f.getName() %> > size:
				<%=f.length()/1024 %>k
			</option>
			<% }} %>
		</select> &nbsp;
		<button id="btnOpen">open</button>
		&nbsp; Wrap<input type="checkbox" id="iptWrap" /> <br />
		<textarea id="logContent"></textarea>
	</div>
	<br />
	<a href="index.jsp">back home</a>
	<br />
	<footer>
		<a href="solarsystem.html">Welcome to use test grid</a>
	</footer>
</body>
</html>