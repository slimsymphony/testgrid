package frank.incubator.testgrid.common.message;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MessageException extends Exception {
	
	private static final long serialVersionUID = 2547413770226198762L;

	public MessageException( String str ) {
		super( str );
	}

	public MessageException( Throwable t ) {
		super( t );
	}

	public MessageException( String str, Throwable t ) {
		super( str, t );
	}
	
	public String getInfo() {
		StringBuffer sb = new StringBuffer();
		sb.append( "MessageException:{ info=\"" ).append( this.getMessage() ).append( "\", Stack=\"" );
		StringWriter sw = new StringWriter();
		this.printStackTrace( new PrintWriter(sw) );
		sb.append( sw.toString() ).append( "\"}" );
		return sb.toString();
	}
}
