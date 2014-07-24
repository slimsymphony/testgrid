<%@page import="org.apache.commons.lang3.StringUtils"%>
<%@page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.monitor.*"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<html lang="en">
    <head>
        <meta charset="utf-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="description" content="TestGrid Portal">

        <title>TestGrid Portal</title>

        <!-- Bootstrap core CSS -->
        <link href="css/bootstrap.min.css" rel="stylesheet">
        <!-- Custom styles for this template -->
        <link href="css/dashboard.css" rel="stylesheet">
        <link rel="stylesheet" type="text/css" href="css/themes/bootstrap/easyui.css">
        <link rel="stylesheet" type="text/css" href="css/themes/icon.css">

    </head>

    <body>
        <%
            String action = request.getParameter("action");
            String searchTargetStr = request.getParameter("search");
            
            if (action == null || action.equals("")) {
                action = "Agents";
            }
            
            if (StringUtils.isEmpty(searchTargetStr)){
                searchTargetStr = "";
            }
            
            String loadDataURL = "loadData.jsp?" + "action=" + action + "&mqRegion=" + RegionManager.getCurrentRegion();

            String userName = "";
            String userRole = "";

            Object userNameAttr = session.getAttribute("username");
            Object userRoleAttr = session.getAttribute("userrole");

            if (userNameAttr == null || userNameAttr.toString() == null || userNameAttr.toString().isEmpty()) {
                response.sendRedirect("login.jsp");
            } else {
                userName = userNameAttr.toString();
                if (userRoleAttr != null) {
                    userRole = userRoleAttr.toString();
                }
            }
        %>
        <div class="navbar navbar-inverse navbar-fixed-top" role="navigation">
            <div class="container-fluid">
                <div class="navbar-header">

                    <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-collapse">
                        <span class="sr-only">Toggle navigation</span>
                        <span class="icon-bar"></span>
                        <span class="icon-bar"></span>
                        <span class="icon-bar"></span>
                    </button>
                    <a class="navbar-brand" href="#">TestGrid Portal</a>
                </div>

                <div class="navbar-collapse collapse">
                    <ul class="nav navbar-nav navbar-right">
                        <% if (userRole.equals("ADMINISTRATOR")) {%>
                        <li><a href="index.jsp?action=Users">Users</a></li>
                        <% }%>
                        <li><a href="#">Help</a></li>
                        <li><a href="logout.jsp">Logout</a></li>
                    </ul>

                    <form class="navbar-form navbar-right">
                        <input type="text" class="form-control" placeholder="Search...">
                    </form>

                    <ul class="nav navbar-nav navbar-right">
                        <li><a href="#"><% out.println("Welcome " + userName + " [role as: " + userRole + "]");%></a></li>   
                    </ul>
                </div>
            </div>
        </div>

        <div class="container-fluid">
            <div class="row">

                <div class="col-sm-3 col-md-1 sidebar">
                    <ul class="nav nav-sidebar">
                        <li <% if (action.equals("Agents")) {%> class="active" <%}%>><a href="index.jsp?action=Agents">Agents</a></li>
                        <li <% if (action.equals("Tasks")) {%> class="active" <%}%>><a href="index.jsp?action=Tasks">Tasks</a></li>
                        <li <% if (action.equals("Clients")) {%> class="active" <%}%>><a href="index.jsp?action=Clients">Clients</a></li>
                        <li <% if (action.equals("Tests")) {%> class="active" <%}%>><a href="index.jsp?action=Tests">Tests</a></li>
                        <li <% if (action.equals("Devices")) {%> class="active" <%}%>><a href="index.jsp?action=Devices">Devices</a></li>
                    </ul>
                    <ul class="nav nav-sidebar">
                        <li><a href="dataDump.jsp">RawData</a></li>
                    </ul>
                </div>
                <div id="detailPopupWindow" class="easyui-window" title="Details" data-options="closed:'true'" style="width:1000px;height:300px;padding:10px;"></div>
                <div id="propertyPopupWindow" class="easyui-window" title="Properties" data-options="closed:'true'" style="width:400px;height:300px;padding:10px;">
                   <textarea id="propertyContent" name="propertyContent" style="width:100%;height:85%;" placeholder="{prop-key1:value1,prop-key2:value2}"></textarea>
                   <a class="easyui-linkbutton" data-options="iconCls:'icon-ok'" href="javascript:void(0)" onclick="javascript:injectPropertieContent();" style="width:80px">Ok</a>
                   <a class="easyui-linkbutton" data-options="iconCls:'icon-cancel'" href="javascript:void(0)" onclick="javascript:$('#propertyPopupWindow').window('close');" style="width:80px">Cancel</a>
                </div>
                <div class="col-sm-9 col-sm-offset-3 col-md-10 col-md-offset-1 main">
                    <!--h1 class="page-header">Dashboard</h1-->

                    <h2 class="sub-header">Dashboard - <%= action%></h2>
                    <div class="table-responsive">
                        <%if (action.equals("Agents") || action.equals("Clients") || action.equals("Tests") || action.equals("Devices") 
                                || action.equals("Tasks")){ %>
                        <select id="mqRegion" class="easyui-combobox" name="mqRegion"  style="width:200px;" url="" valueField="id" textField="text" url="loadData.jsp?action=Regions"/>
                        <input id="searchInput" value='<%=searchTargetStr%>' class="easyui-searchbox" data-options="prompt:'Please Input Value To Search...',searcher:doSearch" style="width:300px"></input>
                        <div style="margin:10px 0;"></div>
                        <%}%>
                        <% if (action.equals("Agents")) {%>
                        <table id="tgAgents" class="easyui-treegrid" title="Agent detailed status" style="width:1200px;height:720px"
                               data-options="
                               rownumbers: false,
                               animate: true,
                               collapsible: false,
                               fitColumns: true,
                               url: '<%=loadDataURL%>',
                               method: 'get',
                               idField: 'id',
                               treeField: 'name',
                               onDblClickRow: tgDblClickRowCallback,
                               onLoadSuccess: tgSuccessCallback,
                               onContextMenu: onRowContextMenu
                               ">
                            <thead>
                                <tr>
                                    <th data-options="field:'name',width:230,formatter:formatSearchTarget">Agent/Device/Test</th>
                                    <th data-options="field:'status',width:70,align:'left',formatter:formatStatus">Status</th>
                                    <th data-options="field:'hangtime',width:70,formatter:formatHangtime">Heartbeat(min)</th>
                                    <th data-options="field:'load',width:70,formatter:formatProgress">Load</th>
                                    <th data-options="field:'host',width:120,formatter:formatSearchTarget">Host</th>
                                    <th data-options="field:'ip',width:80,formatter:formatSearchTarget">IP</th>
                                    <th data-options="field:'details',width:400,hidden:true">Details</th>
                                </tr>
                            </thead>
                        </table>
                        <% } else if (action.equals("Devices")) {%>
                        <table id="tgDevices" class="easyui-treegrid" title="Device detailed status" style="width:1200px;height:720px"
                               data-options="
                               rownumbers: false,
                               animate: true,
                               collapsible: false,
                               fitColumns: false,
                               url: '<%=loadDataURL%>',
                               method: 'get',
                               idField: 'id',
                               treeField: 'name',
                               onDblClickRow: tgDblClickRowCallback,
                               onLoadSuccess: tgSuccessCallback,
                               onContextMenu: onRowContextMenu
                               ">
                            <thead>
                                <tr>
                                    <th data-options="field:'name',width:230,formatter:formatSearchTarget">Device/AttachingAgent</th>
                                    <th data-options="field:'status',width:80,align:'left',formatter:formatStatus">Status</th>
                                    <th data-options="field:'hangtime',width:80,formatter:formatHangtime">Heartbeat(min)</th>
                                    <th data-options="field:'loadedtask',width:200,align:'left',formatter:formatLoadedtask">Loaded task</th>
                                    <th data-options="field:'imei',width:120,sortable:true,formatter:formatSearchTarget">IMEI</th>
                                    <th data-options="field:'productname',width:100,align:'left',sortable:true,formatter:formatSearchTarget">Product name</th>
                                    <th data-options="field:'productcode',width:100,align:'left',sortable:true,formatter:formatSearchTarget">Product code</th>
                                    <th data-options="field:'rmcode',width:100,align:'left',sortable:true,formatter:formatSearchTarget">RM code</th>
                                    <th data-options="field:'tag',width:100,sortable:true,formatter:formatSearchTarget">Device tag</th>
                                    <th data-options="field:'details',width:200,hidden:true">Details</th>
                                </tr>
                            </thead>
                        </table>
                        <% } else if (action.equals("Tasks")) {%>
                        <table id="tgTasks" class="easyui-treegrid" title="Task detailed status" style="width:1200px;height:720px"
                               data-options="
                               rownumbers: false,
                               animate: true,
                               collapsible: false,
                               fitColumns: false,
                               url: '<%=loadDataURL%>',
                               method: 'get',
                               idField: 'id',
                               treeField: 'name',
                               onDblClickRow: tgDblClickRowCallback,
                               onLoadSuccess: tgSuccessCallback,
                               onContextMenu: onRowContextMenu
                               ">
                            <thead>
                                <tr>
                                    <th data-options="field:'name',width:350,formatter:formatSearchTarget">Task/Test/Device/Agent</th>
                                    <th data-options="field:'status',width:100,align:'left',formatter:formatStatus">Status</th>
                                    <th data-options="field:'hangtime',width:100,formatter:formatHangtime">Heartbeat(min)</th>
                                    <th data-options="field:'owner',width:100,formatter:formatSearchTarget">Owner</th>
                                    <th data-options="field:'starttime',width:150,formatter:formatSearchTarget">Start time</th>
                                    <th data-options="field:'endtime',width:150,formatter:formatSearchTarget">End time</th>
                                    <th data-options="field:'url',width:200,sortable:true,formatter:formatURL">URL</th>
                                    <th data-options="field:'details',width:400,hidden:true">Details</th>
                                </tr>
                            </thead>
                        </table>
                        <% } else if (action.equals("Clients")) {%>
                        <table id="tgClients" class="easyui-treegrid" title="Client detailed status" style="width:1200px;height:720px"
                               data-options="
                               rownumbers: false,
                               animate: true,
                               collapsible: false,
                               fitColumns: false,
                               url: '<%=loadDataURL%>',
                               method: 'get',
                               idField: 'id',
                               treeField: 'name',
                               onDblClickRow: tgDblClickRowCallback,
                               onLoadSuccess: tgSuccessCallback,
                               onContextMenu: onRowContextMenu
                               ">
                            <thead>
                                <tr>
                                    <th data-options="field:'name',width:350,formatter:formatSearchTarget">Client/Task/Test/Device/Agent</th>
                                    <th data-options="field:'status',width:200,align:'left',formatter:formatStatus">Status</th>
                                    <th data-options="field:'hangtime',width:200,formatter:formatHangtime">Heartbeat(min)</th>
                                    <th data-options="field:'host',width:200,formatter:formatSearchTarget">Host</th>
                                    <th data-options="field:'ip',width:200,formatter:formatSearchTarget">IP</th>
                                    <th data-options="field:'details',width:400,hidden:true">Details</th>
                                </tr>
                            </thead>
                        </table>
                        <% } else if (action.equals("Tests")) {%>
                        <table id="tgTests" class="easyui-treegrid" title="Test detailed status" style="width:1200px;height:720px"
                               data-options="
                               rownumbers: false,
                               animate: true,
                               collapsible: false,
                               fitColumns: false,
                               url: '<%=loadDataURL%>',
                               method: 'get',
                               idField: 'id',
                               treeField: 'name',
                               onDblClickRow: tgDblClickRowCallback,
                               onLoadSuccess: tgSuccessCallback,
                               onContextMenu: onRowContextMenu
                               ">
                            <thead>
                                <tr>
                                    <th data-options="field:'name',width:330,formatter:formatSearchTarget">Test/BelongingTask</th>
                                    <th data-options="field:'status',width:100,align:'left',formatter:formatStatus">Status</th>
                                    <th data-options="field:'hangtime',width:100,formatter:formatHangtime">Heartbeat(min)</th>
                                    <th data-options="field:'starttime',width:150,formatter:formatSearchTarget">Start time</th>
                                    <th data-options="field:'endtime',width:150,formatter:formatSearchTarget">End time</th>
                                    <th data-options="field:'url',width:300,sortable:true,formatter:formatURL">URL</th>
                                    <th data-options="field:'details',width:400,hidden:true">Details</th>
                                </tr>
                            </thead>
                        </table>
                        <% } else if (action.equals("Users")) {%>
                        <table id="dgUsers" class="easyui-datagrid" title="In-line editing user role" style="width:700px;height:auto"
                               data-options="
                               iconCls: 'icon-edit',
                               singleSelect: true,
                               toolbar: '#tbUsers',
                               url: '<%=loadDataURL%>',
                               method: 'get',
                               onClickRow: onClickRow
                               ">
                            <thead>
                                <tr>
                                    <th data-options="field:'username',width:300">UserName</th>
                                    <th data-options="field:'userrole',width:200,
                                        formatter:function(value,row){
                                        return row.userrole;
                                        },
                                        editor:{
                                        type:'combobox',
                                        options:{
                                        valueField:'userrole',
                                        textField:'userrole',
                                        url:'loadData.jsp?action=UserRoles',
                                        required:true
                                        }
                                        }">UserRole</th>
                                </tr>
                            </thead>
                        </table>
                        <div id="tbUsers" style="height:auto">
                            <a href="javascript:void(0)" class="easyui-linkbutton" data-options="iconCls:'icon-save',plain:true" onclick="accept()">Accept</a>
                            <a href="javascript:void(0)" class="easyui-linkbutton" data-options="iconCls:'icon-undo',plain:true" onclick="reject()">Reject</a>
                        </div> 
                    <% }%>
                    </div>
                </div>
            </div>
        </div>

        <script src="js/bootstrap.min.js"></script>
        <script type="text/javascript" src="js/jquery.min.js"></script>
        <script type="text/javascript" src="js/jquery.easyui.min.js"></script>
        <script type="text/javascript">var action = '<%=action%>'; var userrole = '<%=userRole%>'; var searchTargetStr = '<%=searchTargetStr%>'</script>
        <script type="text/javascript" src="js/tm_utils.js"></script>

    </body>
</html>