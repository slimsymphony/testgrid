<%@page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@page import="frank.incubator.testgrid.common.model.*"%>
<%@page import="frank.incubator.testgrid.agent.*"%>
<%@page import="frank.incubator.testgrid.common.*"%>
<%@page import="java.util.concurrent.atomic.AtomicInteger"%>
<%@page import="java.util.*"%>
<%@page import="java.sql.Timestamp"%>
<%@page import="java.io.*"%>
<%
String sn = request.getParameter("sn");
String server = request.getServerName();
%>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="Agents of S.H.I.L.E.D :)">
<link href='css/upload.css'/>
<script src="js/jquery-2.1.1.min.js" type="text/javascript"></script>
<script src="js/jquery-ui.min.js" type="text/javascript"></script>
<script src="js/jquery.widget.min.js" type="text/javascript"></script>
<script src="js/jquery.fileupload.js" class="ng-scope"></script>
<script src="js/jquery.ui.widget.js" class="ng-scope"></script>
<script src="js/jquery.iframe-transport.js"></script>
<script src="js/hammer.min.js" type="text/javascript"></script>
<script src="js/spin.min.js" type="text/javascript"></script>
<script>
var sn = '<%=sn%>';
var server = '<%=server%>';
var connection,canvas,ctx,hammer;
var resolution,deviceWidth=1080,deviceHeight=1920;//, canvasWidth=0, canvasHeight = 0;
var pageWidth;
var pageHeight;
var ratio=1;
var canvasHeight = 700;//(pageWidth>pageHeight)?(pageHeight>800?800:(Math.round(pageHeight/100)*100)):();
var canvasWidth = 420;
var swipe=[];
var isConn = false;
// 关闭提醒
window.onbeforeunload = function(){
	$.ajax({
	    url: "http://"+server+":5451/handle.jsp?op=release&sn="+sn,
	    type: "GET",
	    async: false
	});
}

/* 转菊花参数 **/
var opts = {
	high : 30,
	lines : 12,
	length : 5,
	width : 20,
	radius : 10,
	corners : 1,
	rotate : 0,
	trail : 60,
	speed : 1,
	direction : 1,
	shadow : true,
	hwaccel : false
};

/* 转菊花方法 * */
$.fn.spin = function(opts) {
	this.each(function() {
		var $this = $(this), data = $this.data();

		if (data.spinner) {
			data.spinner.stop();
			delete data.spinner;
		}
		if (opts !== false) {
			data.spinner = new Spinner($.extend({
				color : $this.css('color')
			}, opts)).spin(this);
		}
	});
	return this;
};
//$(element).hammer(options).bind("pan", myPanHandler);

$(function(){
	pageWidth = document.body.clientWidth-100;
	pageHeight = document.body.clientHeight-100;
	/*var calculateScreen = function(){
		var pagePotrait = pageWidth/pageHeight;
		var pageMin,pageMax,deviceMin,deviceMax;
		if(pagePotrait>1){
			pageMin = Math.round(pageHeight/100)*100;
		}else{
			pageMin = Math.round(pageWidth/100)*100;
		}
		var devicePortrait = deviceWidth/deviceHeight;
		if(devicePortrait>1){
			deviceMin = Math.round(deviceHeight/100)*100;
		}else{
			deviceMin = Math.round(deviceWidth/100)*100;
		}
		if( (pagePotrait>1) == (devicePortrait>1)){
			ratio = pageMin/deviceMin;
			canvasWidth = pageMin*ratio;//100*(Math.round(pageWidth/100));
			canvasHeight = pageMin//canvasWidth * (pageHeight/pageWidth);
		}else{
			if(pagePotrait>1){
				canvasHeight = 
			}else{
				
			}
		}
	}*/
	var startSpin = function(){
		$('#preview').show();
		try {$('#preview').spin(opts);} catch (e) {alert(e);}
	}
	var stopSpin = function(){
		try {$('#preview').data().spinner.stop();} catch (e) {alert(e);}
		$('#preview').hide();
	}
	startSpin();
	
	$('#btnBack').on("click",function(){
		sendKeyEvent("BACK");
	});
	
	$('#btnHome').on("click",function(){
		sendKeyEvent("HOME");
	});
	
	$('#btnMenu').on("click",function(){
		sendKeyEvent("MENU");
	});
	
	$.get(
		"http://"+server+":8088/metainfo/size?sn="+sn,
		function(data,status){
			if(status=='success'){
				data = JSON.parse(data);
				if(data.result){
					resolution = data.size;
					var arr = resolution.split("x");
					deviceWidth = arr[0]*1;
					deviceHeight = arr[1]*1;
					$('#canvas').css("width",canvasWidth);
					$('#canvas').css("height",canvasHeight);
				}
			}
		}
	);
	canvas = document.getElementById("canvas");
	ctx = canvas.getContext("2d");
	var draw =function(imgData, frameCount) {
	    var r = new FileReader();
	    r.readAsBinaryString(imgData);
	    r.onload = function(){ 
	        var img=new Image();
	        img.onload = function() {
	            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
	        }
	        img.src = "data:image/jpeg;base64,"+window.btoa(r.result);
	    };
	}
	var encode =function(input) {
	    var keyStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
	    var output = "";
	    var chr1, chr2, chr3, enc1, enc2, enc3, enc4;
	    var i = 0;

	    while (i < input.length) {
	        chr1 = input[i++];
	        chr2 = i < input.length ? input[i++] : Number.NaN; // Not sure if the index 
	        chr3 = i < input.length ? input[i++] : Number.NaN; // checks are needed here

	        enc1 = chr1 >> 2;
	        enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
	        enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
	        enc4 = chr3 & 63;

	        if (isNaN(chr2)) {
	            enc3 = enc4 = 64;
	        } else if (isNaN(chr3)) {
	            enc4 = 64;
	        }
	        output += keyStr.charAt(enc1) + keyStr.charAt(enc2) +
	                  keyStr.charAt(enc3) + keyStr.charAt(enc4);
	    }
	    return output;
	}
	connection = new WebSocket('ws://'+server+':8088/events/');
	connection.binaryType="arraybuffer";
	
	// When the connection is open, send some data to the server
	connection.onopen = function () {
	  connection.send("sn:"+sn); // Send the message 'Ping' to the server
	};
	connection.onmessage = function (event) {
		$('#canvas').show();
        var data = new Uint8Array(event.data);
        var bytes = new Blob([data], {'type': 'image\/jpg'});
        //var screen = document.getElementById("screen");
        //screen.src = 'data:image/png;base64,'+encode(data);
        draw(bytes);
        if(!isConn){
        	stopSpin();
        	isConn = true;
        }
    };
    
    var sendKeyEvent = function(key){
    	var param = '{"sn":"'+sn+'","type":"'+key+'"}';
    	$('#ifm').attr('src',"http://"+server+":8088/metainfo/action?action="+param);
    }
    
    var doAction =function(action, coordinates){
    	/*$.get("",function(data,status){
    		if(status == 'success' && result==true){
    		}
    	});*/
    	var param = '{"sn":"'+sn+'","type":"'+action+'","startPointer":{"x":'+coordinates[0]+',"y":'+coordinates[1]+'},"endPointer":{"x":'+coordinates[2]+',"y":'+coordinates[3]+'}}';
    	$('#ifm').attr('src',"http://"+server+":8088/metainfo/action?action="+param);
    };
	// Log errors
	connection.onerror = function (error) {
	 	alert('Communication Error ' + error +", please refresh the page to retry.");
	};
	hammer = new Hammer(canvas);
	hammer.on("panstart", function(ev) {
		var e = ev.srcEvent;
		swipe = [];
		transform(e,swipe);
	});
	hammer.on("panend", function(ev) {
		var e = ev.srcEvent;
		transform(e,swipe);
		doAction("SWIPE",swipe);
	});
	/*hammer.on("panleft", function(ev) {
		
	});
	hammer.on("panright", function(ev) {
	});*/
	hammer.on("tap", function(ev) {
		var e = ev.srcEvent;
		var arr = transform(e);
		arr.push(0);
		arr.push(0);
		doAction("TAP",arr);
	});
	hammer.on("press", function(ev) {
	});
	function transform(e, arr){
		if(!arr || arr == null)
			arr = [];
		var x;
		var y;
		if (e.pageX || e.pageY) { 
		  x = e.pageX;
		  y = e.pageY;
		}
		else { 
		  x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft; 
		  y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop; 
		} 
		x -= canvas.offsetLeft;
		y -= canvas.offsetTop;
		x= x/canvasWidth*deviceWidth;
		y= y/canvasHeight*deviceHeight;
		arr.push(x); arr.push(y);
		return arr;
	}
	
	$("#fileupload").fileupload({
    	url:"http://"+server+':5451/install',
    	acceptFileTypes: /(\.|\/)(apk|ipa)$/i,
    	sequentialUploads: true,
    	formData:{'sn':sn}
	}).bind('fileuploadsubmit', function(e,data){
		startSpin();
    }).bind('fileuploaddone', function (e, data) {
    	try {stopSpin();} catch (e) {}
    	if(data && (data.success || (data.result && data.result.success)))
    		alert('安装成功');
    	else
    		alert('安装失败');
    }).bind('fileuploadfail', function (e, data) {
    	try {stopSpin();} catch (e) {}
    	alert('安装失败,远程设备连接不可用');
    });
	$("#uploadBtn").click(function(){
    	$("#fileupload").click();
     });
	
	var re=/^((http|https):\/\/)?(\w(\:\w)?@)?([0-9a-z_-]+\.)*?([a-z0-9-]+\.[a-z]{2,6}(\.[a-z]{2})?(\:[0-9]{2,6})?)((\/[^?#<>\/\\*":]*)+(\?[^#]*)?(#.*)?)?$/i;
	$("#uploadBtn2").click(function(){
    	var url = window.prompt("请提供下载的URL(以http://开头的apk)");
    	if(url && url != '' && re.test(url)){
    		startSpin();
        	$.post('http://'+server+':5451/install',{'sn':sn, 'ext':'apk', 'url':url},function(data,status){
        		try {stopSpin();} catch (e) {}
        		if(data && (data.success || (data.result && data.result.success))){
        			alert('安装成功');
        			return;
        		}
        		alert('安装失败');
        	});
    	}else{
    		alert('您提供的URL非法');
    	}
    	
     });
});

</script>
</head>

<body>
<div id="container" style="text-align:center;">
<div id="preview" style="position: relative;top:200px;"></div>
<div id="panel">
<button id="uploadBtn" style="width:150px;font-family:Microsoft Yahei;margin-bottom:6px;">上传安装App</button>
<button id="uploadBtn2" style="width:150px;font-family:Microsoft Yahei;margin-bottom:6px;">提供URL安装App</button>
<input type="file" style="display:none;color:white;" id="fileupload" name="fileupload"/>
<br/>
<button id="btnBack">Back</button> 
<button id="btnHome">Home</button>
<button id="btnMenu">Menu</button>
</div>

<canvas id="canvas" width="420" height="700" style="display:none;" />
<!-- <img id="screen" width="480" height="800" style="display:none;"/>-->
</div>
<iframe id="ifm" name="ifm" style="display:none;"></iframe>
</body>
</html>