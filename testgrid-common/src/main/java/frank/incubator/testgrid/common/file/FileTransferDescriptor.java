package frank.incubator.testgrid.common.file;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.model.BaseObject;

/**
 * Descriptor for all the FileTransfer Channels.
 * Represent a FT end-point host information.
 * Also was used be communicate with other FT end-point.
 * The channels collection will always keep order by priority.
 * 
 * @author Wang Frank
 * 
 */
public class FileTransferDescriptor extends BaseObject {
	public FileTransferDescriptor() {
		this.setId( "FileTransferDescriptor" );
		channels = new ArrayList<FileTransferChannel>();
		host = CommonUtils.getHostName();
	}
	
	public FileTransferDescriptor( FileTransferChannel ...chs ) {
		this.setId( "FileTransferDescriptor" );
		if( chs != null )
			channels = Arrays.asList( chs );
		else
			channels = new ArrayList<FileTransferChannel>();
		host = CommonUtils.getHostName();
	}
	
	private String host;

	public String getHost() {
		return host;
	}

	public void setHost( String host ) {
		this.host = host;
	}

	public List<FileTransferChannel> getChannels() {
		return channels;
	}

	public void setChannels( List<FileTransferChannel> channels ) {
		this.channels = channels;
		Collections.sort( this.channels );
	}

	private List<FileTransferChannel> channels;
	
	public void addChannel( FileTransferChannel channel ) {
		channels.add( channel );
		Collections.sort( channels );
	}
	
	public void addChannels( FileTransferChannel ... cls ) {
		if( cls != null )
			for( FileTransferChannel cl : cls )
				channels.add( cl );
		Collections.sort( channels );
	}
	
	public void removeChannel( FileTransferChannel channel ) {
		channels.remove( channel );
	}
}
