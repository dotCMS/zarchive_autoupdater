package org.dotcms.autoupdater;

import com.dotmarketing.filters.CMSFilter;
import com.dotmarketing.osgi.GenericBundleActivator;
import org.apache.felix.http.api.ExtHttpService;
import org.dotcms.autoupdater.servlet.UpdateServlet;
import org.dotcms.autoupdater.servlet.UpdateServlet2x;
import org.dotcms.autoupdater.servlet.UpdateUploadServlet;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Created by Jonathan Gamba
 * Date: 9/10/12
 */
public class Activator extends GenericBundleActivator {

    public static final String MAPPING_UPGRADE = "/servlets/upgrade/*";
    public static final String MAPPING_UPGRADE_2X = "/servlets/upgrade2x/*";
    public static final String MAPPING_UPGRADE_UPLOAD = "/servlets/upgradeUpload";

    @Override
    @SuppressWarnings ("unchecked")
    public void start ( BundleContext context ) throws Exception {

        //Initializing services...
        initializeServices( context );

        //Service reference to ExtHttpService that will allows to register servlets and filters
        ServiceReference sRef = context.getServiceReference( ExtHttpService.class.getName() );
        if ( sRef != null ) {

            ExtHttpService service = (ExtHttpService) context.getService( sRef );
            try {
                //Registering servlets
                service.registerServlet( MAPPING_UPGRADE, new UpdateServlet(), null, null );
                service.registerServlet( MAPPING_UPGRADE_2X, new UpdateServlet2x(), null, null );
                service.registerServlet( MAPPING_UPGRADE_UPLOAD, new UpdateUploadServlet(), null, null );
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }

        //Exclude some urls
        CMSFilter.addExclude( "/app" + MAPPING_UPGRADE );
        CMSFilter.addExclude( "/app" + MAPPING_UPGRADE_2X );
        CMSFilter.addExclude( "/app" + MAPPING_UPGRADE_UPLOAD );
    }

    @Override
    public void stop ( BundleContext bundleContext ) throws Exception {

        CMSFilter.removeExclude( "/app" + MAPPING_UPGRADE );
        CMSFilter.removeExclude( "/app" + MAPPING_UPGRADE_2X );
        CMSFilter.removeExclude( "/app" + MAPPING_UPGRADE_UPLOAD );

        //Lets try to clean up everything
        unregisterServices( bundleContext );
    }

}