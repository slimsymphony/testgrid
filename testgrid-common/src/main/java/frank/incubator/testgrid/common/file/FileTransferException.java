package frank.incubator.testgrid.common.file;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Wang Frank
 *
 */
public class FileTransferException extends Exception {

	private static final long serialVersionUID = 5530427023139550313L;
	
	public FileTransferException( String str ) {
		super( str );
	}

	public FileTransferException( Throwable t ) {
		super( t );
	}

	public FileTransferException( String str, Throwable t ) {
		super( str, t );
	}
	
	public String getInfo() {
		StringBuffer sb = new StringBuffer();
		sb.append( "FileTransferException:{ info=\"" ).append( this.getMessage() ).append( "\", Stack=\"" );
		StringWriter sw = new StringWriter();
		this.printStackTrace( new PrintWriter(sw) );
		sb.append( sw.toString() ).append( "\"}" );
		return sb.toString();
	}

}
