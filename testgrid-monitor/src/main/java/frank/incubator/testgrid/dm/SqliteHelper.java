package frank.incubator.testgrid.dm;

import static frank.incubator.testgrid.common.CommonUtils.closeQuietly;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

public class SqliteHelper implements DbHelper{
	private static final String dsFileName = "cloudservice.db";
	private final Map<String, String> initTables = new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;
		{
			this.put( "devices", "CREATE TABLE devices (imei VARCHAR(20)  NULL PRIMARY KEY,sn vaRCHAR(10)  NOT NULL,rmcode vARCHAR(10)  NULL,sw vARCHAR(20)  NULL,hw vARCHAR(20)  NULL,productcode vARCHAR(10)  NULL,fingerprint vARCHAR(50)  NULL,testnodeid inTEGER  NULL,status INTEGER DEFAULT '0' NULL,sim1number varchar(20)  NULL,sim2number vARCHAR(20)  NULL,role integer DEFAULT '0' NULL,sim1operator varCHAR(20)  NULL,sim2operator vARCHAR(20)  NULL,sim1operatorcode vaRCHAR(10)  NULL,sim2operatorcode vaRCHAR(10)  NULL,sim1operatorcountry varchar(10)  NULL,sim2operatorcountry vARCHAR(10)  NULL,sim1pin1 vaRCHAR(10)  NULL,sim1pin2 varCHAR(10)  NULL,sim2pin1 vaRCHAR(10)  NULL,sim2pin2 vARCHAR(10)  NULL,sim1puk1 vARCHAR(10)  NULL,sim1puk2 vARCHAR(10)  NULL,sim2puk1 vARCHAR(10)  NULL,sim2puk2 vARCHAR(10)  NULL,sim1signal varchar(20) NULL,sim2signal varchar(20) NULL)" );
			this.put( "testnodes", "CREATE TABLE testnodes (id INTEGER  NOT NULL PRIMARY KEY,hostname vARCHAR(40)  NULL,ip vARCHAR(30)  NULL,status VARCHAR(20)  NULL,tags VARCHAR(50)  NULL,desc VARCHAR(2000)  NULL)" );
			this.put( "usage_history", "CREATE TABLE usage_history (event_time timestamp DEFAULT (datetime('now','localtime')) NULL,event_type VARCHAR(20)  NULL,event_detail VARCHAR(4000)  NULL)" );
                        this.put( "users", "CREATE TABLE users (username VARCHAR(50) NOT NULL PRIMARY KEY, userrole VARCHAR(50)  NULL)" );
		}
	};
	private LogConnector log;
	
	public SqliteHelper() {
		log = LogUtils.get( "sqlite" );
		try {
			Class.forName( "org.sqlite.JDBC" );
			File ds = new File( System.getProperty( "user.dir" ), dsFileName );
                        log.info("DB path: " + System.getProperty( "user.dir" ));
                        log.info("DB file: " + dsFileName);
			if ( !ds.exists() ) {
				ds.createNewFile();
			}
			selfCheck();
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}

	@Override
	public Connection getConnection() throws SQLException {
		Connection connection = null;
		try {
			connection = DriverManager.getConnection( "jdbc:sqlite:" + dsFileName );
		} catch ( SQLException e ) {
			e.printStackTrace();
		}
		return connection;
	}

	private void createTable( Connection conn, String tableName ) {
		PreparedStatement ps = null;
		String sql = initTables.get( tableName );
		if ( sql != null ) {
			try {
				ps = conn.prepareStatement( sql );
				ps.executeUpdate();
			} catch ( Exception e ) {
				log.error( "create table " + tableName + " failed.", e );
			} finally {
				closeQuietly( ps );
			}
		}
	}

	@Override
	public void selfCheck() {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		Map<String, Boolean> checklist = new HashMap<String, Boolean>() {
			private static final long serialVersionUID = 1L;
			{
				this.put( "devices", false );
				this.put( "testnodes", false );
				this.put( "usage_history", false );
                                this.put( "users", false );
			}
		};
		String sql = "SELECT name FROM sqlite_master WHERE type='table'";
		try {
			conn = getConnection();
			ps = conn.prepareStatement( sql );
			rs = ps.executeQuery();
			String tn = null;
			while ( rs.next() ) {
				tn = rs.getString( 1 );
				if ( tn != null && checklist.containsKey( tn.toLowerCase() ) ) {
					checklist.put( tn.toLowerCase(), true );
				}
			}
			rs.close();
			ps.close();
			for ( String key : checklist.keySet() ) {
				if ( !checklist.get( key ) ) {
					createTable( conn, key );
				}
			}
		} catch ( Exception ex ) {
			log.error( "SqliteHelper SelfCheck got exception.", ex );
		} finally {
			closeQuietly( rs );
			closeQuietly( ps );
			closeQuietly( conn );
		}
	}
}
