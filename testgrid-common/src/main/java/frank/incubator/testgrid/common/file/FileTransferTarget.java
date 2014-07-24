package frank.incubator.testgrid.common.file;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Represent the File transfer target side. Normally, it is test agent when
 * transferring task artifacts, and should be test client when transferring test
 * report.
 * 
 * @author Wang Frank
 * 
 */
public interface FileTransferTarget extends FileTransferResource {

	/**
	 * Fetch files from remote source. Fetching files from file source (client).
	 * Refer to FileTransferMode.SOURCE_HOST mode.
	 * 
	 * @param token
	 * @param fileList
	 * @param localDestDir
	 * @return
	 * @throws Exception
	 */
	Collection<File> fetch( String token, Map<String, Long> fileList, File localDestDir ) throws Exception;

	/**
	 * Accepting Files be pushed from file source. Refer to
	 * FileTransferMode.SERVER_HOST mode.
	 * 
	 * @param token
	 * @param fileList
	 * @param localDestDir
	 * @return
	 * @throws Exception
	 */
	Collection<File> accept( String token, Map<String, Long> fileList, File localDestDir ) throws Exception;
}
