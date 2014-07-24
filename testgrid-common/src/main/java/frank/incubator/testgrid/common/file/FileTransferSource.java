package frank.incubator.testgrid.common.file;

import java.io.File;
import java.util.Collection;

public interface FileTransferSource extends FileTransferResource{
	
	/**
	 * Publish Files to be transferred with token. And waiting for incoming request for these files.
	 * Refer to FileTransferMode.SOURCE_HOST mode.
	 * Which means the server was located on File Source side(test client);
	 * 
	 * @param token
	 * @param fileList
	 * @throws Exception
	 */
	void publish( String token, Collection<File> fileList ) throws Exception;
	
	/**
	 * Push Files to target actively.
	 * Refer to FileTransferMode.TARGET_HOST mode.
	 * Which means the server was located on Target side(test agent);
	 * 
	 * @param token
	 * @param fileList
	 * @throws Exception
	 */
	void push( String token, Collection<File> fileList ) throws Exception;
}
