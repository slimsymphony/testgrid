package frank.incubator.testgrid.dm;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstraction for DB Helpers.
 * 
 * @author Wang Frank
 *
 */
public interface DbHelper {
	/**
	 * Get Default connection.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public Connection getConnection() throws SQLException;
	
	/**
	 * Self check the database status.
	 */
	public void selfCheck();
	
}
