package frank.incubator.testgrid.common.model;

import java.util.ArrayList;
import java.util.List;

import frank.incubator.testgrid.common.CommonUtils;

/**
 * TestSuite include multiple test instances. It represent a logic Test Task
 * published by a Test Client. So it should have its unique Id. So test monitor
 * take the TestSuite status as a criteria of changing the execution status.
 * 
 * @author Wang Frank
 *
 */
public class TestSuite extends BaseObject {

	public TestSuite() {
		this.id = "TestSuite_" + System.currentTimeMillis() + "_" + CommonUtils.generateToken(5);
		tests = new ArrayList<Test>();
	}

	public TestSuite(String id) {
		this.id = id;
		tests = new ArrayList<Test>();
	}

	private List<Test> tests;

	public TestSuite addTest(Test test) {
		this.tests.add(test);
		return this;
	}

	public void removeTest(Test test) {
		this.tests.remove(test);
	}

	public List<Test> getTests() {
		return tests;
	}

	public void setTests(List<Test> tests) {
		this.tests = tests;
	}

	public boolean contains(Test t) {
		return tests.contains(t);
	}

}
