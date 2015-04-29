package frank.incubator.testgrid.monitor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import frank.incubator.testgrid.common.model.Device;
import frank.incubator.testgrid.dm.DeviceManageException;
import frank.incubator.testgrid.dm.DeviceManager;
import frank.incubator.testgrid.dm.SqlDeviceManagerImpl;
import frank.incubator.testgrid.monitor.ServiceAdapter;

@FixMethodOrder( MethodSorters.NAME_ASCENDING )
@SuppressWarnings( "serial" )
public class SqliteTest {

	private static DeviceManager dm = new SqlDeviceManagerImpl();
	
	/**
	 * @param args
	 */
	public static void main( String[] args ) {
		testInit();
	}
	
	public static void testInit() {
		try {
			Connection conn = ServiceAdapter.getDbHelper().getConnection();
			conn.close();
		} catch ( SQLException e ) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void test1Add() {
		Device d = new Device();
		d.addAttribute( "imei", "004402475336743" );
		d.addAttribute( "sn", "1acc4f6c" );
		try {
			dm.addDevice( d );
		} catch ( DeviceManageException e ) {
			e.printStackTrace();
			Assert.fail();
		}
		
	}
	
	@Test
	public void test2Query() {
		List<Device> ds =  dm.queryDevices( new HashMap<String,Object>(){{this.put("imei","004402475336743");this.put( "status", Device.DEVICE_FREE );}} );
		Assert.assertEquals( 1, ds.size() );
	}
	
	@Test
	public void test3Update() {
		Device d = dm.queryDevices( new HashMap<String,Object>(){{this.put("imei","004402475336743");this.put( "status", Device.DEVICE_FREE );}} ).get( 0 );
		d.addAttribute( "sw", "0.1402.2" );
		try {
			dm.updateDevice( d );
			d = dm.queryDevices( new HashMap<String,Object>(){{this.put("imei","004402475336743");this.put( "status", Device.DEVICE_FREE );}} ).get( 0 );
			Assert.assertEquals( "0.1402.2", d.getAttribute( "sw" ) );
		} catch ( DeviceManageException e ) {
			e.printStackTrace();
			Assert.fail();
		}
	}
	
	@Test
	public void test4Remove() {
		Device d = dm.queryDevices( new HashMap<String,Object>(){{this.put("imei","004402475336743");this.put( "status", Device.DEVICE_FREE );}} ).get( 0 );
		try {
			dm.removeDevice( d );
			Assert.assertEquals(0,dm.queryDevices( new HashMap<String,Object>(){{this.put("imei","004402475336743");this.put( "status", Device.DEVICE_FREE );}}).size() );
		} catch ( DeviceManageException e ) {
			e.printStackTrace();
			Assert.fail();
		}
	}
}
