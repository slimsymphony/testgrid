/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frank.incubator.testgrid.monitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.jms.Queue;
import javax.jms.Topic;
import javax.servlet.GenericServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.message.MessageException;
import frank.incubator.testgrid.common.message.MessageHub;

/**
 * Initialization process, including MQ binding etc.
 */
public class InitializationServlet extends GenericServlet {

	private static final long serialVersionUID = 1L;
	private List<String> mqUris = new ArrayList<String>();
    private LogConnector log;

    public void init(ServletConfig servletConfig) throws ServletException {
        this.mqUris = Arrays.asList(servletConfig.getInitParameter("mqUri").split(";"));
        log = LogUtils.get("InitializationServlet");
        conn2MQ();
    }

    public void service(ServletRequest request, ServletResponse response)
            throws ServletException, IOException {

        response.getWriter().write("<html><body>Init parameters. " + "mqUris: " + this.mqUris.toString() + "</body></html>");
    }

    private void conn2MQ() {
        try {
            for (String mqUri : mqUris){
                
                String[] mqUriParams = mqUri.split("\\*");
                if (mqUriParams.length < 2){
                    log.error("Ilegal format (should be label*mqUrl) for mq configure: " + mqUri);
                    continue;
                }
                MonitorCache monitorCache = RegionManager.addMQ(mqUri);
                MessageHub hub = new MessageHub(mqUriParams[1], Constants.MSG_TARGET_MONITOR);
                hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_AGENT_STATUS, null, new StatusListenerAdapter(Constants.MSG_STATUS_AGENT, monitorCache));
                hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_TASK_STATUS, null, new StatusListenerAdapter(Constants.MSG_STATUS_TASK, monitorCache));
                hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_TEST_STATUS, null, new StatusListenerAdapter(Constants.MSG_STATUS_TEST, monitorCache));
                hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_DEVICE_STATUS, null, new StatusListenerAdapter(Constants.MSG_STATUS_DEVICE, monitorCache));
                hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_CLIENT_STATUS, null, new StatusListenerAdapter(Constants.MSG_STATUS_CLIENT, monitorCache));
                hub.bindHandlers(Constants.BROKER_STATUS, Topic.class, Constants.HUB_MONITOR_STATUS, null, new StatusListenerAdapter(Constants.MSG_STATUS_MONITOR, monitorCache));
                hub.bindHandlers(Constants.BROKER_NOTIFICATION, Queue.class, Constants.HUB_AGENT_NOTIFICATION, null, null);
                AdminController adminControler = new AdminController(hub);
                RegionManager.addControler(mqUri, adminControler);
            }
        } catch (MessageException e) {
            log.error("Exception in conn2MQ: {}.", e);
        }
    }
}
