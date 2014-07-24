package frank.incubator.testgrid.client;

public enum TaskStatus {
	INIT, PUBLISHED, ACCEPTED, RESERVED, STARTED, RUNNING, FINSHED, CANCELLED, FAILED, UNKNOWN;

	public static TaskStatus parse( String str ) {
		for ( TaskStatus e : TaskStatus.values() ) {
			if ( e.name().equalsIgnoreCase( str ) ) {
				return e;
			}
		}
		return null;
	}
}
