package frank.incubator.testgrid.common;

/**
 * Contents for test cloud common part.
 * 
 * @author Wang Frank
 * 
 */
public class Constants {

	/**
	 * Device default Config file name.
	 */
	public static final String DEVICE_CONFIG_FILE = "devices.json";

	/**
	 * Default execution console output log file name.
	 */
	public static final String EXEC_CONSOLE_OUTPUT = "stdout.log";

	/**
	 * Default execution timeline file name.
	 */
	public static final String EXEC_TIMELINE_OUTPUT = "timeline.txt";

	/**
	 * Split Task and Test Id when set to device status.
	 */
	public static final String TASK_TEST_SPLITER = ":::";

	/**
	 * Default workspace Folder Name.
	 */
	public static final String DEFAULT_WORKSPACE_NAME = "workspace";
	/**
	 * Time constant of 1 second in milliseconds.
	 */
	public static final long ONE_SECOND = 1000L;
	/**
	 * Time constant of 1 minute in milliseconds.
	 */
	public static final long ONE_MINUTE = 60000L;

	/**
	 * Time constant of 1 hour in milliseconds.
	 */
	public static final long ONE_HOUR = 3600000L;
	/**
	 * Time constant of 1 day or 24 hours in milliseconds.
	 */
	public static final long ONE_DAY = 86400000L;
	/**
	 * Format of the timestamp values.
	 */
	public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";

	/**
	 * Format of the calendar dates.
	 */
	public static final String DATE_FORMAT = "yyyy/MM/dd";

	/**
	 * Format of the hour values.
	 */
	public static final String HOUR_FORMAT = "HH:mm";

	/**
	 * Default timeout of a test processing in the Test Automation Service.
	 * Currently 6 hours.
	 */
	public static final long DEFAULT_TEST_TIMEOUT = 6L * ONE_HOUR;

	/**
	 * Task status: Reserved
	 */
	public static final String STATUS_TASK_RESERVED = "Reserved";
	/**
	 * Task status: Running.
	 */
	public static final String STATUS_TASK_RUNNING = "Running";

	/**
	 * Broker for task communication.
	 */
	public static final String BROKER_TASK = "BROKER_TASK";
	/**
	 * Broker for File Transfer.
	 */
	public static final String BROKER_FT = "BROKER_FT";
	/**
	 * Broker for Notification.
	 */
	public static final String BROKER_NOTIFICATION = "BROKER_NOTIFICATION";
	/**
	 * Broker for Model object status notification.
	 */
	public static final String BROKER_STATUS = "BROKER_STATUS";

	/**
	 * Agent status update topic. Agent publish,
	 */
	public static final String HUB_AGENT_STATUS = "TOPIC_AGENT_STATUS";

	/**
	 * Client status update topic. Client publish,
	 */
	public static final String HUB_CLIENT_STATUS = "TOPIC_CLIENT_STATUS";

	/**
	 * Test status update topic. Agent publish,
	 */
	public static final String HUB_TEST_STATUS = "TOPIC_TEST_STATUS";

	/**
	 * Device status update topic. Agent publish,
	 */
	public static final String HUB_DEVICE_STATUS = "TOPIC_DEVICE_STATUS";
	/**
	 * Task status update topic.
	 */
	public static final String HUB_TASK_STATUS = "TOPIC_TASK_STATUS";
	/**
	 * Monitor Heartbeat Loop back topic.
	 */
	public static final String HUB_MONITOR_STATUS = "TOPIC_MONITOR_STATUS";
	/**
	 * Task request publish topic.
	 */
	public static final String HUB_TASK_PUBLISH = "TOPIC_TASK_PUBLISH";
	/**
	 * Task info communication queue.
	 */
	public static final String HUB_TASK_COMMUNICATION = "QUEUE_TASK_COMMUNICATION";
	/**
	 * Mail notification queue.For monitor.
	 */
	public static final String HUB_MAIL_NOTIFICATION = "QUEUE_MAIL_NOTIFICATION";
	/**
	 * Client notification queue. Including admin operation.
	 */
	public static final String HUB_CLIENT_NOTIFICATION = "QUEUE_CLIENT_NOTIFICATION";
	/**
	 * Agent notification queue.Including admin operation.
	 */
	public static final String HUB_AGENT_NOTIFICATION = "QUEUE_AGENT_NOTIFICATION";
	/**
	 * File Transfer pipe Name. which provide message communication for File
	 * transfer transaction.
	 */
	public static final String HUB_FILE_TRANSFER = "QUEUE_FILE_TRANSFER";

	/**
	 * The agent check the status of device requirements of incoming task.
	 * Didn't have require devices.
	 */
	public static final int CAPABILITY_NO = -1;
	/**
	 * The agent check the status of device requirements of incoming task. Have
	 * require devices, but device in use, need wait.
	 */
	public static final int CAPABILITY_WAIT = 0;
	/**
	 * The agent check the status of device requirements of incoming task. Could
	 * be reserved immediately.
	 */
	public static final int CAPABILITY_OK = 1;

	/**
	 * Client ask to reserve devices for task, but failed.
	 */
	public static final int RESERVE_FAILED = 0;
	/**
	 * Client ask to reserve devices for task, success.
	 */
	public static final int RESERVE_SUCC = 1;

	/**
	 * Message Type: Agent accept to execute the Task.
	 */
	public static final int MSG_TASK_ACCEPT = 1;
	/**
	 * Message Type: client ask the agent to reserve Task.
	 */
	public static final int MSG_TASK_RESERVE = 2;
	/**
	 * Message Type: client ask the agent to confirm Task reservation success.
	 */
	public static final int MSG_TASK_CONFIRM = 3;

	/**
	 * Message Type: Agent received all the test artifacts, ready to start test.
	 */
	public static final int MSG_TEST_READY = 4;

	/**
	 * Message Type: Agent confirm the task could start.
	 */
	public static final int MSG_TASK_START = 5;
	/**
	 * Message Type: Client confirm Test *was* FINISHED.
	 */
	public static final int MSG_TEST_FINISH_CONFIRM = 6;
	/**
	 * Message Type: Client request to subscrbe test.
	 */
	public static final int MSG_TASK_SUBSCRIBE = 7;
	/**
	 * Message Type: One side ask another side to start fetching artifacts.
	 */
	public static final int MSG_START_FT_FETCH = 9;
	/**
	 * Message Type: Agent notify client TASK FINISHED.
	 */
	public static final int MSG_TEST_FINISHED = 0;

	/**
	 * Message Type: Agent notify client the test have failed.
	 */
	public static final int MSG_TEST_FAIL = -1;
	/**
	 * Message Type: Agent notify client the test have succeed.
	 */
	public static final int MSG_TEST_SUCC = 0;
	/**
	 * Message Type: client notify agent the task have been cancelled.
	 */
	public static final int MSG_TASK_CANCEL = -2;

	/**
	 * Message content: text, means it's a text message.
	 */
	public static final String MSG_CONTENT_TEXT = "Text";
	/**
	 * Status: agent.
	 */
	public static final int MSG_STATUS_AGENT = 1;

	/**
	 * Status: client.
	 */
	public static final int MSG_STATUS_CLIENT = 2;

	/**
	 * Status: device.
	 */
	public static final int MSG_STATUS_DEVICE = 3;

	/**
	 * Status: test.
	 */
	public static final int MSG_STATUS_TEST = 4;

	/**
	 * Status: task.
	 */
	public static final int MSG_STATUS_TASK = 5;

	/**
	 * Status: monitor (For loop back heart beat).
	 */
	public static final int MSG_STATUS_MONITOR = 6;

	/**
	 * Message Attribute: Queue message sender from.
	 */
	public static final String MSG_HEAD_FROM = "From";
	/**
	 * Message Attribute: Queue message target.
	 */
	public static final String MSG_HEAD_TARGET = "Target";
	/**
	 * Message Attribute: task
	 */
	// public static final String MSG_HEAD_TASK = "Task";

	/**
	 * Message Attribute: test
	 */
	public static final String MSG_HEAD_TEST = "Test";
	/**
	 * Message Attribute: task ID
	 */
	public static final String MSG_HEAD_TASKID = "TaskID";
	/**
	 * Message Attribute: test ID
	 */
	public static final String MSG_HEAD_TESTID = "TestID";
	/**
	 * Message Attribute: reserve begin time.
	 */
	public static final String MSG_HEAD_RESERVE_TIME = "ReserveBegin";
	/**
	 * Message Attribute: reserve begin time.
	 */
	public static final String MSG_HEAD_RUNNING_TIME = "RunningTime";
	/**
	 * Message Attribute: test
	 */
	public static final String MSG_HEAD_TEST_INFO = "TestInfo";

	/**
	 * Message Attribute: transaction, it means all the message during a task
	 * transaction.
	 */
	public static final String MSG_HEAD_TRANSACTION = "Transaction";
	/**
	 * Message Attribute: notification.
	 */
	public static final String MSG_HEAD_NOTIFICATION = "Notification";
	/**
	 * Message Attribute: agent information.
	 */
	public static final String MSG_HEAD_AGENTINFO = "Agent";
	/**
	 * Message Attribute: Task execution state.
	 */
	public static final String MSG_HEAD_TASKSTATE = "TaskState";

	/**
	 * Message Attribute: Status Type attribute.
	 */
	public static final String MSG_HEAD_STATUS_TYPE = "StatusType";

	/**
	 * Message Attribute: agent information.
	 */
	public static final String MSG_HEAD_CLIENTINFO = "Client";
	/**
	 * Message Attribute: File transfer ip address.
	 */
	public static final String MSG_HEAD_FT_IP = "Ip";
	/**
	 * Message Attribute: File transfer Port number.
	 */
	public static final String MSG_HEAD_FT_PORT = "Port";

	/**
	 * Message Attribute : Error.
	 */
	public static final String MSG_HEAD_ERROR = "Error";

	/**
	 * Message Attribute: agent information.
	 */
	public static final String MSG_HEAD_MONITORINFO = "Monitor";
	/**
	 * Message Attribute: Test Result.
	 */
	public static final String MSG_HEAD_TEST_RESULT = "Result";
	/**
	 * Message Attribute: Response.
	 */
	public static final String MSG_HEAD_RESPONSE = "Response";
	/**
	 * Message Attribute: Response.
	 */
	public static final String MSG_HEAD_RESPONSE_DETAIL = "ResponseDetail";

	/**
	 * Message Attribute: Message time-stamp[Type:Long].
	 */
	public static final String MSG_HEAD_TIMESTAMP = "Timestamp";

	/**
	 * Message attribute: File Size, type: long;
	 */
	public static final String MSG_HEAD_FILESIZE = "Filesize";

	/**
	 * Message Attribute: Client target.
	 */
	public static final String MSG_TARGET_CLIENT = "CLIENT";
	/**
	 * Message Attribute: Agent target.
	 */
	public static final String MSG_TARGET_AGENT = "AGENT";
	/**
	 * Message Attribute: Monitor target.
	 */
	public static final String MSG_TARGET_MONITOR = "MONITOR";
	/**
	 * Message Attribute: File Transfer token.
	 */
	public static final String MSG_HEAD_FT_TOKEN = "Token";
	/**
	 * Message Attribute: whether the test is *Success*, it relevant to whether
	 * need to clean up workspace.
	 */
	public static final String MSG_HEAD_TEST_SUCC = "TestSuccess";
	/**
	 * Message Attribute: notification type, including: DEVICE, TASK, TEST,
	 * SYSTEM.
	 */
	public static final String MSG_HEAD_NOTIFICATION_TYPE = "NotificationType";
	/**
	 * Message Attribute: notification action, using along with
	 * MSG_HEAD_NOTIFICATION_TYPE.
	 */
	public static final String MSG_HEAD_NOTIFICATION_ACTION = "NotificationAction";
	/**
	 * Message Attribute: message content type.
	 */
	public static final String MSG_HEAD_CONTENT = "Content";
	/**
	 * Message Attribute: Device condition.
	 */
	public static final String MSG_HEAD_DEVICE_CONDITION = "DeviceCondition";
	/**
	 * Message Attribute: Device Information, json formed.
	 */
	public static final String MSG_HEAD_DEVICE_INFO = "Device";
	/**
	 * Message Attribute: Update time.
	 */
	public static final String MSG_HEAD_UPDATETIME = "UpdateTime";
	/**
	 * Message Attribute: Device Event.
	 */
	public static final String MSG_HEAD_DEVICE_EVENT = "DeviceEvent";
	/**
	 * Message Attribute: File transfer mode;
	 */
	public static final String MSG_HEAD_FT_MODE = "FileTransferMode";
	/**
	 * Message Attribute: Configuration changes.
	 */
	public static final String MSG_HEAD_CONFIG_CHANGES = "ConfigChanges";
	/**
	 * Message Attribute: Plugin Name
	 */
	public static final String MSG_HEAD_PLUGIN_NAME = "PluginName";
	/**
	 * Message Attribute: Plugin Delay Seconds
	 */
	public static final String MSG_HEAD_PLUGIN_DELAY = "PluginDelay";
	/**
	 * Message Attribute: Plugin Attributes
	 */
	public static final String MSG_HEAD_PLUGIN_ATTRIBUTES = "PluginAttributes";
	/**
	 * Message Attribute: Reserved devices, only include deviceID.
	 */
	public static final String MSG_HEAD_RESERVED_DEVICES = "ReservedDevices";
	/*
	 * Notification Modes listed below.
	 */

	/**
	 * Notification Type: DEVICE related notification.
	 */
	public static final int NOTIFICATION_DEVICE = 1;
	/**
	 * Notification Type: TASK related notification.
	 */
	public static final int NOTIFICATION_TASK = 2;
	/**
	 * Notification Type: TEST related notification.
	 */
	public static final int NOTIFICATION_TEST = 3;
	/**
	 * Notification Type: SYSTEM related notification.
	 */
	public static final int NOTIFICATION_SYSTEM = 4;

	/*
	 * Device actions.
	 */
	public static final int ACTION_DEVICE_RELEASE = 0;

	public static final int ACTION_DEVICE_OFFLINE = 1;

	public static final int ACTION_DEVICE_RESERVE = 2;

	public static final int ACTION_DEVICE_REMOVE = 3;

	/*
	 * Task actions
	 */
	public static final int ACTION_TASK_CANCEL = 10;

	/*
	 * Test actions
	 */
	public static final int ACTION_TEST_CANCEL = 20;

	public static final int ACTION_TEST_CHECK = 21;
	/*
	 * System actions
	 */
	public static final int ACTION_AGENT_EXIT = 30;
	/*
	 * Switch agent node to Maintain mode.
	 */
	public static final int ACTION_AGENT_MAINTAIN = 31;
	/*
	 * Switch agent node to normal working mode.
	 */
	public static final int ACTION_AGENT_NORMAL = 32;
	/*
	 * Update agent node config items.
	 */
	public static final int ACTION_AGENT_CONFIG_UPDATE = 33;
	/*
	 * Start http service.
	 */
	public static final int ACTION_AGENT_ENABLE_HTTPSERVER = 34;
	/*
	 * sTOP http service.
	 */
	public static final int ACTION_AGENT_DISABLE_HTTPSERVER = 35;
	/*
	 * Enable assigned plugin
	 */
	public static final int ACTION_AGENT_START_PLUGIN = 36;
	/*
	 * Suspend assigned plugin
	 */
	public static final int ACTION_AGENT_SUSPEND_PLUGIN = 37;
	/*
	 * Disable assigned plugin
	 */
	public static final int ACTION_AGENT_DEACTIVE_PLUGIN = 38;
	/*
	 * Reconfig running Plugin.
	 */
	public static final int ACTION_AGENT_PLUGIN_CONFIG = 39;
	/*
	 * client exit.
	 */
	public static final int ACTION_CLIENT_EXIT = 40;

	// all the task status related message will be sent by Client side cos only
	// client aware whole task status.
	/**
	 * Task status notify number: pending (waiting for started, it means at
	 * least one or more tests in testsuite waiting for agent to be executed.);
	 */
	public static final int TASK_STATUS_PENDING = 0;
	/**
	 * Task status notify number: All the sub tests started.
	 */
	public static final int TASK_STATUS_STARTED = 1;
	/**
	 * Task status notify number: Have been failed, means no more actions will
	 * be done for this task, at least one or more tests didn't have results.
	 */
	public static final int TASK_STATUS_FAILED = -1;
	/**
	 * Task status notify number: Finished, all the tests have been finished and
	 * got results.
	 */
	public static final int TASK_STATUS_FINISHED = 2;

	// all the test status related message will be sent by Agent side cos only
	// agent aware detail test executing status.
	/**
	 * Task status notify number: pending (waiting for started, it means at
	 * least one or more tests in testsuite waiting for agent to be executed.);
	 */
	public static final int TEST_STATUS_PENDING = 0;
	/**
	 * Task status notify number: All the sub tests started.
	 */
	public static final int TEST_STATUS_STARTED = 1;
	/**
	 * Task status notify number: Have been failed, means no more actions will
	 * be done for this task, at least one or more tests didn't have results.
	 */
	public static final int TEST_STATUS_FAILED = -1;
	/**
	 * Task status notify number: Finished, all the tests have been finished and
	 * got results.
	 */
	public static final int TEST_STATUS_FINISHED = 2;

	/**
	 * IoHub name: Task Handler.
	 */
	public static final String IO_TASK_HANDLER = "TaskHandle";

	/**
	 * Agent config file name.
	 */
	public static final String AGENT_CONFIG_FILE = "agent.config";

	/**
	 * Message Queue Service config file, Json formed.
	 */
	public static final String MQ_CONFIG_FILE = "mqservice.config";

	/**
	 * Message service config file name.
	 */
	public static final String MESSAGE_SERVICE_CONFIG_FILE = "messageservice.config";

	/**
	 * Defaule date pattern for general usage.
	 */
	public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

	/**
	 * task cancel succeed
	 */
	public static final int TASK_CANCEL_SUCC = 0;
	/**
	 * task cancel failed.
	 */
	public static final int TASK_CANCEL_FAIL = 1;
	/**
	 * task can't be found
	 */
	public static final int TASK_CANCEL_NOTFOUND = 2;

	/**
	 * Default Task communication socket port number for agent node.
	 */
	public static final int TASK_PORT = 5451;

	public static final int VALIDATION_SUCC = 1;

	public static final int VALIDATION_FAIL = 0;

	public static final int FILE_NOT_EXIST = 0;

	public static final String TRANSFER_ENDED = "{end}";

	public static final int DEFAULT_BUFFER = 8192;

	public static final int RECEIVE_READY = 1;

	public static final int RECEIVE_FINISHED = 2;

	/**
	 * The minimum number of server port number.
	 */
	public static final int MIN_PORT_NUMBER = 1000;

	/**
	 * The maximum number of server port number.
	 */
	public static final int MAX_PORT_NUMBER = 65535;// 49151;

	// ======= Device Attributes ======
	public static final String PLATFORM_ANDROID = "ANDROID";

	public static final String PLATFORM_IOS = "IOS";

	public static final String DEVICE_HOST = "host";

	public static final String DEVICE_IMEI = "imei";

	public static final String DEVICE_SN = "sn";

	public static final String DEVICE_RMCODE = "rmcode";

	public static final String DEVICE_HWTYPE = "hwtype";

	public static final String DEVICE_SWVERSION = "swversion";

	public static final String DEVICE_FINGERPRINT = "fingerprint";

	public static final String DEVICE_PRODUCTCODE = "productcode";

	public static final String DEVICE_SIM1_OPERATOR = "sim1operator";

	public static final String DEVICE_SIM1_OPERATORCODE = "sim1operatorcode";

	public static final String DEVICE_SIM1_OPERATORCOUNTRY = "sim1operatorcountry";

	public static final String DEVICE_SIM2_OPERATOR = "sim2operator";

	public static final String DEVICE_SIM2_OPERATORCODE = "sim2operatorcode";

	public static final String DEVICE_SIM2_OPERATORCOUNTRY = "sim2operatorcountry";

	public static final String DEVICE_SIM1_SIGNAL = "sim1signal";

	public static final String DEVICE_SIM2_SIGNAL = "sim2signal";

	public static final String DEVICE_ID = "id";

	public static final String DEVICE_LANGUAGE = "lan";

	public static final String DEVICE_MEM_SIZE = "memsize";

	public static final String DEVICE_CURRENT_FREQUENCY = "currentfrequency";

	public static final String DEVICE_MAX_FREQUENCY = "maxfrequency";

	public static final String DEVICE_MIN_FREQUENCY = "minfrequency";

	public static final String DEVICE_TAG = "tag";

	public static final String DEVICE_PRODUCT_NAME = "productname";

	public static final String DEVICE_EXCLUDE_PREFIX = "exclude.";

	public static final String DEVICE_HOST_OS_TYPE = "hostOsType";

	public static final String DEVICE_PLATFORM = "platform";

	public static final String DEVICE_PLATFORM_VERSION = "platform_version";

	public static final String DEVICE_MANUFACTURER = "manufacturer";

	public static final String DEVICE_RESOLUTION = "resolution";

	public static final String DEVICE_DPI = "dpi";

	public static final String DEVICE_IP_WLAN = "ip_wlan";

	public static final String DEVICE_ISROOT = "is_root";

	public static final String DEVICE_ATTRIBUTE_WILDCARD = "%*%";

	public static final String DEVICE_ATTRIBUTE_OR = "%OR%";
	
	public static final String DEVICE_ATTRIBUTE_NOT = "%NOT%";

	/*
	 * Some predefined Env parameters.
	 */
	public static final String ENV_DEVICE_MAIN_SN = "DEVICE_MAIN_SN";

	public static final String ENV_DEVICE_REF_SN = "DEVICE_REF_SN";
	/**
	 * several items for describing the config items;
	 */
	public static final String AGENT_CONFIG_DUP_WORKSPACE_BACKUP = "duplicate_workspace_backup";
	/**
	 * DEFAULT DEVICE ATTRIBUTES WHICH AUTOMATICALLY APPEND TO ALL DEVICES ON
	 * CURRENT AGENT.
	 */
	public static final String AGENT_CONFIG_DEVICE_DEFAULT_ATTRS = "default_attributes";
	/**
	 * More detail could be collected from device side.
	 */
	public static final String AGENT_CONFIG_DEVICE_DETECT_MORE_DETAIL = "device_detect_more_detail";
	public static final String AGENT_CONFIG_CHECK_HANG_TEST = "check_hang_test";
	public static final String AGENT_CONFIG_FT_FTP_HOST = "file_transfer_ftp_host";
	public static final String AGENT_CONFIG_FT_FTP_PORT = "file_transfer_ftp_port";
	public static final String AGENT_CONFIG_FT_FTP_USERNAME = "file_transfer_ftp_username";
	public static final String AGENT_CONFIG_FT_FTP_PASSWORD = "file_transfer_ftp_password";
	public static final String AGENT_CONFIG_FT_USE_FTP = "file_transfer_use_ftp";
	public static final String AGENT_CONFIG_HANG_TEST_TIMEOUT = "hang_test_timeout";
	public static final String AGENT_CONFIG_ENABLE_HTTPSERVER = "enable_httpserver";
	public static final String AGENT_CONFIG_ENABLE_PLUGIN = "enable_plugin";
	public static final String AGENT_CONFIG_CHECK_INVALID_RESERVATION_DEVICE = "check_invalid_reservation_device";
	public static final String AGENT_CONFIG_FTILE_TRANSFER_DESCRIPTOR = "ft_channels";
	public static final String AGENT_CONFIG_PLATFORM = "platform";
	public static final String AGENT_CONFIG_DISABLE_HOSTNAME = "disable_hostname";
	public static final String AGENT_CONFIG_DISABLE_ANDROID_DETECTOR = "android_detector_disable";
	public static final String AGENT_CONFIG_DEVICE_DETECT_INTERVAL = "device_detect_interval";
	public static final String AGENT_CONFIG_ENABLE_NOTIFICATION = "enable_notification";
	public static final String AGENT_CONFIG_NOTIFICATION_CHARSET = "notification_charset";
	public static final String AGENT_CONFIG_NOTIFICATION_URL = "notification_url";
	public static final String AGENT_CONFIG_NOTIFICATION_RECEIVERS = "notification_receivers";
	/**
	 * Resource Name of plugin configuration file.
	 */
	public static final String PLUGIN_CONFIG_FILE = "plugins.json";

	/*
	 * File Transfer Related message Header and constants.
	 */
	public static final String MSG_HEAD_FT_TRANSACTION = "FtTransaction";

	public static final String MSG_HEAD_FT_DESCRIPTOR = "FtDescritor";

	public static final String MSG_HEAD_FT_CHANNEL = "FtChannel";

	public static final String MSG_HEAD_FT_CHANNEL_CLASS = "FtChannelClass";

	public static final String MSG_HEAD_FT_TASKID = "FtTaskId";

	public static final String MSG_HEAD_FT_TASK = "FtTask";

	public static final String MSG_HEAD_FT_ARTIFACTS = "FtArtifacts";

	public static final String MSG_HEAD_FT_TARGETFOLDER = "FtTargetFolder";
	
	public static final String MSG_HEAD_FT_ARTIFACT_REMOVE = "FtArtifactRemove";

	public static final int MSG_HEAD_FT_NEGO = 0;

	public static final int MSG_HEAD_FT_NEGO_BACK = 1;

	public static final int MSG_HEAD_FT_PREPARE = 2;

	public static final int MSG_HEAD_FT_CONFIRM = 3;

	public static final int MSG_HEAD_FT_START = 4;

	public static final int MSG_HEAD_FT_SUCC = 5;

	public static final int MSG_HEAD_FT_FAIL = 6;

	public static final int MSG_HEAD_FT_TIMEOUT = 7;

	public static final int MSG_HEAD_FT_CANCEL = 8;
	
	public static final int MSG_HEAD_FT_SKIP = 9;
	
	public static final String OS_WINDOWS_SERIES = "WINDOWS";

	public static final String OS_UNIX_SERIES = "UNIX_LIKE";

	public static final String OS_MAC = "MAC";

	public static final String MQ_URL = "MQ_URL";

	public static final String TIMELINE_PUBLISH_TEST = "test_publish";

	public static final String TIMELINE_DEVICE_RESERVE = "device_selection";

	public static final String TIMELINE_SEND_ARTIFACTS = "artifact_transfer";

	public static final String TIMELINE_FINISH_TEST = "test_execution";

	public static final String AGENT_CONFIG_WORKSPACE_TESTFOLDER_KEEPDAYS = "test_folder_keep_days";

}
