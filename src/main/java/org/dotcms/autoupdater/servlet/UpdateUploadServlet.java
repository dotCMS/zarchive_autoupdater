package org.dotcms.autoupdater.servlet;

import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotHibernateException;
import com.dotmarketing.util.Logger;
import com.liferay.portal.model.User;
import com.dotcms.repackage.org.apache.commons.fileupload.FileItem;
import com.dotcms.repackage.org.apache.commons.fileupload.FileItemFactory;
import com.dotcms.repackage.org.apache.commons.fileupload.FileUploadException;
import com.dotcms.repackage.org.apache.commons.fileupload.disk.DiskFileItemFactory;
import com.dotcms.repackage.org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;


public class UpdateUploadServlet extends BaseUpdateServlet {
	
	class UpdateData {
		private File file;
		private String version;
		private String major;
		private String build;
		private String comment;
		
		public String getComment() {
			return comment;
		}
		public void setComment(String comment) {
			this.comment = comment;
		}

		public File getFile() {
			return file;
		}
		public void setFile(File file) {
			this.file = file;
		}
		public String getMajor() {
			return major;
		}
		public void setMajor(String major) {
			this.major = major;
		}
		public String getVersion() {
			return version;
		}
		public void setVersion(String version) {
			this.version = version;
		}
		
		public String getBuild() {
			return build;
		}
		public void setBuild(String build) {
			this.build = build;
		}
	}


	private static final long serialVersionUID = 1L;
		
	  protected void service(HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException{		  

		  //Get the credentials
		  Logger.info(UpdateUploadServlet.class, "Getting credentials");
		  User auth=getCredentials(request, response);
		  if (auth!=null) {
			  UpdateServletLogicConfig config=new UpdateServletLogicConfig(false, false);
			  UpdateServletLogic l=new UpdateServletLogic(config);
			  try{
			   Logger.info(UpdateUploadServlet.class, "Checking for write permissions for user "+auth.getUserId());	  
			  if (l.hasUploadRights(auth)) {
				  Logger.info(UpdateUploadServlet.class, "Getting upload data");	  
				  UpdateData data=getUpdateData(request);
				  if (data!=null) {
					  Logger.info(UpdateUploadServlet.class, "Got file " + (data.getFile()!=null?data.getFile().getName():"no file uploaded")+ " and version: " + data.getVersion() );	  
					  try {
						  HibernateUtil.startTransaction();
						  l.uploadUpdateFile(data,auth);
						  
						  response.setStatus(201);
					  } catch (Exception e) {
						  try {
							HibernateUtil.rollbackTransaction();
						} catch (DotHibernateException e1) {
							Logger.debug(UpdateUploadServlet.class,"DotHibernateException: " + e1.getMessage(),e1);
						}
						  Logger.error(UpdateUploadServlet.class,"Exception: " + e.getMessage(),e);
					  } finally {
						  try {
							HibernateUtil.commitTransaction();
						} catch (DotHibernateException e) {
							Logger.debug(UpdateUploadServlet.class,"DotHibernateException: " + e.getMessage(),e);
						}
						  DbConnectionFactory.closeConnection();
					  }
					  
				  }else{
					  Logger.info(UpdateUploadServlet.class, "No update data submitted");	  
				  }
			  } else {
				  Logger.error(UpdateUploadServlet.class,"User " + auth + " has no upload permissions");
				  response.sendError(403);
			  }
			  }catch(Exception e){
				  Logger.error(UpdateUploadServlet.class,  e.getMessage(),e);
				  response.sendError(403);
			  }
		  }else{
			  Logger.info(UpdateUploadServlet.class, "No user found for credentials");
		  }

	  }
	  
	  private UpdateData getUpdateData (HttpServletRequest request)  {
		  UpdateData data=new UpdateData();
		  FileItemFactory factory = new DiskFileItemFactory();
		// Create a new file upload handler
		  ServletFileUpload upload = new ServletFileUpload(factory);
		  
		  // Parse the request
		  try {
			List<FileItem> items = upload.parseRequest(request);
			  Iterator<FileItem> iter = items.iterator();
			  while (iter.hasNext()) {
			      FileItem item = (FileItem) iter.next();

			      if (item.isFormField()) {
			          processFormField(item,data);
			      } else {
			          data.setFile(processUploadedFile(item));
			      }
			  }
		} catch (FileUploadException e) {
			Logger.debug(UpdateUploadServlet.class,"FileUploadException: " + e.getMessage(),e);
		} catch (Exception e) {
			Logger.debug(UpdateUploadServlet.class,"Exception: " + e.getMessage(),e);
		}
		  return data;

	  }
	  
	  private void processFormField(FileItem item,UpdateData data) {
		  String name = item.getFieldName();
		  String value = item.getString();
		  if (name.equalsIgnoreCase("version")) {
			  data.setVersion(value);
		  }
		  if (name.equalsIgnoreCase("major")) {
			  data.setMajor(value);
		  }
		  if (name.equalsIgnoreCase("build")) {
			  data.setBuild(value);
		  }
		  if (name.equalsIgnoreCase("comment")) {
			  data.setComment(value);
		  }
		  

	  }
	  
	  private File  processUploadedFile(FileItem item) throws Exception {
		  Logger.info(UpdateUploadServlet.class, "Processing uploaded file");	 
		  File uploadedFile=File.createTempFile("dotcms_update_", "zip");
		  item.write(uploadedFile);
		  return uploadedFile;

	  }
	  
	
	
}
