<%@page contentType="text/html" pageEncoding="UTF-8"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Logout</title>
</head>
<body>
	<%

        session.removeAttribute("username");
        session.removeAttribute("password");
        session.removeAttribute("userrole");
        session.invalidate();
        %>
	<h2>Logout successfully.</h2>

	<% response.sendRedirect("index.jsp"); %>
</body>
</html>

