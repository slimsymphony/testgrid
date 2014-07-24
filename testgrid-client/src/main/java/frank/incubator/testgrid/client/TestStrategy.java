package frank.incubator.testgrid.client;

/**
 * This *Strategy* defined the different operation model for test client when met limited resource.<br/>
 * - <b>EXEC_ASAP</b> : this strategy needs the task to be executed asap. So when met limited resources, test client will try to merge all the tests of testsuites into available packages which can be executed asap.<br/>
 * - <b>SPLIT_AMAP</b>: this strategy always long-term executions, so need to be run concurrently and separately, so when met limited resources, the test client will waiting for pending resources, and keep waiting. ideally, the performance of this strategy is same as serial running task.<br/>
 * - <b>MODERATE</b>: this strategy was the combined strategy of above 2. There were a default timeout. during the timeout, once resource available, one test will be executed asap, but when timeout touched, all the rest tests will be merged together and running on next available devices.<br/>
 * 
 * @author Wang Frank
 *
 */
public enum TestStrategy {
	EXEC_ASAP, SPLIT_AMAP, MODERATE; 
}
