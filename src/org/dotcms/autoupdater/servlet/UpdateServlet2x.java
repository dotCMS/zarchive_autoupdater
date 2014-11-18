package org.dotcms.autoupdater.servlet;

import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Created by Jonathan Gamba.
 * Date: 4/18/12
 * Time: 5:21 PM
 */
public class UpdateServlet2x extends BaseUpdateServlet {

    private static final long serialVersionUID = 1L;

    /**
     * The purpose of this method is to received a 2x version call and provide an update file if there is one available
     *
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    protected void service ( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {

        config = new UpdateServletLogicConfig( true, false );

        //Getting all the parameters for the autoupdater
        String version = request.getParameter( config.getReqVersion() );
        String build = request.getParameter( config.getReqBuild() );
        String agentVersion = request.getParameter( config.getReqAgentVersion() );
        //String checkValue = request.getParameter( config.getReqCheckValue() );
        String allowTestingBuildsParam = request.getParameter( config.getAllowTestingBuilds() );
        String forSpecificMinorVersion = request.getParameter( config.getSpecificVersion() );//You can specify a minor version to update to

        //Whether to serve the file, or just return what would be served
        /*boolean check = false;
        if ( checkValue != null && checkValue.equalsIgnoreCase( "true" ) ) {
            check = true;
        }*/

        boolean allowTestingBuilds = false;
        if ( allowTestingBuildsParam != null && allowTestingBuildsParam.equalsIgnoreCase( "true" ) ) {
            allowTestingBuilds = true;
        }

        if ( agentVersion != null && agentVersion.length() > 0 ) {
            //Find and serve a build file and data for a given autoupdater version and build
            serveAgentFile( response, version, agentVersion, allowTestingBuilds );
        } else {
            //Serve a build file for a major, minor and build version
            provideUpdateInfo( response, version, forSpecificMinorVersion, build, allowTestingBuilds );
        }
    }

    /**
     * Serve a build file for a major, minor and build version
     *
     * @param response
     * @param version
     * @param forSpecificMinorVersion
     * @param build
     * @param allowTestingBuilds
     * @throws java.io.IOException
     */
    private void provideUpdateInfo ( HttpServletResponse response, String version, String forSpecificMinorVersion, String build, boolean allowTestingBuilds ) throws IOException {

        UpdateServletLogic logic = new UpdateServletLogic( config );

        List<Contentlet> list = null;
        if ( UtilMethods.isSet( forSpecificMinorVersion ) ) {

            //Search the contentlet for this minor version
            Contentlet specific = logic.getVersion( forSpecificMinorVersion, allowTestingBuilds );
            if ( specific != null ) {
                //Check if we have the right version
                if ( UtilMethods.compareVersions( specific.getStringProperty( "minor" ), version ) ) {
                    list = new java.util.ArrayList<Contentlet>();
                    list.add( specific );
                } else {
                    Logger.info( org.dotcms.autoupdater.servlet.UpdateServlet.class, "Could not find version = " + forSpecificMinorVersion );
                    response.sendError( 204 );

                    return;
                }
            }
        } else {
            //Search for build contentlets. This build contentlet version will be the greater build version than the current version we provide
            list = logic.getContentlets( version, allowTestingBuilds );

            if (list != null && list.size() > 0) {
                //Check if we have the right version
                Contentlet specific = list.get(0);
                if (specific != null) {
                    if (!UtilMethods.compareVersions(specific.getStringProperty("minor"), version)) {
                        Logger.info( org.dotcms.autoupdater.servlet.UpdateServlet.class, "Could not find version = " + forSpecificMinorVersion);
                        response.sendError(204);

                        return;
                    }
                }
            }

        }

        //For a given build contentlet this method will verify for a download update file link and if everything is ok with it will return it
        String buildDownloadLink = logic.processAndVerifyDownloadLink( list, version, build );
        Contentlet buildContentlet = null;
        if ( list != null && list.size() > 0 ) {
            buildContentlet = list.get( 0 );
        }
        String md5 = "";
        String newMinor = "";
        String prettyName = "";
        String downloadLink = "";
        if ( buildContentlet != null ) {
            md5 = buildContentlet.getStringProperty( config.getFilesMD5FieldName() );
            newMinor = buildContentlet.getStringProperty( config.getFilesMinorFieldName() ) + "_" + buildContentlet.getStringProperty( config.getBuildNumberField() );
            prettyName = buildContentlet.getStringProperty( config.getFilesPrettyNameFieldName() );
            downloadLink = buildDownloadLink;
        }
        if ( buildDownloadLink == null ) {
            Logger.info( org.dotcms.autoupdater.servlet.UpdateServlet.class, "Could not find file for version = " + version + " and build " + build );
            response.sendError( logic.getRetCode() );
        } else {
            serveHeaders( response, downloadLink, md5, newMinor, prettyName );
        }
    }

}