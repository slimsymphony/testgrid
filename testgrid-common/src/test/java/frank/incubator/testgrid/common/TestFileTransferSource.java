package frank.incubator.testgrid.common;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import frank.incubator.testgrid.common.file.FileTransferMode;
import frank.incubator.testgrid.common.file.FileTransferSource;
import frank.incubator.testgrid.common.file.FileTransferTarget;
import frank.incubator.testgrid.common.file.HttpFileTransferSource;
import frank.incubator.testgrid.common.file.HttpFileTransferTarget;
import frank.incubator.testgrid.common.file.DirectSocketTransferSource;

public class TestFileTransferSource {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main( String[] args ) throws Exception {
		testHttp();
	}
	
	@SuppressWarnings( "serial" )
	public static void testSocket( String[] args ) throws Exception  {
		if( args == null || args.length!=3 ) {
			System.out.println("Args[0]: Host Args[1]: Port Args[2]: token");
			System.exit( 0 );
		}
		String host = args[0];
		int port = Integer.parseInt( args[1] );
		String token = args[2];
		FileTransferSource source = new DirectSocketTransferSource( host, port, System.out );
		source.push( token, new ArrayList<File>() {{this.add( new File("a.txt") );}} );
		source.dispose();
	}
	
	public static void testHttp() throws Exception  {
		FileTransferSource source = new HttpFileTransferSource( FileTransferMode.TARGET_HOST, "http://localhost:5451/upload", System.out );
		String token = CommonUtils.generateToken( 7 );
		File folder = new File( "C:/temp/test" );
		ArrayList<File> files = new ArrayList<File>();
		Map<String,Long> fileList = new HashMap<String,Long>();
		for( File f : folder.listFiles() ) {
			if( f.isFile() ) {
				files.add( f );
				fileList.put( f.getName(), f.length() );
			}
		}
		source.push( token, files );
		
		FileTransferTarget target = new HttpFileTransferTarget( FileTransferMode.TARGET_HOST, null, new File("C:/develop/eclipse-workspaces/java8_workspace/testgrid/testgrid-agent/workspace"), System.out  );
		target.accept( token, fileList, new File("C:/temp/") );
	}

}
