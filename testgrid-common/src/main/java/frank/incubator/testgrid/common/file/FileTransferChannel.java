package frank.incubator.testgrid.common.file;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.model.BaseObject;

/**
 * File Transfer channel defined a contract which represent the way to transfer bunch of files.
 * And it also knows how to select and invoke the concrete FileTransferClass instance to do the actual work.
 * 
 * @author Wang Frank
 * 
 */
public abstract class FileTransferChannel extends BaseObject implements Comparable<FileTransferChannel> {

	private int priority;
	
	protected Map<String,Object> properties;
	
	public FileTransferChannel() {
		properties = new HashMap<String,Object>();
	}
	
	public int getPriority() {
		return priority;
	}

	public void setPriority( int priority ) {
		this.priority = priority;
	}
	
	public Map<String, Object> getProperties() {
		return properties;
	}

	public void setProperties( Map<String, Object> properties ) {
		this.properties = properties;
	}
	
	public void setProperty( String key, Object value ) {
		properties.put( key, value );
	}
	
	public Object getProperty( String key ) {
		return properties.get( key );
	}
	
	@SuppressWarnings( "unchecked" )
	public <T> T getProperty( String key, T defaultValue ) {
		try {
			T ret = (T) properties.get( key );
			if( ret == null)
				return defaultValue;
			return  ret;
		}catch( Exception ex ) {
			ex.printStackTrace();
			return defaultValue;
		}
	}
	
	@SuppressWarnings( "unchecked" )
	public <T> T getProperty( String key, Class<T> clazz ) {
		return (T) properties.get( key );
	}

	/**
	 * Priority defined the order. Higher priority will be sorted as smaller index.
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo( FileTransferChannel o ) {
		if ( o == null )
			throw new NullPointerException( "Compare Object is Null" );
		return ( priority - o.priority );
	}
	
	/**
	 * Self check if the channle was connected.
	 * @return
	 */
	abstract public boolean validate();
	
	/**
	 * When negotiation, check if received channel was connected.
	 * @return
	 */
	abstract public boolean apply();
	
	/**
	 * Send assigned files, with dedicated token.
	 * 
	 * @param token
	 * @param fileList
	 * @param log
	 * @return
	 */
	abstract public boolean send( String token, Collection<File> fileList, LogConnector log );
	
	/**
	 * Receive incoming files, with dedicated token, to assigned local folder.
	 * 
	 * @param token
	 * @param fileList
	 * @param localDestDir
	 * @param log
	 * @return
	 */
	abstract public boolean receive( String token, Map<String, Long> fileList, final File localDestDir, LogConnector log );
}
