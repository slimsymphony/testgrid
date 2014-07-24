package frank.incubator.testgrid.dm;

import java.util.List;
import java.util.Map;

import frank.incubator.testgrid.common.model.Agent;

/**
 * Test Node Manager API.
 * 
 * @author Wang Frank
 *
 */
public interface AgentManager {

	void addAgent(Agent node) throws DeviceManageException;
	void updateAgent(Agent node) throws DeviceManageException;
	List<Agent> queryAgent(Map<String,? extends Object> conditions);
	void removeAgent(Agent node) throws DeviceManageException;
}
