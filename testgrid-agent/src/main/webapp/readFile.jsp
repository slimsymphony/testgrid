<%@page language="java" contentType="text/html; charset=utf-8"
	pageEncoding="utf-8"%><%@page import="org.apache.commons.io.IOUtils"%><%@page
	import="frank.incubator.testgrid.agent.*"%><%@page import="java.io.*"%><%@page
	import="frank.incubator.testgrid.common.*"%>
<%
String filename = request.getParameter("filename");
AgentNode agent = (AgentNode)HttpServer.getAppRef();
File logFolder = new File(agent.getWorkspace().getParentFile(), "logs");
File file = new File(logFolder, filename);
if(file.exists() && file.isFile()){
	FileInputStream fin = null;
	try{
		fin = new FileInputStream( file );
		IOUtils.copy( fin, out );
	}catch(Exception e){
		out.print(e.getMessage());
	}finally{
		IOUtils.closeQuietly( fin );
	}
}else{
	out.print("File " + filename +" didn't exist in Log Directory.");
}
%>