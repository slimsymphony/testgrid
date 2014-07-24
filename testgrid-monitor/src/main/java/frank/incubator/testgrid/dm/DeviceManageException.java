package frank.incubator.testgrid.dm;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DeviceManageException extends Exception {

	private static final long serialVersionUID = 4792850301727992763L;

	public DeviceManageException( String msg ) {
		super( msg );
	}

	public DeviceManageException( String msg, Throwable t ) {
		super( msg, t );
	}

	public DeviceManageException( Throwable t ) {
		super( t );
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append( "MessageException:{ info=\"" ).append( this.getMessage() ).append( "\", Stack=\"" );
		StringWriter sw = new StringWriter();
		this.printStackTrace( new PrintWriter(sw) );
		sb.append( sw.toString() ).append( "\"}" );
		return sb.toString();
	}
}
