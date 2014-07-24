/*******************************************************************************
 * Monitor UI JS utilities.
 * Author: larryang
 * *****************************************************************************/

/*******************************************************************************
 * General context menu and specific handler hooking
 * *****************************************************************************/

var contextMenu;

function onRowContextMenu(e, row){
    
    var rowNum = (typeof row.id == 'undefined') ? row : row.id;

    e.preventDefault();

    if (typeof contextMenu == 'undefined'){
        createContextMenu(action);
    } 
    
    if (action == 'Users'){
        $('#dgUsers').datagrid('selectRow', rowNum);
    }else{
        $('#tg' + action).treegrid('select', rowNum);
    }
    
    contextMenu.menu('show', {
        left:e.pageX, 
        top:e.pageY
    });  
}

function createContextMenu(action){
    
    contextMenu = $('<div/>').appendTo('body');
    
    if (action == 'Agents'){
        
        contextMenu.menu({
            onClick: function(menuItem){
                if (menuItem.name == 'Release'){
                    release();
                }else if (menuItem.name == 'ShowDetails'){
                    showDetails();
                }else if (menuItem.name == 'CollapseAll'){
                    collapseAll();
                }else if (menuItem.name == 'ExpandAll'){
                    expandAll();
                }else if (menuItem.name == 'MaintainMode'){
                    switchMaintainMode();
                }else if (menuItem.name == 'NormalMode'){
                    switchNormalMode();
                }else if (menuItem.name == 'InjectProperties'){
                    injectProperties();
                }
            }
        });
        contextMenu.menu('appendItem', {
            text: 'Show details', 
            name: 'ShowDetails'
        });
        if (typeof userrole != 'undefined' && userrole == 'ADMINISTRATOR'){
            contextMenu.menu('appendItem', {
                text: 'Release', 
                name: 'Release'
            });
            contextMenu.menu('appendItem', {
                text: 'Maintain mode', 
                name: 'MaintainMode'
            });
            contextMenu.menu('appendItem', {
                text: 'Normal mode', 
                name: 'NormalMode'
            });
            contextMenu.menu('appendItem', {
                text: 'Inject properties', 
                name: 'InjectProperties'
            });
        }
        contextMenu.menu('appendItem', {
            separator: true
        });
        contextMenu.menu('appendItem', {
            text: 'Collapse all', 
            name: 'CollapseAll'
        });
        contextMenu.menu('appendItem', {
            text: 'Expand all', 
            name: 'ExpandAll'
        });
        
    }else{
        
        contextMenu.menu({
            onClick: function(menuItem){
                if (menuItem.name == 'ShowDetails'){
                    showDetails();
                }else if (menuItem.name == 'Release'){
                    release();
                }else if (menuItem.name == 'CollapseAll'){
                    collapseAll();
                }else if (menuItem.name == 'ExpandAll'){
                    expandAll();
                }
            }
        });
        contextMenu.menu('appendItem', {
            text: 'Show details', 
            name: 'ShowDetails'
        });
        contextMenu.menu('appendItem', {
            separator: true
        });
        contextMenu.menu('appendItem', {
            text: 'Collapse all', 
            name: 'CollapseAll'
        });
        contextMenu.menu('appendItem', {
            text: 'Expand all', 
            name: 'ExpandAll'
        });
    //Release operation only happens from agents.
    //TODO: Should need to seperate Clients action since it is more like Agents, May need change later on.
    //contextMenu.menu('appendItem', {text: 'Release', name: 'Release'});
    }
}

/*******************************************************************************
 * Context menu handlers.
 * *****************************************************************************/

function showDetails(){
    
    var node = $('#tg' + action).treegrid('getSelected');

    if (node){
        document.getElementById("detailPopupWindow").innerHTML = node.details;
        $('#detailPopupWindow').window('open');
    }                                        
}

function switchMaintainMode(){
    var node = $('#tgAgents').treegrid('getSelected');
    if (node && typeof node.nodetype != 'undefined'){
        if (node.nodetype == 'agent'){
            $.post('adminOperation.jsp', {
                operation : "SetMaintainMode",
                type : node.nodetype, 
                host : node.host
            }, function(response){
                alert(response);
            /* do something with response*/
            })
        }else{
            alert("MaintainMode " + node.nodetype + " not supported yet.");
        }
    }else{
        alert("This operation for selected node is not supported.");
    }
}

function switchNormalMode(){
    var node = $('#tgAgents').treegrid('getSelected');
    if (node && typeof node.nodetype != 'undefined'){
        if (node.nodetype == 'agent'){
            $.post('adminOperation.jsp', {
                operation : "SetNormalMode",
                type : node.nodetype, 
                host : node.host
            }, function(response){
                alert(response);
            /* do something with response*/
            })
        }else{
            alert("NormalMode " + node.nodetype + " not supported yet.");
        }
    }else{
        alert("This operation for selected node is not supported.");
    }
}

function injectProperties(){
    var node = $('#tgAgents').treegrid('getSelected');
    if (node && typeof node.nodetype != 'undefined'){
        if (node.nodetype == 'agent'){
            $('#propertyPopupWindow').window('open');
        }else{
            alert("InjectProperties " + node.nodetype + " not supported yet.");
        }
    }else{
        alert("This operation for selected node is not supported.");
    }

}

function injectPropertieContent(){
    //alert(document.getElementById("propertyContent").value);
    var node = $('#tgAgents').treegrid('getSelected');
    if (document.getElementById("propertyContent").value != ''){
        $.post('adminOperation.jsp', {
            operation : "InjectProperties",
            type : node.nodetype, 
            host : node.host,
            properties : document.getElementById("propertyContent").value
        }, function(response){
            alert(response);
        /* do something with response*/
        })
    }
    
    $('#propertyPopupWindow').window('close');
}

function release(){
    var node = $('#tgAgents').treegrid('getSelected');
                                            
    if (node && typeof node.nodetype != 'undefined'){
        if (node.nodetype == 'test'){
            $.post('adminOperation.jsp', {
                type : node.nodetype, 
                host : node.host, 
                id : node.testid
            }, function(response){
                alert(response);
            /* do something with response*/
            })
        }else if (node.nodetype == 'device'){
            $.post('adminOperation.jsp', {
                type : node.nodetype, 
                host : node.host, 
                details : node.details
            }, function(response){
                alert(response);
            /* do something with response*/
            })
        }else{
            alert("Release " + node.nodetype + " not supported yet.");
        }
    }else{
        alert("Release operation for selected node is not supported.");
    } 
}

function collapseAll(){
    $('#tg' + action).treegrid('collapseAll');
}

function expandAll(){
    $('#tg' + action).treegrid('expandAll');
}

function collapse(){
    var node = $('#tg' + action).treegrid('getSelected');
    if (node){
        $('#tg' + action).treegrid('collapse', node.id);
    }
}

function expand(){
    var node = $('#tg' + action).treegrid('getSelected');
    if (node){
        $('#tg' + action).treegrid('expand', node.id);
    }
}

/*******************************************************************************
 * UI formatting functions
 * *****************************************************************************/
function formatProgress(value){
    if (typeof value != 'undefined'){
        if (value < 10){
            var s = '<div style="width:100%;border:1px solid #ccc">' +
            '<div style="width:' + value + '%;background:#cc0000;color:#fff"></div>'  + value + '%' + 
            '</div>';
            return s;         
        }else{
            var s = '<div style="width:100%;border:1px solid #ccc">' +
            '<div style="width:' + value + '%;background:#cc0000;color:#fff">' + value + '%' + '</div>'
            '</div>';
            return s;
        }
    } else {
        return '';
    }
}

function openURLInWindow(url)
{
    window.open(url, "_blank", "width=1000, height=800") ;
}

function formatURL(value){
    if (typeof value != 'undefined' && value != null && value != '' && value != 'null'){
        var s = '<a href="#" onclick="openURLInWindow(\'' + value + '\')">' + value + '</a>';
        return s;
    }else{
        return value;
    }
}

function formatSearchTarget(value){
    if (typeof value != 'undefined' && value != null && value != '' && value != 'null' && typeof currentSearchValue != 'undefined'){
        if (value.indexOf(currentSearchValue) >= 0){
            var s = '<div style="width:100%;background:FFE4E1">' + value + '</div>';
            return s;
        }
    }
    return value;
}

function formatStatus(value){
    if (typeof value != 'undefined'){
        var s;
        if (value == 'NORMAL' || value == 'FREE' || value == 'NEW' || value == 'UPDATE' | value == 'INUSE' | value == 'IDLE'
                                || value == 'STARTED' || value == 'STOPPED' || value == 'FINISHED'){
            s = '<div style="width:100%;background:lightgreen">' + value + '</div>';
        }else if (value == 'BUSY' || value == 'RESERVED' || value == 'FAILED') {
            s = '<div style="width:100%;background:red">' + value + '</div>';
        }else if (value == 'UNKNOWN' || value == 'PENDING' || value == 'PREPARING'){
            s = '<div style="width:100%;background:lightyellow">' + value + '</div>';
        }else if (value =='MAINTAIN' || value == 'LOST_TEMP' || value == 'LOST'){
            s = '<div style="width:100%;background:lightgrey">' + value + '</div>';
        }else{
            s = value;
        }
        return s;
    }else{
        return value;
    }  
}

function formatLoadedtask(value){
    
    if (typeof value != 'undefined' && value != null && value != '' && value != 'null'){
        var s = '<div style="width:100%;background:lightgreen">' + value + '</div>';
        return s;
    }else{
        return value;
    }
}

function formatHangtime(value){
    if (typeof value != 'undefined'){
        var s;
        if (value <= 15){
            s = '<div style="width:100%;background:lightgreen">' + value + ' min</div>';
        }else if (value <= 30){
            s = '<div style="width:100%;background:yellow">' + value + ' min</div>';
        }else{
            s = '<div style="width:100%;background:red">' + value + ' min</div>';
        }
        return s;
    }else{
        return value;
    }
}

/*******************************************************************************
 * User management related functions
 * *****************************************************************************/
var editIndex = undefined;
var changedRows = new Array();

function endEditing(){
    if (editIndex == undefined){
        return true
    }
    if ($('#dgUsers').datagrid('validateRow', editIndex)){
        var ed = $('#dgUsers').datagrid('getEditor', {
            index:editIndex,
            field:'userrole'
        });
        var userrole = $(ed.target).combobox('getText');
        $('#dgUsers').datagrid('getRows')[editIndex]['userrole'] = userrole;
        $('#dgUsers').datagrid('endEdit', editIndex);
        changedRows.push($('#dgUsers').datagrid('getRows')[editIndex]);
        editIndex = undefined;
        return true;
    } else {
        return false;
    }
}

function onClickRow(index){
    if (editIndex != index){
        if (endEditing()){
            $('#dgUsers').datagrid('selectRow', index)
            .datagrid('beginEdit', index);
            editIndex = index;
        } else {
            $('#dgUsers').datagrid('selectRow', editIndex);
        }
    }
}

function accept(){
    if (endEditing()){
        //alert(JSON.stringify(changedRows));
        $.post('adminOperation.jsp', {
            type : 'modUsers', 
            users : JSON.stringify(changedRows)
        }, function(response){
            alert(response);
        /* do something with response*/
        })
        $('#dgUsers').datagrid('acceptChanges');
    }
}

function reject(){
    $('#dgUsers').datagrid('rejectChanges');
    editIndex = undefined;
}

/*******************************************************************************
 * Searching utils
 ******************************************************************************/
var currentSearchValue;
function doSearch(value){
    currentSearchValue = value;
    $('#tg' + action).treegrid('reload', {filterValue:value});
    //$('#tg' + action).treegrid('reload');
}

function tgSuccessCallback(data){
    if (typeof currentSearchValue != 'undefined'){
         expandAll();
    }
}

function tgDblClickRowCallback(row){
    
    var targetAction;
    var nodeType = row['nodetype'];
    var name = row['name'];
    
    if (typeof(nodeType) == 'undefined' || nodeType == '' || typeof(name) == 'undefined' || name == ''){
        return;
    }
    
    if (nodeType == 'agent'){
        targetAction = "Agents";
    }else if (nodeType == 'client'){
        targetAction = 'Clients';
    }else if (nodeType == 'task'){
        targetAction = 'Tasks';
    }else if (nodeType == 'test'){
        targetAction = 'Tests';
    }else if (nodeType == 'device'){
        targetAction = 'Devices';
    }else{
        alert("Error: Note type " + nodeType + " is not supported.");
        return;
    }
    
    openURLInWindow("index.jsp?action=" + targetAction + "&search=" + name);

}

/*******************************************************************************
 * UI event handlers
 *******************************************************************************/
function updateICasePath(selectedTestType){

    if (selectedTestType == 'test_automation/system_tests/RFA'){
        document.getElementById('icasePath').value = "tools/test_automation";
    }else if (selectedTestType == 'icase'){
        document.getElementById('icasePath').value = "aol1/platform/packages/apps/Gallery2";
    }else{
        document.getElementById('icasePath').value = "";
    }
}

/*******************************************************************************
 * Onload initialization.
 *******************************************************************************/
$(function(){
    
    $('#mqRegion').combobox('reload', "loadData.jsp?action=Regions");
    
    $('#mqRegion').combobox({
        onSelect: function(data){
            $.post('adminOperation.jsp', {
                type : "region", 
                mqRegion : data.id
            }, function(response){
                location.reload();
            })
        }
    })

    $('#year').combobox({
        onSelect: function(data){
            $('#week').combobox('reload', "loadICaseInfo.jsp?action=week&year=" + data.id);
        }
    })
    
    $('#week').combobox({
        onSelect: function(data){
            $('#product').combobox('reload', "loadICaseInfo.jsp?action=product&year=" + $('#year').combobox('getValue') + "&week=" + data.id + "&random=" + Math.random());
            $('#icaseMode').combobox('reload', "loadICaseInfo.jsp?action=icaseMode&year=" + $('#year').combobox('getValue') + "&week=" + data.id + "&random=" + Math.random());
            $('#icaseIndex').combobox('reload', "loadICaseInfo.jsp?action=icaseIndex&year=" + $('#year').combobox('getValue') + "&week=" + data.id + "&random=" + Math.random());
            $('#icasePath').combobox('reload', "loadICaseInfo.jsp?action=icasePath&year=" + $('#year').combobox('getValue') + "&week=" + data.id + "&random=" + Math.random());
            $('#icaseSplit').combobox('reload', "loadICaseInfo.jsp?action=icaseSplit&year=" + $('#year').combobox('getValue') + "&week=" + data.id + "&random=" + Math.random());
            $('#icaseDevice').combobox('reload', "loadICaseInfo.jsp?action=icaseDevice&year=" + $('#year').combobox('getValue') + "&week=" + data.id + "&random=" + Math.random());
            $('#icaseDebug').combobox('reload', "loadICaseInfo.jsp?action=icaseDebug&year=" + $('#year').combobox('getValue') + "&week=" + data.id + "&random=" + Math.random());
            $('#targetVariant').combobox('reload', "loadICaseInfo.jsp?action=targetVariant&year=" + $('#year').combobox('getValue') + "&week=" + data.id + "&random=" + Math.random());
        }
    })

    if (typeof(searchTargetStr) != 'undefined' && searchTargetStr != ''){
        doSearch(searchTargetStr);
    }
    
    $('#triggerForm').form({
        success:function(data){
            alert(data);
        }
    });

})


