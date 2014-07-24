package frank.incubator.testgrid.dm;

import static frank.incubator.testgrid.common.CommonUtils.closeQuietly;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.Agent;
import frank.incubator.testgrid.common.model.Agent.AgentStatus;
import frank.incubator.testgrid.monitor.ServiceAdapter;

public class AgentSqlManagerImpl implements AgentManager {

	private static LogConnector log = LogUtils.get( "sqlite" );

	@SuppressWarnings( "serial" )
	@Override
	public void addAgent( Agent node ) throws DeviceManageException {
		if ( node == null ) {
			throw new DeviceManageException( "Invalid Agent provided for Adding.Agent=" + node );
		}

		Connection conn = null;
		PreparedStatement ps = null;
		PreparedStatement ps2 = null;
		ResultSet rs = null;
		try {
			final String host = node.getHost();
			final String ip = node.getIp();
			String sql = "insert into Agents(hostname,ip,port,status,tags,desc) values(?,?,?,?,?,?)";
			conn = ServiceAdapter.getDbHelper().getConnection();
			List<Agent> nodes = queryAgent( new HashMap<String, String>() {
				{
					this.put( "hostName", host );
					this.put( "ip", ip );
				}
			} );
			if ( nodes.size() > 0 ) {
				updateAgent( node );
			} else {
				conn.setAutoCommit( false );
				ps = conn.prepareStatement( sql );
				ps2 = conn.prepareStatement( "SELECT last_insert_rowid()" );
				injectAgent( ps, node );
				ps.executeUpdate();
				rs = ps2.executeQuery();
				if ( rs.next() ) {
					node.setId( rs.getInt( 1 )+"" );
				}
			}
		} catch ( Exception ex ) {
			throw new DeviceManageException( "Add Agent into database failed.node="+node, ex );
		} finally {
			closeQuietly( rs );
			closeQuietly( ps );
			closeQuietly( ps2 );
			closeQuietly( conn );
		}
	}

	private void injectAgent( PreparedStatement ps, Agent node ) throws SQLException {
		ps.setString( 1, node.getHost() );
		ps.setString( 2, node.getIp() );
		ps.setInt( 3, 0 );
		ps.setString( 4, node.getStatus().name() );
		ps.setString( 5, CommonUtils.toString( node.getTags(), "," ) );
		ps.setString( 6, node.getDesc() );
	}

	@Override
	public void updateAgent( Agent node ) throws DeviceManageException {
		Connection conn = null;
		PreparedStatement ps = null;
		String sql = "update Agents set host=?,ip=?,port=?,status=?,tags=?,desc=? where id=?";
		try {
			conn = ServiceAdapter.getDbHelper().getConnection();
			ps = conn.prepareStatement( sql );
			injectAgent(ps,node);
			ps.setInt( 7, CommonUtils.parseInt( node.getId(), 0 ) );
			ps.executeUpdate();
		} catch ( Exception ex ) {
			throw new DeviceManageException("Update Agent information got exception.node="+node, ex);
		} finally {
			closeQuietly( ps );
			closeQuietly( conn );
		}
	}

	@Override
	public List<Agent> queryAgent( Map<String, ? extends Object> conditions ) {
		List<Agent> nodes = new ArrayList<Agent>(); 
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		StringBuffer sql = new StringBuffer("select * from Agents where 1=1 ");
		try {
			conn = ServiceAdapter.getDbHelper().getConnection();
			if(conditions !=null && !conditions.isEmpty()) {
				for(String k : conditions.keySet()) {
					sql.append( "and " ).append( k ).append( "=? " );
				}
			}
			sql.append( " order by id" );
			ps = conn.prepareStatement( sql.toString() );
			int counter = 1;
			if(conditions !=null && !conditions.isEmpty()) {
				for(String k : conditions.keySet()) {
					Object v = conditions.get( k );
					if(v instanceof Integer) {
						ps.setInt( counter++, (Integer)v );
					}else if(v instanceof Long) {
						ps.setLong( counter++, (Long)v );
					}else if(v instanceof Timestamp) {
						ps.setTimestamp( counter++, (Timestamp)v );
					}else {
						ps.setString( counter++, (String)v );
					}
				}
			}
			
			rs = ps.executeQuery();
			while(rs.next()) {
				nodes.add( extractAgent(rs) );
			}
		} catch ( Exception ex ) {
			log.error( "Query Agent got exception.conditions="+CommonUtils.toJson(conditions), ex );
		} finally {
			closeQuietly(rs);
			closeQuietly(ps);
			closeQuietly(conn);
		}
		return nodes;
	}

	private Agent extractAgent( ResultSet rs ) throws SQLException {
		Agent t = new Agent();
		t.setId(rs.getInt( "id" )+"");
		t.setDesc( rs.getString( "desc" ) );
		t.setHost( rs.getString( "hostname" ) );
		t.setIp( rs.getString( "ip" ) );
		t.setStatus( AgentStatus.parse(rs.getString( "status" )) );
		return t;
	}

	@Override
	public void removeAgent( Agent node ) throws DeviceManageException {
		Connection conn = null;
		PreparedStatement ps = null;
		String sql = "delete from Agents where id=?";
		try {
			conn = ServiceAdapter.getDbHelper().getConnection();
			ps = conn.prepareStatement( sql );
			ps.setInt( 1, CommonUtils.parseInt( node.getId(), 0 ) );
			ps.executeUpdate();
		} catch ( Exception ex ) {
			throw new DeviceManageException("Delete Agent information got exception.node="+node, ex);
		} finally {
			closeQuietly( ps );
			closeQuietly( conn );
		}
	}

}
