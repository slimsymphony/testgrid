package frank.incubator.testgrid.agent.test;

public class ReplaceTest {

	public static void main(String[] args) {
		String f = "bug 4.test";
		f = f.replaceAll("\\s", "_");
		System.out.println(f);
	}

}
