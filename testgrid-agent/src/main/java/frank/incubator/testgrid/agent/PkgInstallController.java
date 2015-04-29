package frank.incubator.testgrid.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.Constants;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;

public class PkgInstallController extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private LogConnector log = LogUtils.get("Http");
	private int maxMemSize = 256 * 1024 * 1024;
	private long maxFileSize = 1024 * 1024 * 1024;
	private static File workspace;

	public synchronized static void setWorkspace(File path) {
		workspace = new File(path, "install");
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		boolean result = false;
		File pkg = null;
		String sn = null;
		boolean isMultipart = ServletFileUpload.isMultipartContent(request);
		if (!isMultipart) {
			if (request.getParameter("workspace") != null) {
				workspace = new File(request.getParameter("workspace"));
			} else {
				response.sendError(HttpStatus.SC_METHOD_FAILURE, "Invalid request Type[ not multipart]");
			}
			return;
		}
		DiskFileItemFactory factory = new DiskFileItemFactory();
		// maximum size that will be stored in memory
		factory.setSizeThreshold(maxMemSize);
		// Location to save data that is larger than maxMemSize.
		if (workspace == null)
			workspace = new File("workspace");
		factory.setRepository(workspace);

		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload(factory);
		// maximum file size to be uploaded.
		upload.setSizeMax(maxFileSize);
		String token = CommonUtils.generateToken(5);
		try {
			// Parse the request to get file items.
			List<FileItem> fileItems = upload.parseRequest(request);
			// Process the uploaded file items
			Iterator<FileItem> i = fileItems.iterator();
			File file = null;
			FileItem fi = null;
			String filePath = null;
			File tokenFolder = null;
			while (i.hasNext()) {
				fi = i.next();
				if (fi.isFormField()) {
					String fieldName = fi.getFieldName();
					if (fieldName.equalsIgnoreCase("token")) {
						filePath = fi.getString();
						tokenFolder = new File(workspace, filePath);
						if (tokenFolder.exists()) {
							if (tokenFolder.isDirectory())
								FileUtils.deleteDirectory(tokenFolder);
							else
								tokenFolder.delete();
						}
						tokenFolder.mkdirs();
						log.info("current task folder is :" + tokenFolder.getAbsolutePath());
						break;
					}else if(fieldName.equalsIgnoreCase("sn")) {
						sn = fi.getString().trim();
					}
				}
			}
			
			if(tokenFolder == null || !tokenFolder.exists()) {
				if(sn != null && !sn.trim().isEmpty()) {
					tokenFolder = new File(workspace,sn);
				}else {
					tokenFolder = new File(workspace,CommonUtils.generateToken(6));
				}
				if(!tokenFolder.exists())
					tokenFolder.mkdirs();
			}

			i = fileItems.iterator();
			while (i.hasNext()) {
				fi = i.next();
				if (!fi.isFormField()) {
					// Get the uploaded file parameters
					String fileName = fi.getName();//.replaceAll("\\\\", "/");
					String ext = fileName.substring(fileName.lastIndexOf(".")+1);
					//fileName = fileName.replaceAll("\\s", "_");
					fileName = token + "_pkg." + ext;
					// Write the file
					/*if (fileName.lastIndexOf("/") >= 0) {
						file = new File(tokenFolder, fileName.substring(fileName.lastIndexOf("/")));
					} else {
						file = new File(tokenFolder, fileName.substring(fileName.lastIndexOf("/") + 1));
					}*/
					file = new File(tokenFolder, fileName);
					fi.write(file);
					log.info("Uploaded Filename: " + fileName);
					if(file != null && file.exists() && file.length()>0)
						pkg = file;
				}
			}
		} catch (Exception ex) {
			log.error("Got exception when receiving uploding files.", ex);
			response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
					"Got exception when receiving uploding files:" + ex.getMessage());
		}
		
		if(pkg != null && sn != null) {
			// begin to install
			// 1. create install description
			// 2. wait for result file
			// 3. if over timeout return fail to user
			// 4. if result file exists, give response back to user
			File taskFolder = new File(workspace, "tasks");
			File resultFolder = new File(workspace, "results"); 
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(new File(taskFolder, sn + ".properties"));
				StringBuilder sb = new StringBuilder();
				sb.append("path=").append(pkg.getName());
				fos.write(sb.toString().getBytes("UTF-8"));
				fos.flush();
				CommonUtils.closeQuietly(fos);
				if(!resultFolder.exists()) {
					resultFolder.mkdirs();
				}
				
				long start = System.currentTimeMillis();
				long timeout = Constants.ONE_MINUTE * 5;
				File resultFile = null;
				while((System.currentTimeMillis() - start) <timeout) {
					for(File f : resultFolder.listFiles()) {
						if(f.isFile() && f.getName().equalsIgnoreCase(token + "_" + sn + ".properties")) {
							resultFile = f;
							break;
						}
					}
					if(resultFile == null)
						TimeUnit.SECONDS.sleep(5);
					else
						break;
				}
				if(resultFile != null) {
					Properties props = new Properties();
					props.load(new StringReader(CommonUtils.readFileContent(resultFile)));
					if(props.containsKey("success")) {
						result = CommonUtils.parseBoolean(props.get("success").toString());
					}
				}
			}catch(Exception ex) {
				log.error("Write Task file failed", ex);
			}
		}
		Writer w = null;
		try {
			response.setCharacterEncoding("UTF-8");
			response.setContentType("application/json; charset=UTF-8");
			w = response.getWriter();
			w.write("{\"success\":"+result+"}");
		}finally {
			CommonUtils.closeQuietly(w);
			FileUtils.deleteQuietly(pkg);
		}
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (request.getParameter("workspace") != null) {
			workspace = new File(request.getParameter("workspace"));
		} else {
			response.sendError(HttpStatus.SC_METHOD_NOT_ALLOWED, "Only POST method supported for File Upload.");
		}
	}
}
