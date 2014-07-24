package frank.incubator.testgrid.agent.device;

import static frank.incubator.testgrid.common.CommonUtils.exec;
import static frank.incubator.testgrid.common.CommonUtils.isWindows;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import frank.incubator.testgrid.agent.AgentNode;
import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.model.Device;

public final class AndroidDeviceDetector extends DeviceDetector {

	final static String WORKDIR = "ADB_HOME";

	public AndroidDeviceDetector( File workspace, DeviceManager deviceExplorer, long scanInterval ) {
		super( workspace, deviceExplorer, scanInterval );
	}

	@Override
	public void refresh() {
		try {
			if ( isWindows() )
				parseProducts( exec( "%ADB_HOME%/adb devices", null ) );
			else
				parseProducts( exec( "$ADB_HOME/adb devices", null ) );
		} catch ( Exception ex ) {
			log.error( "Got exception when refersh device info.", ex );
		}
	}

	public void parseProducts( String str ) {
		Collection<Device> devices = new ArrayList<Device>();
		BufferedReader br = new BufferedReader( new StringReader( str ) );
		String line = null;
		String sn = null;
		try {
			line = br.readLine();
			while ( ( line = br.readLine() ) != null ) {
				try {
					line = line.trim();
					if ( line.indexOf( "device" ) > 0 && line.endsWith( "device" ) ) {
						sn = line.substring( 0, line.indexOf( "device" ) ).trim();
					} else if( line.indexOf( "recovery" ) > 0 && line.endsWith( "recovery" ) ) {
						sn = line.substring( 0, line.indexOf( "recovery" ) ).trim();
					}
					if( sn != null ) {
						Device p = getDeviceInfo( sn );
						loadUserDefined( p );
						devices.add( p );
						log.debug( "Append product:" + sn + "\n" + p.toString() );
					}
				} catch ( Exception e ) {
					log.error( "Parse product sn failed. line=" + line, e );
				}
			}

			this.deviceManager.sync( devices );
		} catch ( Exception ex ) {
			log.error( "Parse products Info met error.", ex );
		}
	}

	private Map<String, String> parseProps( String str ) {
		Map<String, String> props = new HashMap<String, String>();
		BufferedReader br = new BufferedReader( new StringReader( str ) );
		String line = null;
		try {
			line = br.readLine();
			while ( ( line = br.readLine() ) != null ) {
				try {
					line = line.trim();
					if ( line.indexOf( "[" ) < 0 )
						continue;
					if ( line.indexOf( "[" ) == line.lastIndexOf( "[" ) || line.indexOf( "]" ) == line.lastIndexOf( "]" ) )
						continue;
					String key = line.substring( line.indexOf( "[" ) + 1, line.indexOf( "]" ) );
					String value = line.substring( line.lastIndexOf( "[" ) + 1, line.lastIndexOf( "]" ) );
					props.put( key, value );
				} catch ( Exception e ) {
					log.error( "Parse product props failed. line=" + line, e );
				}
			}
		} catch ( Exception ex ) {
			log.error( "Parse product props met error.", ex );
		}
		return props;
	}

	private String adb() {
		if ( isWindows() )
			return "%ADB_HOME%\\adb";
		else
			return "$ADB_HOME/adb";
	}

	private Device getDeviceInfo( String sn ) throws Exception {
		AndroidDevice p = new AndroidDevice();
		p.addAttribute( Constants.DEVICE_SN, sn );
		AndroidDeviceStatus stat = p.getStatus();

		String propstr = exec( adb() + " -s " + sn + " shell getprop", null );
		Map<String, String> props = parseProps( propstr );
		String imei = "";
		try {
			if( CommonUtils.isWindows() ) {
				imei = CommonUtils.grep( exec( adb() + " -s " + sn + " shell dumpsys iphonesubinfo", null ), "Device ID", false );
			} else {
				imei = exec( adb() + " -s " + sn + " shell dumpsys iphonesubinfo|grep 'Device ID'", null );
			}
			
			if( imei.contains( "=" ) )
				imei = imei.split( "=" )[1].trim();
			else
				imei = "";
		} catch ( Exception e ) {
			log.error( "Parse [" + sn + "] Imei failed. Imei=" + imei, e );
		}

		p.addAttribute( Constants.DEVICE_IMEI, imei );
		String rmcode = props.get( "ro.product.name" );
		if ( rmcode == null || rmcode.isEmpty() ) {
			rmcode = props.get( "ro.product.rmcode" );
			if ( rmcode == null || rmcode.isEmpty() ) {
				//rmcode = props.get( "ro.product.name" );
				rmcode = "UNKNOWN";
			}
		}
		p.addAttribute( Constants.DEVICE_RMCODE, rmcode );
		p.addAttribute( Constants.DEVICE_HWTYPE, props.get( "ro.product.hw.id" ) );
		p.addAttribute( Constants.DEVICE_SWVERSION, props.get( "apps.setting.product.swversion" ) );
		p.addAttribute( Constants.DEVICE_FINGERPRINT, props.get( "ro.build.fingerprint" ) );
		p.addAttribute( Constants.DEVICE_PRODUCTCODE, props.get( "ro.ril.product.code" ) );
		p.addAttribute( Constants.DEVICE_LANGUAGE, props.get( "persist.sys.language" ) );
		p.addAttribute( Constants.DEVICE_PRODUCT_NAME, props.get( "ro.build.product" ) );
		boolean sim1ok = false;
		boolean sim2ok = false;
		if ( props.get( "gsm.sim.state" ) != null && !props.get( "gsm.sim.state" ).equals( "ABSENT" ) ) {
			sim1ok = true;
			p.addAttribute( Constants.DEVICE_SIM1_OPERATOR, props.get( "gsm.sim.operator.alpha" ) );
			p.addAttribute( Constants.DEVICE_SIM1_OPERATORCODE, props.get( "gsm.sim.operator.numeric" ) );
			p.addAttribute( Constants.DEVICE_SIM1_OPERATORCOUNTRY, props.get( "gsm.sim.operator.iso-country" ) );
		}

		if ( props.get( "gsm.sim2.state" ) != null && !props.get( "gsm.sim2.state" ).equals( "ABSENT" ) ) {
			p.addAttribute( Constants.DEVICE_SIM2_OPERATOR, props.get( "gsm.sim2.operator.alpha" ) );
			p.addAttribute( Constants.DEVICE_SIM2_OPERATORCODE, props.get( "gsm.sim2.operator.numeric" ) );
			p.addAttribute( Constants.DEVICE_SIM2_OPERATORCOUNTRY, props.get( "gsm.sim2.operator.iso-country" ) );
		}
		
		// Get More detail info and device status if agent permitted.
		if ( CommonUtils.parseBoolean( ( String ) AgentNode.getConfig( Constants.AGENT_CONFIG_DEVICE_DETECT_MORE_DETAIL ) ) ) {
			/*String memSize = exec( adb() + " -s " + sn + " shell cat /proc/meminfo|head -1", null );
			if ( memSize != null ) {
				memSize = memSize.toLowerCase().trim();
				if ( memSize.indexOf( "memtotal:" ) >= 0 && memSize.indexOf( "kb" ) >= 0 ) {
					if ( memSize != null && !memSize.isEmpty() )
						p.addAttribute( Constants.DEVICE_MEM_SIZE, memSize.replaceAll( "memtotal:", "" ).replaceAll( "kb", "" ).trim() );
				}
			}*/
			String memInfo = exec( adb() + " -s " + sn + " shell cat /proc/meminfo", null );
			String line = null;
			BufferedReader br = new BufferedReader( new StringReader( memInfo ) );
			try {
				while( ( line = br.readLine() ) != null ) {
					line = line.trim().replaceAll( "kB", "" ).trim();
					if( line.indexOf( "MemTotal" ) >= 0 ) {
						int total = CommonUtils.parseInt( line.replaceAll( "MemTotal:", "" ).trim(), 0 );
						p.addAttribute( Constants.DEVICE_MEM_SIZE, total );
						stat.addMemInfo( "MemTotal", total );
					}else {
						String[] ar = null;
						if( line.indexOf( ":" ) >0 ) {
							ar = line.split( ":" );
							String trim = ar[1].trim();
							stat.addMemInfo( ar[0], CommonUtils.parseInt( trim, 0 ) );
						}
					}
				}
			} catch( Exception ex ) {
				log.error( "Extract meminfo failed.", ex );
			}
			String minFrequency = exec( adb() + " -s " + sn + " shell cat \"/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq\"", null );
			if ( minFrequency != null )
				p.addAttribute( Constants.DEVICE_MIN_FREQUENCY, minFrequency.trim() );
			String maxFrequency = exec( adb() + " -s " + sn + " shell cat \"/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq\"", null );
			if ( maxFrequency != null )
				p.addAttribute( Constants.DEVICE_MAX_FREQUENCY, maxFrequency.trim() );
			String currentFrequency = exec( adb() + " -s " + sn + " shell cat \"/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq\"", null );
			if ( currentFrequency != null ) {
				//p.addAttribute( Constants.DEVICE_CURRENT_FREQUENCY, currentFrequency.trim() );
				stat.setFrequency( CommonUtils.parseLong( currentFrequency.trim(), 0L ) );
			}

			if ( sim1ok || sim2ok ) {
				String signalStrength = "";
				if( CommonUtils.isWindows() ) {
					signalStrength = CommonUtils.grep( exec( adb() + " -s " + sn + " shell dumpsys telephony.msim.registry", null ), "mSignalStrength", false );
				} else {
					signalStrength = exec( adb() + " -s " + sn + " shell dumpsys telephony.msim.registry|grep mSignalStrength", null );
				}
				br = new BufferedReader( new StringReader( signalStrength.trim() ) );
				int index = 0;
				while ( ( line = br.readLine() ) != null ) {
					line = line.trim();
					if ( line.indexOf( "mSignalStrength" ) >= 0 ) {
						line = line.substring( line.indexOf( ":" ) + 1 ).trim();
						line = line.substring( 0, line.indexOf( " " ) );
						try {
							int aus = Integer.parseInt( line );
							int dBm = 0;
							if ( aus != 99 )
								dBm = aus * 2 - 113;
							line = String.valueOf( dBm ) + "dBm";
						} catch ( Exception e ) {
							line = line + "aus";
						}
						if ( sim1ok && index == 0 ) {
							//p.addAttribute( Constants.DEVICE_SIM1_SIGNAL, line );
							stat.setSim1Signal( line );
						}
						else if ( sim2ok && index == 1 ) {
							//p.addAttribute( Constants.DEVICE_SIM2_SIGNAL, line );
							stat.setSim2Signal( line );
						}
						index++;
					}
				}
			}
			
			br = new BufferedReader(new StringReader(exec( adb() + " -s " + sn + " shell top -n 1 -m 30", null )));
			boolean started = false;
			List<String> items = null;
			List<String> vals = null;
			String item = null;
			int pid = 0;
			Map<String,Object> procInfo = null;
			while( ( line = br.readLine() ) != null ) {
				line = line.trim();
				if( !started ) {
					if( line.indexOf("PID") >=0 ) {
						started = true;
						items = CommonUtils.splitBlanks( line.toLowerCase() );
						items.remove( "pcy" );
						continue;
					}
				} else {
					if( line.isEmpty() )
						continue;
					vals = CommonUtils.splitBlanks( line );
					//line = line.substring( 0, line.indexOf( "%" ) ).trim();
					pid = CommonUtils.parseInt( vals.get( 0 ), 0 );//CommonUtils.parseInt( line.substring( 0, line.indexOf( " " ) ) , 0 );
					stat.getTopCpuConsumingProcs().add( pid );
					//float load = CommonUtils.parseFloat( line.substring( line.lastIndexOf( " " ) + 1 ) , 0F );
					procInfo = stat.getProcesses().get( pid );
					if( procInfo == null )
						procInfo = new HashMap<String,Object>();
					for( int i=0; i<items.size(); i++ ) {
						item = items.get( i );
						if( item.equals( "pid" ) ){
							continue;
						}
						if( vals.get( i ).equals( "bg" ) || vals.get( i ).equals( "fg" ) )
							continue;
						procInfo.put( item, vals.get( i ) );
					}
					stat.getProcesses().put( pid, procInfo );
				}
			}
			
			br = new BufferedReader(new StringReader(exec( adb() + " -s " + sn + " shell procrank", null )));
			started = false; 
			while( ( line = br.readLine() ) != null ) {
				line = line.trim();
				if( !started ) {
					if( line.indexOf("PID") >=0 ) {
						started = true;
						items = CommonUtils.splitBlanks( line.toLowerCase() );
					}
				} else {
					if( line.indexOf( "------" ) >=0 )
						break;
					if( line.isEmpty() )
						continue;
					vals = CommonUtils.splitBlanks( line );
					pid = CommonUtils.parseInt( vals.get( 0 ), 0 );
					stat.getTopMemConsumingProcs().add( pid );
					procInfo = stat.getProcesses().get( pid );
					if( procInfo == null )
						procInfo = new HashMap<String,Object>();
					for( int i=0; i<items.size(); i++ ) {
						item = items.get( i );
						if( item.equals( "pid" ) ){
							continue;
						}
						procInfo.put( item, vals.get( i ) );
					}
					stat.getProcesses().put( pid, procInfo );
				}
			}
		}
		return p;
	}
}
