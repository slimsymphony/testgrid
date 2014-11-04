package frank.incubator.testgrid.agent.test;

import frank.incubator.testgrid.agent.AgentNode;

public class AgentTest {

	/**
	 * @param args
	 */
	public static void main( String[] args ) {
		//failover:(tcp://10.220.116.240:61616,tcp://10.220.116.242:61616)
		AgentNode agent = new AgentNode("tcp://10.32.207.162:61617");
	}

}
