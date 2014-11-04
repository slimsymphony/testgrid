package frank.incubator.testgrid.dm;

import static frank.incubator.testgrid.common.CommonUtils.closeQuietly;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.monitor.ServiceAdapter;

/**
 * Implementation of  Sqlite based Device manager.
 * 
 * @author Wang Frank
 *
 */
public class SqlDeviceManagerImpl implements DeviceManager {
	
	private LogConnector log;
	
	public SqlDeviceManagerImpl() {
		log = LogUtils.get( "sqlite" );
	}
	
	@Override
	public void addDevice( final Device device ) throws DeviceManageException {
		if( device == null || device.getAttribte( "imei" ) == null ) {
			throw new DeviceManageException("Invalid Device provided for Adding.device="+device);
		}
		Connection conn = null;
		PreparedStatement ps = null;
		try {
			@SuppressWarnings( "serial" )
			List<Device> candidates = queryDevices( new HashMap<String,String>(){{this.put( "imei", (String)device.getAttribte( "imei" ) );}} );
			if(candidates!=null && !candidates.isEmpty()) {
				updateDevice( device );
				log.debug( "Device[imei:"+device.getAttribte( "imei" )+"] have already been used, info updated." );
			}else {
				conn = ServiceAdapter.getDbHelper().getConnection();
				ps = conn.prepareStatement( "insert into devices( sn,rmcode,sw,hw,productcode,fingerprint,testnodeid,status,sim1number,sim2number," +
						"role,sim1operator,sim2operator,sim1operatorcode,sim2operatorcode,sim1operatorcountry,sim2operatorcountry," +
						"sim1pin1,sim1pin2,sim2pin1,sim2pin2,sim1puk1,sim1puk2,sim2puk1,sim2puk2,sim1signal,sim2signal,imei) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" );
				inject( device, ps );
				ps.executeUpdate();
				log.debug( "Device[imei:"+device.getAttribte( "imei" )+"] been added." );
			}
		}catch(Exception ex) {
			log.error( "Add Device got exception.", ex );
			throw new DeviceManageException(ex);
		}finally {
			closeQuietly( ps );
			closeQuietly( conn );
		}
	}

	private void inject( Device device, PreparedStatement ps ) throws SQLException {
		ps.setString( 1, (String)device.getAttribte( "sn" ) );
		ps.setString( 2, (String)device.getAttribte( "rmcode" ) );
		ps.setString( 3, (String)device.getAttribte( "sw" ) );
		ps.setString( 4, (String)device.getAttribte( "hw" ) );
		ps.setString( 5, (String)device.getAttribte( "productcode" ) );
		ps.setString( 6, (String)device.getAttribte( "fingerprint" ) );
		if( device.getAttribte( "testnodeid" ) != null )
			ps.setInt( 7, (Integer)device.getAttribte( "testnodeid" ) );
		else
			ps.setInt( 7, 0 );
		if( device.getAttribte( "status" ) == null )
			device.addAttribute( "status", Device.DEVICE_FREE );
		ps.setInt( 8, (Integer)device.getAttribte( "status" ) );
		ps.setString( 9, (String)device.getAttribte( "sim1number" ) );
		ps.setString( 10, (String)device.getAttribte( "sim2number" ) );
		if(device.getAttribte( "role" ) == null)
			device.addAttribute( "role", Device.ROLE_MAIN );
		ps.setInt( 11, (Integer)device.getAttribte( "role" ) );
		ps.setString( 12, (String)device.getAttribte( "sim1operator" ) );
		ps.setString( 13, (String)device.getAttribte( "sim2operator" ) );
		ps.setString( 14, (String)device.getAttribte( "sim1operatorcode" ) );
		ps.setString( 15, (String)device.getAttribte( "sim2operatorcode" ) );
		ps.setString( 16, (String)device.getAttribte( "sim1operatorcountry" ) );
		ps.setString( 17, (String)device.getAttribte( "sim2operatorcountry" ) );
		ps.setString( 18, (String)device.getAttribte( "sim1pin1" ) );
		ps.setString( 19, (String)device.getAttribte( "sim1pin2" ) );
		ps.setString( 20, (String)device.getAttribte( "sim2pin1" ) );
		ps.setString( 21, (String)device.getAttribte( "sim2pin2" ) );
		ps.setString( 22, (String)device.getAttribte( "sim1puk1" ) );
		ps.setString( 23, (String)device.getAttribte( "sim1puk2" ) );
		ps.setString( 24, (String)device.getAttribte( "sim2puk1" ) );
		ps.setString( 25, (String)device.getAttribte( "sim2puk2" ) );
		ps.setString( 26, (String)device.getAttribte( "sim1signal" ) );
		ps.setString( 27, (String)device.getAttribte( "sim2signal" ) );
		ps.setString( 28, (String)device.getAttribte( "imei" ) );
	}

	@Override
	public void removeDevice( Device device ) throws DeviceManageException {
		if(device == null || device.getAttribte( "imei" ) == null)
			return;
		Connection conn = null;
		PreparedStatement ps = null;
		String sql = "delete from devices where imei=?";
		try {
			conn = ServiceAdapter.getDbHelper().getConnection();
			ps = conn.prepareStatement( sql );
			ps.setString( 1, (String)device.getAttribte( "imei" ) );
			ps.executeUpdate();
			log.info( "Deleted device["+device+"] success." );
		}catch(Exception ex) {
			throw new DeviceManageException("Remove device:["+device+"] failed.", ex);
		}finally {
			closeQuietly(ps);
			closeQuietly(conn);
		}
	}

	@Override
	public void updateDevice( Device device ) throws DeviceManageException {
		if( device == null || device.getAttribte( "imei" ) == null ) {
			throw new DeviceManageException("Invalid Device provided for Updating.device="+device);
		}
		Connection conn = null;
		PreparedStatement ps = null;
		String sql = "update devices set sn=?,rmcode=?,sw=?,hw=?,productcode=?,fingerprint=?,testnodeid=?,status=?,sim1number=?,sim2number=?,role=?,sim1operator=?,sim2operator=?,sim1operatorcode=?,sim2operatorcode=?,sim1operatorcountry=?,sim2operatorcountry=?,sim1pin1=?,sim1pin2=?,sim2pin1=?,sim2pin2=?,sim1puk1=?,sim1puk2=?,sim2puk1=?,sim2puk2=?,sim1signal=?,sim2signal=? where imei=?";
		try {
			conn = ServiceAdapter.getDbHelper().getConnection();
			ps = conn.prepareStatement( sql );
			inject(device,ps);
			ps.executeUpdate();
			log.info( "Update device["+device+"] success." );
		}catch(Exception ex) {
			throw new DeviceManageException("Update device:["+device+"] failed.", ex);
		}finally {
			closeQuietly(ps);
			closeQuietly(conn);
		}
	}

	@Override
	public List<Device> queryDevices( Map<String, ? extends Object> conditions ) {
		if(conditions == null)
			conditions = new HashMap<String,Object>();
		List<Device> devices = new ArrayList<Device>();
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		StringBuffer sql = new StringBuffer("select * from devices where ");
		try {
			conn = ServiceAdapter.getDbHelper().getConnection();
			createCondition(sql,conditions);
			sql.append( "1=1" );
			ps = conn.prepareStatement( sql.toString() );
			setCondition(ps,conditions);
			rs = ps.executeQuery();
			while( rs.next() ) {
				devices.add(extract(rs));
			}
		}catch(Exception ex) {
			log.error( "Query Devices got exception, conditions=["+CommonUtils.toJson( conditions )+"]", ex );
		}finally {
			closeQuietly(rs);
			closeQuietly(ps);
			closeQuietly(conn);
		}
		return devices;
	}

	private Device extract( ResultSet rs ) throws SQLException {
		Device device = new Device();
		device.addAttribute( "sn", rs.getString("sn") );
		device.addAttribute( "rmcode", rs.getString("rmcode") );
		device.addAttribute( "sw", rs.getString("sw") );
		device.addAttribute( "hw", rs.getString("hw") );
		device.addAttribute( "productcode", rs.getString("productcode") );
		device.addAttribute( "fingerprint", rs.getString("fingerprint") );
		device.addAttribute( "testnodeid", rs.getInt("testnodeid") );
		device.addAttribute( "status", rs.getInt( "status" ));
		device.addAttribute( "sim1number", rs.getString("sim1number") );
		device.addAttribute( "sim2number", rs.getString("sim2number") );
		device.addAttribute( "role", rs.getInt( "role" ));
		device.addAttribute( "sim1operator", rs.getString("sim1operator") );
		device.addAttribute( "sim2operator", rs.getString("sim2operator") );
		device.addAttribute( "sim1operatorcode", rs.getString("sim1operatorcode") );
		device.addAttribute( "sim2operatorcode", rs.getString("sim2operatorcode") );
		device.addAttribute( "sim1operatorcountry", rs.getString("sim1operatorcountry") );
		device.addAttribute( "sim2operatorcountry", rs.getString("sim2operatorcountry") );
		device.addAttribute( "sim1pin1", rs.getString("sim1pin1") );
		device.addAttribute( "sim1pin2", rs.getString("sim1pin2") );
		device.addAttribute( "sim2pin1", rs.getString("sim2pin1") );
		device.addAttribute( "sim2pin2", rs.getString("sim2pin2") );
		device.addAttribute( "sim1puk1", rs.getString("sim1puk1") );
		device.addAttribute( "sim1puk2", rs.getString("sim1puk2") );
		device.addAttribute( "sim2puk1", rs.getString("sim2puk1") );
		device.addAttribute( "sim2puk2", rs.getString("sim2puk2") );
		device.addAttribute( "sim1signal", rs.getString("sim1signal") );
		device.addAttribute( "sim2signal", rs.getString("sim2signal") );
		device.addAttribute( "imei", rs.getString("imei") );
		return device;
	}

	private void createCondition( StringBuffer sql, Map<String, ? extends Object> conditions ) {
		for( String key : conditions.keySet() ) {
			sql.append( key ).append( "=?" ).append( " and " );
		}
	}

	private void setCondition( PreparedStatement ps, Map<String, ? extends Object> conditions ) throws SQLException {
		int counter = 1;
		Object val = null;
		for( String key : conditions.keySet() ) {
			val = conditions.get( key );
			if( val == null || val instanceof String ) {
				ps.setString( counter++, (String)val );
			}else if( val instanceof Integer) {
				ps.setInt( counter++, (Integer)val );
			}
		}
	}

}
