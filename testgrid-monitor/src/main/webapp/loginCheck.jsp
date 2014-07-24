<%@page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.monitor.utils.*"%>
<%@page import="frank.incubator.testgrid.dm.*"%>
<%@page import="java.util.List"%>
<%@page import="java.util.HashMap"%>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Login Authentication</title>
    </head>
    <body>
        <%
            String username=request.getParameter("username");
            String password=request.getParameter("password");
            
            LDAPUser ldapUser = null;
            
            try {
                LDAPServer ldapServer = new LDAPServer("147.243.4.80", 636, "cn=mpcisvc01,ou=systemusers,ou=accounts", "w%5]5sf6bVh2U)H%");
                LDAPUtil ldapUtil = new LDAPUtil(ldapServer);
                ldapUser = ldapUtil.authenticate(username, password);
            } catch (Exception e){
                ldapUser = null;
            }          

            if(ldapUser != null){
                session.setAttribute("username", ldapUser.getUsername());
                
                SysUser user = new SysUser();
                user.setUserName(ldapUser.getUsername());
                user.setUserRole(UserRole.ADMINISTRATOR);
                UserManager userManager = new UserManagerImpl();
                userManager.addUser(user);
                
                HashMap<String, String> condition = new HashMap<String, String>();
                condition.put("username", ldapUser.getUsername());
                List<SysUser> candidates = userManager.queryUsers(condition);
                
                session.setAttribute("userrole", candidates.get(0).getUserRole().toString());
                
                response.sendRedirect("index.jsp");
            }else{
               // response.sendRedirect("error.jsp?type=loginError");
               // temporary solution for no-ldap users. Allow all users to login and got admin privilege.
               	session.setAttribute("username", username );
            	session.setAttribute("userrole", UserRole.ADMINISTRATOR.name() );
            	response.sendRedirect("index.jsp");
            }
        %>
    </body>
</html>
