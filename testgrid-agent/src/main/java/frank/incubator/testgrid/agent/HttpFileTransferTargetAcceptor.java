package frank.incubator.testgrid.agent;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;

import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

/**
 * @author Wang Frank
 * 
 */
public final class HttpFileTransferTargetAcceptor extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private LogConnector log = LogUtils.get( "Http" );
	private int maxMemSize = 256 * 1024 * 1024;
	private long maxFileSize = 1024 * 1024 * 1024;
	private static File workspace;

	public synchronized static void setWorkspace( File path ) {
		workspace = path;
	}

	@Override
	public void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		boolean isMultipart = ServletFileUpload.isMultipartContent( request );
		if ( !isMultipart ) {
			if( request.getParameter( "workspace" ) != null ) {
				workspace = new File( request.getParameter( "workspace" ) );
			}else {
				response.sendError( HttpStatus.SC_METHOD_FAILURE, "Invalid request Type[ not multipart]" );
			}
			return;
		}
		DiskFileItemFactory factory = new DiskFileItemFactory();
		// maximum size that will be stored in memory
		factory.setSizeThreshold( maxMemSize );
		// Location to save data that is larger than maxMemSize.
		if( workspace == null )
			workspace=  new File( "workspace" );
		factory.setRepository( workspace );

		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload( factory );
		// maximum file size to be uploaded.
		upload.setSizeMax( maxFileSize );

		try {
			// Parse the request to get file items.
			List<FileItem> fileItems = upload.parseRequest( request );
			// Process the uploaded file items
			Iterator<FileItem> i = fileItems.iterator();
			File file = null;
			FileItem fi = null;
			String filePath = null;
			File tokenFolder = null;
			while ( i.hasNext() ) {
				fi = i.next();
				if ( fi.isFormField() ) {
					String fieldName = fi.getFieldName();
					if( fieldName.equalsIgnoreCase( "token" ) ) {
						filePath = fi.getString();
						tokenFolder = new File(workspace, filePath);
						if( tokenFolder.exists() ) {
							if( tokenFolder.isDirectory() )
								FileUtils.deleteDirectory( tokenFolder );
							else
								tokenFolder.delete();
						}
						tokenFolder.mkdirs();
						log.info( "current task folder is :" + tokenFolder.getAbsolutePath() );
						break;
					}
				}
			}
			
			i = fileItems.iterator();
			while ( i.hasNext() ) {
				fi = i.next();
				if ( !fi.isFormField() ) {
					// Get the uploaded file parameters
					String fileName = fi.getName().replaceAll( "\\\\", "/" );
					// Write the file
					if ( fileName.lastIndexOf( "/" ) >= 0 ) {
						file = new File( tokenFolder, fileName.substring( fileName.lastIndexOf( "/" ) ) );
					} else {
						file = new File( tokenFolder, fileName.substring( fileName.lastIndexOf( "/" ) + 1 ) );
					}
					fi.write( file );
					log.info( "Uploaded Filename: " + fileName );
				}
			}
		} catch ( Exception ex ) {
			log.error( "Got exception when receiving uploding files.", ex );
			response.sendError( HttpStatus.SC_INTERNAL_SERVER_ERROR, "Got exception when receiving uploding files:"+ex.getMessage() );
		}
	}

	@Override
	public void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		if( request.getParameter( "workspace" ) != null ) {
			workspace = new File( request.getParameter( "workspace" ) );
		}else {
			response.sendError( HttpStatus.SC_METHOD_NOT_ALLOWED, "Only POST method supported for File Upload." );
		}
	}

}
