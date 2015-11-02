package frank.incubator.testgrid.common;

import java.io.File;
import java.util.HashMap;

import frank.incubator.testgrid.common.file.FileTransferTarget;
import frank.incubator.testgrid.common.file.DirectSocketTransferTarget;

public class TestFileTransferTarget {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args == null || args.length != 4) {
			System.out.println("Args[0]: Port Args[1]: token Args[3]: length");
			System.exit(0);
		}
		final int port = Integer.parseInt(args[0]);
		final String token = args[1];
		final long length = Long.parseLong(args[2]);
		final FileTransferTarget target = new DirectSocketTransferTarget(null, port, System.out);
		Thread t = new Thread("accept") {
			@SuppressWarnings("serial")
			@Override
			public void run() {
				try {
					target.accept(token, new HashMap<String, Long>() {
						{
							this.put("a.txt", length);
						}
					}, new File("c:/temp/"));
					target.dispose();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		t.start();
	}

}
