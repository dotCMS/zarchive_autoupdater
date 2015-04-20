package org.dotcms.autoupdater.servlet;

import com.dotcms.repackage.com.oreilly.servlet.Base64Decoder;
import com.dotmarketing.cms.factories.PublicCompanyFactory;
import com.dotmarketing.cms.factories.PublicEncryptionFactory;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.util.Logger;
import com.liferay.portal.ejb.UserLocalManagerUtil;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.User;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Enumeration;
import java.util.List;

public abstract class BaseUpdateServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    protected UpdateServletLogicConfig config;

    protected User getCredentials ( HttpServletRequest request, HttpServletResponse response ) throws IOException {

        String a = request.getHeader( "Authorization" );

        Enumeration e = request.getHeaderNames();
        while ( e.hasMoreElements() ) {
            String s = ( String ) e.nextElement();
            Logger.debug( this, "Header: " + s + " " + request.getHeader( s ) );
        }
        if ( a == null ) {
            response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            response.setHeader( "WWW-Authenticate", "BASIC realm=\"Private\"" );
            return null;
        }

        String userpassEncoded = a.substring( 6 );
        String userpassDecoded = Base64Decoder.decode( userpassEncoded );
        int index = userpassDecoded.indexOf( ":" );
        String user = userpassDecoded.substring( 0, index );
        String pass = userpassDecoded.substring( index + 1 );

        User auth = auth( user, pass );
        if ( auth == null ) {
            response.sendError( 403 );
        }

        return auth;
    }

    protected User auth ( String username, String pass ) {

        try {
            Company comp = PublicCompanyFactory.getDefaultCompany();
            User user = UserLocalManagerUtil.getUserByEmailAddress( comp.getCompanyId(), username.toLowerCase() );

            if ( user.getPassword().equals( pass ) || user.getPassword().equals( PublicEncryptionFactory.digestString( pass ) ) ) {
                Logger.debug( this, "Login succeed" );
                return user;
            } else {
                Logger.debug( this, "Login failed: " + username );

            }
        } catch ( Exception e ) {
            Logger.debug( this, "Login failed: " + username, e );
        }
        return null;
    }

    /**
     * This method pretend to verify and serve if there is something to serve an update for the autoupdater client, so will find and serve a build file and data for a given autoupdater version and build
     *
     * @param response
     * @param version
     * @param build
     * @param allowTestingBuilds
     * @return
     * @throws java.io.IOException
     */
    protected boolean serveAgentFile ( HttpServletResponse response, String version, String build, boolean allowTestingBuilds ) throws IOException {

        UpdateServletLogic logic = new UpdateServletLogic( config );

        //Search for build contentlets. This build contentlet version will be the greater build version than the current version we provide
        List<Contentlet> builds = logic.getContentlets( UpdateServletLogic.AUTO_UPDATER_PREFIX + version, allowTestingBuilds );

        Contentlet buildContentlet = null;
        if ( builds != null && builds.size() > 0 ) {
            buildContentlet = builds.get( 0 );
        }

        //For a given build contentlet this method will return a build file
        File buildFile = logic.getFile( builds, version, build );

        //Getting some properties to return
        String md5 = "";
        String newMinor = "";
        String prettyName = "";
        if ( buildContentlet != null ) {
            md5 = buildContentlet.getStringProperty( config.getFilesMD5FieldName() );
            newMinor = buildContentlet.getStringProperty( config.getFilesMinorFieldName() ) + "_" + buildContentlet.getStringProperty( config.getBuildNumberField() );
            prettyName = buildContentlet.getStringProperty( config.getFilesPrettyNameFieldName() );
        }

        boolean success = false;
        if ( buildFile == null ) {
            Logger.info( this, "Could not find autoupdater file for version = " + version + " and build " + build );
            response.sendError( logic.getRetCode() );
        } else {
            serveHeaders( response, null, md5, newMinor, prettyName );
            success = serveFile( response, buildFile );
        }

        return success;
    }

    /**
     * Serve a given build file
     *
     * @param response
     * @param buildFile
     * @return
     * @throws java.io.IOException
     */
    protected boolean serveFile ( HttpServletResponse response, File buildFile ) throws IOException {

        long _fileLength = buildFile.length();
        response.setHeader( "Content-Length", String.valueOf( _fileLength ) );
        ServletOutputStream out = null;
        FileChannel from = null;
        ByteBuffer bb = null;

        try {
            out = response.getOutputStream();
            from = new FileInputStream( buildFile ).getChannel();
            bb = ByteBuffer.allocateDirect( 10 );

            int numRead = 0;
            while ( numRead >= 0 ) {
                // read() places read bytes at the buffer's position so the
                // position should always be properly set before calling read()
                // This method sets the position to 0
                bb.rewind();

                // Read bytes from the channel
                numRead = from.read( bb );

                // The read() method also moves the position so in order to
                // read the new bytes, the buffer's position must be set back to
                // 0
                bb.rewind();

                // Read bytes from ByteBuffer;
                for ( int i = 0; i < numRead; i++ ) {
                    out.write( bb.get() );
                }
            }

        } catch ( Exception e ) {
            Logger.warn( this, "Error occurred serving file = " + buildFile.getAbsolutePath(), e );
        } finally {
            if ( bb != null )
                bb.clear();
            if ( from != null )
                from.close();
            if ( out != null )
                out.close();
        }

        return true;
    }

    protected boolean serveHeaders ( HttpServletResponse response, String downloadLink, String md5, String minor, String prettyName ) {

        if ( downloadLink != null ) {
            response.setHeader( "Download-Link", downloadLink );
        }
        if ( md5 != null && !md5.isEmpty() ) {
            response.setHeader( "Content-MD5", md5 );
        }
        if ( minor != null ) {
            response.setHeader( "Minor-Version", minor );
        }
        if ( prettyName != null ) {
            response.setHeader( "Pretty-Name", prettyName );
        }
        return true;
    }

}