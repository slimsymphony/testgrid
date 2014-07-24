<%@page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<%@page import="frank.incubator.testgrid.agent.*"%>
<%@page import="frank.incubator.testgrid.common.*"%>
<%@page import="java.util.*"%>
<%
	((AgentNode)HttpServer.getAppRef()).stopHttpService();
	out.println("Ok, Now you have stopped the HTTP service in this agent.");
%>