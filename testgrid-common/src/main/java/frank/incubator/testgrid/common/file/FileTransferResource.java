package frank.incubator.testgrid.common.file;

/**
 * This interface just for
 * 
 * @author Wang Frank
 *
 */
public interface FileTransferResource {
	/**
	 * release the resources held in the file transfer process.(if possible.);
	 */
	public void dispose();

	/**
	 * Return the File transfer mode.
	 * 
	 * @return
	 */
	FileTransferMode getTransferMode();
}
