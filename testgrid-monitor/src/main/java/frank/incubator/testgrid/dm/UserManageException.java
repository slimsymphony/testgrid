package frank.incubator.testgrid.dm;

public class UserManageException extends Exception {

	public UserManageException( String msg ) {
		super( msg );
	}

	public UserManageException( String msg, Throwable t ) {
		super( msg, t );
	}

	public UserManageException( Throwable t ) {
		super( t );
	}
}
