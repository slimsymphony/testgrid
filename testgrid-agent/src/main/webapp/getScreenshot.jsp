<%@page import="java.io.*"%><%@page import="org.apache.commons.io.*"%><%
response.addHeader( "Pragma", "public" );
response.addHeader( "Cache-Control", "max-age=86400" );
response.addHeader( "Content-Type", "image/png" );
String path = request.getParameter("path");
File file = new File( path );
//response.addHeader( "Content-Disposition", "Attachment;filename="+file.getName() );
response.addHeader( "Content-Length", String.valueOf( file.length() ) );
FileInputStream fin = null;
OutputStream os = null;
try{
	os = response.getOutputStream();
	fin = new FileInputStream(path);
	IOUtils.copy( fin, os );
	os.flush();
}catch(Exception ex){
	ex.printStackTrace();
}finally{
	IOUtils.closeQuietly( os );
	IOUtils.closeQuietly( fin );
}
%>