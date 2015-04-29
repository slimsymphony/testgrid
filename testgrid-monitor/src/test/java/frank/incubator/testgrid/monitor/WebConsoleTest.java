package frank.incubator.testgrid.monitor;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.model.Agent;
import frank.incubator.testgrid.common.model.Agent.AgentStatus;
import frank.incubator.testgrid.common.model.Client;
import frank.incubator.testgrid.common.model.Task;
import frank.incubator.testgrid.common.model.Test;
import frank.incubator.testgrid.common.model.Test.Phase;
import frank.incubator.testgrid.common.model.Test.Target;
import frank.incubator.testgrid.common.model.TestSuite;
import frank.incubator.testgrid.monitor.MonitorCache;
import frank.incubator.testgrid.monitor.WebConsoleBootstrap;

public class WebConsoleTest {

	/**
	 * @param args
	 */
	public static void main( String[] args ) {
		/*Agent agent = new Agent();
		agent.setDesc( "Agent Desc" );
		agent.setHost( CommonUtils.getHostName() );
		agent.setIp( CommonUtils.getValidHostIp() );
		agent.setId( "Agent_"+CommonUtils.generateToken( 5 ) );
		agent.setStatus( AgentStatus.MAINTAIN );
		agent.addTags( "test", "pilot", "dummy" );
		System.out.println(agent);
		MonitorCache.updateAgent( agent );
		
		Task task = new Task();
		task.setId( "task123" );
		task.setTaskOwner( "Frank" );
		TestSuite ts = new TestSuite( "Suite" );
		task.setTestsuite( ts );
		System.out.println(task);
		
		MonitorCache.updateTask( task );
		
		Client client = new Client();
		client.setHost( CommonUtils.getHostName() );
		client.setId( "Client_"+CommonUtils.generateToken( 5 ) );
		client.setTask( task );
		System.out.println(client);
		
		MonitorCache.updateClient( client );
		
		Test test = new Test();
		test.setExecutorApplication( "Python" );
		test.setExecutorScript( "run.py" );
		test.setId( "Test_"+CommonUtils.generateToken( 10 ) );
		test.setPhase( Phase.UNKNOWN );
		test.setResultsFilename( "result.zip" );
		test.setUrl( "http://localhost:8080/test" );
		test.setStartTime( System.currentTimeMillis() );
		test.setTarget( Target.DEVICE );
		test.setWorkspacePath( "/home/testgrid/workspace/"+test.getId()+"/" );
		System.out.println(test);
		
		MonitorCache.updateTest( test );
		*/
		if(args == null)
			args = new String[] {"uri=tcp://10.32.207.162:61616"};
		WebConsoleBootstrap instance = new WebConsoleBootstrap( args );
		instance.setDaemon( true );
		instance.start();
	}

}
