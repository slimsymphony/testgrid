package frank.incubator.testgrid.monitor;

import frank.incubator.testgrid.dm.AgentManager;
import frank.incubator.testgrid.dm.AgentSqlManagerImpl;
import frank.incubator.testgrid.dm.DbHelper;
import frank.incubator.testgrid.dm.DeviceManager;
import frank.incubator.testgrid.dm.SqlDeviceManagerImpl;
import frank.incubator.testgrid.dm.SqliteHelper;

public class ServiceAdapter {

	private static DbHelper dbInstance;
	private static DeviceManager dmInstance;
	private static AgentManager agentInstance;

	public synchronized static DbHelper getDbHelper() {
		if ( dbInstance == null )
			dbInstance = new SqliteHelper();
		return dbInstance;
	}

	public synchronized static DeviceManager getDmManager() {
		if ( dmInstance == null )
			dmInstance = new SqlDeviceManagerImpl();
		return dmInstance;
	}

	public synchronized static AgentManager getAgentManager() {
		if ( agentInstance == null )
			agentInstance = new AgentSqlManagerImpl();
		return agentInstance;
	}
}
