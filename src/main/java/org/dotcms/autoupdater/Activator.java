package org.dotcms.autoupdater;

import com.dotmarketing.filters.CMSFilter;
import com.dotmarketing.osgi.GenericBundleActivator;
import org.apache.felix.http.api.ExtHttpService;
import org.dotcms.autoupdater.servlet.UpdateServlet1x;
import org.dotcms.autoupdater.servlet.UpdateServlet;
import org.dotcms.autoupdater.servlet.UpdateUploadServlet;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import com.dotcms.repackage.org.tuckey.web.filters.urlrewrite.NormalRule;

/**
 * Created by Jonathan Gamba
 * Date: 9/10/12
 */
public class Activator extends GenericBundleActivator {

    public static final String MAPPING_UPGRADE = "/servlets/upgrade";
    public static final String MAPPING_UPGRADE_2X = "/servlets/upgrade2x";
    public static final String MAPPING_UPGRADE_3X = "/servlets/upgrade3x";
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
                service.registerServlet( MAPPING_UPGRADE, new UpdateServlet1x(), null, null );
                service.registerServlet( MAPPING_UPGRADE_2X, new UpdateServlet(), null, null );
                service.registerServlet( MAPPING_UPGRADE_3X, new UpdateServlet(), null, null );
                service.registerServlet( MAPPING_UPGRADE_UPLOAD, new UpdateUploadServlet(), null, null );
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }

        //Exclude some urls
        CMSFilter.addExclude( "/app" + MAPPING_UPGRADE );
        CMSFilter.addExclude( "/app" + MAPPING_UPGRADE_2X );
        CMSFilter.addExclude( "/app" + MAPPING_UPGRADE_3X );
        CMSFilter.addExclude( "/app" + MAPPING_UPGRADE_UPLOAD );

        //Add some url Rewrite rules
        addRule( "oldAutoUpdaterRule2X", "^" + MAPPING_UPGRADE_2X + "$", "/app" + MAPPING_UPGRADE_2X );
        addRule( "oldAutoUpdaterRule3X", "^" + MAPPING_UPGRADE_3X + "$", "/app" + MAPPING_UPGRADE_3X );
        addRule( "oldAutoUpdaterRule", "^" + MAPPING_UPGRADE + "$", "/app" + MAPPING_UPGRADE );
        addRule( "oldAutoUpdaterRuleUpload", "^" + MAPPING_UPGRADE_UPLOAD + "$", "/app" + MAPPING_UPGRADE_UPLOAD );
    }

    /**
     * Creates and add tuckey rules
     *
     * @param name
     * @param from
     * @param to
     * @throws Exception
     */
    private void addRule ( String name, String from, String to ) throws Exception {

        //Create the tuckey rule
        NormalRule rule = new NormalRule();
        rule.setFrom( from );
        rule.setToType( "forward" );
        rule.setTo( to );
        rule.setName( name );

        //And add the rewrite rule
        addRewriteRule( rule );
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