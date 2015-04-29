<%@page contentType="text/html" pageEncoding="UTF-8"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Error Page</title>
</head>
<body>
	<h2>
		<%
            String type = request.getParameter("type");

            if (type.equals("loginError")){
                out.println("Invalid user or password, please try <a href='login.jsp'>login</a> again.");
            }else{
                out.println("Unexpected error happened, please contact support people.");
            }
        %>
	</h2>
</body>
</html>
