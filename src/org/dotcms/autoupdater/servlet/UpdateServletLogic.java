package org.dotcms.autoupdater.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.queryParser.ParseException;
import org.dotcms.autoupdater.servlet.UpdateUploadServlet.UpdateData;
import org.xbill.DNS.DClass;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.ReverseMap;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import com.dotmarketing.beans.Identifier;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.cache.StructureCache;
import com.dotmarketing.cache.WorkingCache;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotHibernateException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.factories.PublishFactory;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.folders.model.Folder;
import com.dotmarketing.portlets.structure.factories.RelationshipFactory;
import com.dotmarketing.portlets.structure.model.Relationship;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.model.User;

public class UpdateServletLogic {

    public static String AUTO_UPDATER_PREFIX = "autoupdater_";

    private UpdateServletLogicConfig config;
    private int retCode;

    public UpdateServletLogic ( UpdateServletLogicConfig config ) {
        this.config = config;
    }

    public int getRetCode () {
        return retCode;
    }

    public String getMD5 ( String version, String build, boolean allowTestingBuilds ) {
        Logger.info( this.getClass(), "Getting MD5 of file for version = " + version + " and build " + build );
        String md5 = null;
        List<Contentlet> list = getContentlets( version, allowTestingBuilds );
        if ( list != null && list.size() > 0 ) {
            md5 = list.get( 0 ).getStringProperty( config.getFilesMD5FieldName() );
        }


        return md5;
    }

    public String getMinor ( String version, String build, boolean allowTestingBuilds ) {
        Logger.info( this.getClass(), "Looking for latest minor version" );

        String minorRet = null;
        List<Contentlet> list = getContentlets( version, allowTestingBuilds );
        if ( list != null && list.size() > 0 ) {
            Contentlet cont = list.get( 0 );
            minorRet = cont.getStringProperty( config.getFilesMinorFieldName() ) + "_" + cont.getStringProperty( config.getBuildNumberField() );
        }


        return minorRet;
    }

    public String getPrettyName ( String version, String build, boolean allowTestingBuilds ) {

        String nameRet = null;
        List<Contentlet> list = getContentlets( version, allowTestingBuilds );
        if ( list != null && list.size() > 0 ) {
            nameRet = list.get( 0 ).getStringProperty( config.getFilesPrettyNameFieldName() );
        }
        return nameRet;
    }

    /**
     * Returns the build Contentlet of a given minor version
     *
     * @param minorVersion
     * @param allowTestingBuilds
     * @return
     */
    public Contentlet getVersion ( String minorVersion, boolean allowTestingBuilds ) {

        try {

            ContentletAPI cAPI = APILocator.getContentletAPI();
            User sysUser = APILocator.getUserAPI().getSystemUser();

            String query = "+structureName:" + config.getFilesStructure() + " +"
                    + config.getFilesStructure() + "." + config.getFilesMinorFieldName() + ":" + minorVersion + ""
                    + " -" + config.getFilesStructure() + "." + config.getFilesMinorFieldName() + ":*autoupdater_*"
                    + " +deleted:false +live:true";

            if ( !allowTestingBuilds ) {
                query += " +" + config.getFilesStructure() + "." + config.getReleasedField() + ":true";
            }

            List<Contentlet> versionList = cAPI.search( query, 1, 0, config.getFilesStructure() + "." + config.getBuildNumberField() + " desc", sysUser, false );
            if ( !versionList.isEmpty() ) {

                Contentlet minorVersionContentlet = versionList.get( 0 );
                if ( minorVersionContentlet.getBoolProperty( "released" ) || allowTestingBuilds ) {
                    return minorVersionContentlet;
                }
            }

        } catch ( DotDataException e ) {
            Logger.debug( UpdateServletLogic.class, "DotDataException: " + e.getMessage(), e );
        } catch ( DotSecurityException e ) {
            Logger.debug( UpdateServletLogic.class, "DotSecurityException: " + e.getMessage(), e );
        }

        return null;
    }

    /**
     * Returns build contentlets. This build contentlet version will be the greater build version than the current version we provide
     *
     * @param version
     * @param allowTestingBuilds
     * @return
     */
    public List<Contentlet> getContentlets ( String version, boolean allowTestingBuilds ) {

        List<Contentlet> fileList = null;

        try {
            ContentletAPI cAPI = APILocator.getContentletAPI();
            User sysUser = APILocator.getUserAPI().getSystemUser();
            String query;

            //Look for the major version greater than the major version of this current version
            Contentlet majorVersion = getMajorVersion( version, allowTestingBuilds );

            if ( majorVersion != null ) {

                if ( UtilMethods.isSet( majorVersion.getIdentifier() ) ) {
                    String condition = "";
                    if ( !allowTestingBuilds ) {
                        condition = " +" + config.getFilesStructure() + "." + config.getReleasedField() + ":true";
                    }

                    if ( version.startsWith( AUTO_UPDATER_PREFIX ) ) {
                        condition += " +" + config.getFilesStructure() + "." + config.getFilesMinorFieldName() + ":" + AUTO_UPDATER_PREFIX + "*";
                    }

                    Logger.info( this.getClass(), "looking for file of lastest minor version of Major version = " + majorVersion.getStringProperty( "major" ) );

                    //Look for the file with the correct major, and greatest minor version
                    query = "+" + config.getVersionRelationship() + ":" + majorVersion.getIdentifier() + condition + " +deleted:false +live:true";

                    Logger.info( this.getClass(), "File/client query: " + query );
                    fileList = cAPI.search( query, 1, 0, config.getFilesStructure() + "." + config.getBuildNumberField() + " desc", sysUser, false );
                }
            }

        } catch ( DotDataException e ) {
            Logger.debug( UpdateServletLogic.class, "DotDataException: " + e.getMessage(), e );
        } catch ( DotSecurityException e ) {
            Logger.debug( UpdateServletLogic.class, "DotSecurityException: " + e.getMessage(), e );
        } catch ( ParseException e ) {
            Logger.debug( UpdateServletLogic.class, "ParseException: " + e.getMessage(), e );
        }

        Logger.debug( this.getClass(), "returning from getContentlets" );
        return fileList;
    }

    /**
     * For a given version and build number this method will return a build file
     *
     * @param version
     * @param build
     * @param allowTestingBuilds
     * @return
     */
    public File getFile ( String version, String build, boolean allowTestingBuilds ) {

        File buildFile = null;
        retCode = 404;

        try {
            //Search for build contentlets. This build contentlet version will be the greater build version than the current version we provide
            List<Contentlet> fileList = getContentlets( version, allowTestingBuilds );
            if ( fileList != null && fileList.size() > 0 ) {
                //Find and returns a build file for a given build contentlet
                buildFile = getFile( fileList.get( 0 ), version, build );
            }
        } catch ( DotDataException e ) {
            Logger.error( this.getClass(), "DotDataException: " + e.getMessage(), e );
        } catch (DotStateException e) {
			Logger.error(UpdateServletLogic.class,e.getMessage(),e);
		} catch (DotSecurityException e) {
			Logger.error(UpdateServletLogic.class,e.getMessage(),e);
		}

        return buildFile;
    }

    /**
     * For a given build contentlet this method will return a build file
     *
     * @param buildContentles
     * @param version
     * @param build
     * @return
     */
    public File getFile ( List<Contentlet> buildContentles, String version, String build ) {

        File buildFile = null;
        retCode = 404;

        try {
            if ( buildContentles != null && buildContentles.size() > 0 ) {
                //Find and returns a build file for a given build contentlet
                buildFile = getFile( buildContentles.get( 0 ), version, build );
            }
        } catch ( DotDataException e ) {
            Logger.error( this.getClass(), "DotDataException: " + e.getMessage(), e );
        } catch (DotStateException e) {
			Logger.error(UpdateServletLogic.class,e.getMessage(),e);
		} catch (DotSecurityException e) {
			Logger.error(UpdateServletLogic.class,e.getMessage(),e);
		}

        return buildFile;
    }

    /**
     * Returns the major version greater than the major version of this given version with at least one released minor version
     *
     * @param version
     * @param allowTestingBuilds
     * @return
     * @throws com.dotmarketing.exception.DotDataException
     * @throws com.dotmarketing.exception.DotSecurityException
     * @throws org.apache.lucene.queryParser.ParseException
     */
    private Contentlet getMajorVersion ( String version, boolean allowTestingBuilds ) throws DotDataException, DotSecurityException, ParseException {

        Logger.info( this.getClass(), "Looking for Major version of minor = " + version );

        ContentletAPI cAPI = APILocator.getContentletAPI();
        User sysUser = APILocator.getUserAPI().getSystemUser();

        //Get major for this version
        String query = "+structureName:" + config.getFilesStructure() + " +"
                + config.getFilesStructure() + "." + config.getFilesMinorFieldName() + ":*" + version + "*"
                + " +deleted:false +live:true";
        List<Contentlet> versionList = cAPI.search( query, 1, 0, "", sysUser, false );

        Contentlet currentMajorVersion = null;
        if ( !versionList.isEmpty() ) {//If we found something...

            Contentlet currentVersion = versionList.get( 0 );
            if ( UtilMethods.isSet( currentVersion.getIdentifier() ) ) {
                //Search the contentlet for this major version
                query = "+" + config.getVersionRelationship() + ":" + currentVersion.getIdentifier() + " +deleted:false +live:true";
                versionList = cAPI.search( query, 1, 0, "", sysUser, false );
                if ( !versionList.isEmpty() ) {
                    currentMajorVersion = versionList.get( 0 );
                }
            }
        }

        //OK.., if we found the major version of the given version we must try to find now the major version greater than this major version
        if ( currentMajorVersion != null && UtilMethods.isSet( currentMajorVersion.getIdentifier() ) ) {

            Logger.info( this.getClass(), "Found Major version = " + currentMajorVersion.getStringProperty( "major" ) );
            Logger.info( this.getClass(), "Looking for a Major version greater than " + currentMajorVersion.getStringProperty( "major" ) + " with at least one related minor version" );

            //Check to see if there is a major version greater than this major with at least one released minor version
            query = "+structureName:" + config.getVersionStructure() + " +deleted:false +working:true"
                    + " -" + config.getVersionStructure() + "." + config.getVersionMajorField() + ":*" + currentMajorVersion.getStringProperty( config.getVersionMajorField() ) + "*";
            if ( !config.is2XBuild() ) {
                query += " -" + config.getVersionStructure() + "." + config.getVersionMajorField() + ":2.*";
            }

            versionList = cAPI.search( query, 0, 0, config.getVersionStructure() + "." + config.getVersionMajorField() + " desc", sysUser, false );

            if ( !versionList.isEmpty() ) {

                List<Contentlet> majorList;
                for ( Contentlet contentletMajorVersion : versionList ) {

                    if ( !UtilMethods.compareVersions( currentMajorVersion.getStringProperty( config.getVersionMajorField() ), contentletMajorVersion.getStringProperty( config.getVersionMajorField() ) ) ) {

                        query = " +Parent_Versions-Child_Files:" + contentletMajorVersion.getIdentifier() + ( allowTestingBuilds? "" : " +" + config.getFilesStructure() + ".released:true" ) + " +deleted:false +live:true";
                        majorList = cAPI.search( query, 1, 0, config.getFilesStructure() + "." + config.getReleaseDateField() + " desc", sysUser, false );
                        if ( !majorList.isEmpty() ) {
                            Logger.debug( this.getClass(), "Found Major version = " + contentletMajorVersion.getStringProperty( "major" ) + " greater than = " + currentMajorVersion.getStringProperty( "major" ) );
                            currentMajorVersion = contentletMajorVersion;
                            break;
                        }
                    }
                }
            }
            return currentMajorVersion;
        }

        Logger.info( this.getClass(), "Could not find Major version for minor = " + version );
        return null;
    }

    /**
     * Method that will verify for a download update file link and if everything is ok with it will return it
     *
     * @param buildContentles
     * @param version
     * @param build
     * @return Download update file link
     */
    public String processAndVerifyDownloadLink ( List<Contentlet> buildContentles, String version, String build ) {

        String downloadLink = null;
        retCode = 404;

        try {
            if ( buildContentles != null && buildContentles.size() > 0 ) {
                //Find and returns a build file for a given build contentlet
                downloadLink = processAndVerifyDownloadLink( buildContentles.get( 0 ), version, build );
            }
        } catch ( DotDataException e ) {
            Logger.error( this.getClass(), "DotDataException: " + e.getMessage(), e );
        }

        return downloadLink;
    }

    /**
     * Method that will verify for a download update file link and if everything is ok with it will return it
     *
     * @param buildContentlet
     * @param currentVersion
     * @param build
     * @return Download update file link
     * @throws com.dotmarketing.exception.DotHibernateException
     */
    private String processAndVerifyDownloadLink ( Contentlet buildContentlet, String currentVersion, String build ) throws DotHibernateException {

        if ( buildContentlet == null ) {
            return null;
        }

        //Verify if there is a download link to provide
        String updateDownloadLink = buildContentlet.getStringProperty( config.getFilesDownloadLinkFieldName() );
        if ( !UtilMethods.isSet( updateDownloadLink ) ) {
            Logger.info( this.getClass(), "Download link property on minor " + buildContentlet.getStringProperty( "minor" ) + " Build " + buildContentlet.getStringProperty( "buildNumber" ) + " not set" );
            retCode = 204;
            return null;
        }

        String minorVersion = buildContentlet.getStringProperty( "minor" );

        if ( currentVersion.startsWith( AUTO_UPDATER_PREFIX ) ) {
            currentVersion = currentVersion.replaceAll( AUTO_UPDATER_PREFIX, "" );
        }
        if ( minorVersion.startsWith( AUTO_UPDATER_PREFIX ) ) {
            minorVersion = minorVersion.replaceAll( AUTO_UPDATER_PREFIX, "" );
        }

        //Verify if there is new content or not to provide
        Logger.info( this.getClass(), "Got minor version = " + minorVersion + " Build " + buildContentlet.getStringProperty( "buildNumber" ) );
        if ( !UtilMethods.compareVersions( minorVersion, currentVersion ) ) {

            if ( currentVersion.equals( minorVersion ) ) {

                //If same version compare build number
                long fileBuild = Long.parseLong( buildContentlet.getStringProperty( "buildNumber" ) );
                long currentBuild = Long.parseLong( build );
                if ( !( fileBuild > currentBuild ) ) {

                    Logger.info( this.getClass(), "Current version " + currentVersion + " Build " + build + " got version " + minorVersion + " Build " + buildContentlet.getStringProperty( "buildNumber" ) + " , No newer version available" );

                    // No content, no newer version
                    retCode = 204;
                    return null;
                }
            } else {

                Logger.info( this.getClass(), "Current version " + currentVersion + " got version " + minorVersion + ", No newer version available" );

                // No content, no newer version
                retCode = 204;
                return null;
            }
        }

        //And finally return the download link
        retCode = 200;
        return buildContentlet.getStringProperty( config.getFilesDownloadLinkFieldName() );
    }

    /**
     * Returns a build file for a given build contentlet
     *
     * @param buildContentlet
     * @param currentVersion
     * @param build
     * @return
     * @throws com.dotmarketing.exception.DotDataException
     * @throws com.dotmarketing.exception.DotSecurityException
     * @throws com.dotmarketing.business.DotStateException
     */
    private File getFile ( Contentlet buildContentlet, String currentVersion, String build ) throws DotDataException, DotStateException, DotSecurityException {

        if ( buildContentlet == null ) {
            return null;
        }

        //Verify if there is a file to provide
        String contFile = buildContentlet.getStringProperty( config.getFilesFileFieldName() );
        if ( !UtilMethods.isSet( contFile ) ) {
            Logger.info( this.getClass(), "File property on minor " + buildContentlet.getStringProperty( "minor" ) + " Build " + buildContentlet.getStringProperty( "buildNumber" ) + " not set" );
            retCode = 204;
            return null;
        }

        Identifier fileIdentifier = APILocator.getIdentifierAPI().find(contFile);
        String minorVersion = buildContentlet.getStringProperty( "minor" );

        if ( currentVersion.startsWith( AUTO_UPDATER_PREFIX ) ) {
            currentVersion = currentVersion.replaceAll( AUTO_UPDATER_PREFIX, "" );
        }
        if ( minorVersion.startsWith( AUTO_UPDATER_PREFIX ) ) {
            minorVersion = minorVersion.replaceAll( AUTO_UPDATER_PREFIX, "" );
        }

        //Verify if there is new content or not to provide
        Logger.info( this.getClass(), "Got minor version = " + minorVersion + " Build " + buildContentlet.getStringProperty( "buildNumber" ) );
        if ( !UtilMethods.compareVersions( minorVersion, currentVersion ) ) {

            if ( currentVersion.equals( minorVersion ) ) {

                //If same version compare build number
                long fileBuild = Long.parseLong( buildContentlet.getStringProperty( "buildNumber" ) );
                long currentBuild = Long.parseLong( build );
                if ( !( fileBuild > currentBuild ) ) {

                    Logger.info( this.getClass(), "Current version " + currentVersion + " Build " + build + " got version " + minorVersion + " Build " + buildContentlet.getStringProperty( "buildNumber" ) + " , No newer version available" );

                    // No content, no newer version
                    retCode = 204;
                    return null;
                }
            } else {

                Logger.info( this.getClass(), "Current version " + currentVersion + " got version " + minorVersion + ", No newer version available" );

                // No content, no newer version
                retCode = 204;
                return null;
            }
        }

        //And finally create and return the build file
        String path = WorkingCache.getPathFromCache( fileIdentifier.getURI(), fileIdentifier.getHostId() );
        String assetPath = Config.getStringProperty( "ASSET_PATH" );
        File buildFile = new File( Config.CONTEXT.getRealPath( assetPath + path ) );

        retCode = 200;
        return buildFile;
    }

    public boolean uploadUpdateFile ( UpdateData data, User user ) throws Exception {
        ContentletAPI cAPI = APILocator.getContentletAPI();
        Logger.info( this.getClass(), "Uploading: " + data.getVersion() + "/" + data.getBuild() + " by user: " + user.getUserId() );

        Contentlet c = null;

        boolean isNewContent = false;

        String query = "+structureName:" + config.getFilesStructure()
                + " +" + config.getFilesStructure() + "." + config.getFilesMinorFieldName() + ":*" + data.getVersion() + "*"
                + " +" + config.getFilesStructure() + "." + config.getBuildNumberField() + ":*" + data.getBuild() + "*"
                + " +deleted:false +live:true";

        List<Contentlet> filesList = cAPI.search( query, 1, 0, "", APILocator.getUserAPI().getSystemUser(), false );
        if ( !filesList.isEmpty() ) {
            c = filesList.get( 0 );
        }

        if ( c == null || !UtilMethods.isSet( c.getIdentifier() ) ) {
            isNewContent = true;
            c = new Contentlet();
            c.setStructureInode( StructureCache.getStructureByVelocityVarName( config.getFilesStructure() ).getInode() );
            c.setStringProperty( config.getFilesMinorFieldName(), data.getVersion() );
            c.setProperty( config.getBuildNumberField(), data.getBuild() );
            c.setBoolProperty( config.getReleasedField(), false );
            c.setDateProperty( config.getReleaseDateField(), new Date() );
        } else {
            Logger.info( this.getClass(), "Version already exists, updating contentlet " );
            String id = c.getIdentifier();
            c = cAPI.checkout( c.getInode(), APILocator.getUserAPI().getSystemUser(), false );
            c.setIdentifier( id );
            c.setInode( null );
        }


        query = "+structureName:" + config.getVersionStructure() + " +" + config.getVersionStructure() + "." + config.getVersionMajorField() + ":*" + data.getMajor() + "*" + " +deleted:false +live:true";
        List<Contentlet> versionList = cAPI.search( query, 1, 0, "", APILocator.getUserAPI().getSystemUser(), false );
        Contentlet major = null;
        if ( !versionList.isEmpty() ) {
            major = versionList.get( 0 );
        }

        if ( major != null && UtilMethods.isSet( major.getIdentifier() ) ) {
            Map<Relationship, List<Contentlet>> relations = new HashMap<Relationship, List<Contentlet>>();
            Relationship rel = RelationshipFactory.getRelationshipByRelationTypeValue( config.getVersionRelationship() );
            List<Contentlet> contents = new ArrayList<Contentlet>();
            contents.add( major );
            relations.put( rel, contents );

            Folder folder = APILocator.getFolderAPI().findFolderByPath(config.getUpgradeFileHome() + data.getMajor() + "/", APILocator.getHostAPI().findDefaultHost( APILocator.getUserAPI().getSystemUser(), false ).getIdentifier(), user, true);

            if ( folder != null && UtilMethods.isSet( folder.getInode() ) ) {

                com.dotmarketing.portlets.files.model.File file = new com.dotmarketing.portlets.files.model.File();
                String name = data.getVersion() + "_" + data.getBuild();
                if ( data.getMajor().startsWith( AUTO_UPDATER_PREFIX ) ) {
                    name += ".jar";
                } else {
                    name += ".zip";
                }
                Logger.info( this.getClass(), "Upload file name: " + name );
                file.setTitle( name );
                file.setFriendlyName( name );
                file.setPublishDate( new Date() );
                // find if file exists.

                Logger.info( this.getClass(), "Checking if file already exists" );
                com.dotmarketing.portlets.files.model.File oldFile = APILocator.getFileAPI().getFileByURI(config.getUpgradeFileHome() + data.getMajor() + "/" + name, APILocator.getHostAPI().findDefaultHost( APILocator.getUserAPI().getSystemUser(), false ), false, user, true);
                if ( oldFile == null || !UtilMethods.isSet( oldFile.getInode() ) ) {
                	oldFile = APILocator.getFileAPI().getFileByURI(config.getUpgradeFileHome() + data.getMajor() + "/" + name, APILocator.getHostAPI().findDefaultHost( APILocator.getUserAPI().getSystemUser(), false ), false, user, true);
                }

                if ( oldFile != null && UtilMethods.isSet( oldFile.getIdentifier() ) && !isNewContent ) {
                    Logger.info( this.getClass(), "File & content already exist. " );
                    return false;
                }
                // persists the file
                if ( data.getFile() != null ) {
                    Logger.info( this.getClass(), "About to save file & content " );
                    if ( oldFile != null && UtilMethods.isSet( oldFile.getIdentifier() ) ) {
                        Logger.info( this.getClass(), "File already exists, associating previous file with content " );
                        MessageDigest md = getMD5( data.getFile() );
                        byte[] md5sum = md.digest();
                        BigInteger bigInt = new BigInteger( 1, md5sum );
                        String output = bigInt.toString( 16 );
                        c.setStringProperty( config.getFilesMD5FieldName(), output );
                        c.setStringProperty( config.getFilesFileFieldName(), oldFile.getIdentifier() );
                    } else {
                        file.setSize( ( ( Long ) data.getFile().length() ).intValue() );
                        file.setMimeType( "application/zip" );
                        file.setFileName( name );
                        file.setModUser( user.getUserId() );
                        Logger.info( this.getClass(), "Saving file inode " + file.getFileName() );
                        HibernateUtil.saveOrUpdate(file);
                        // get the file Identifier
                        Identifier ident = null;
                        ident = new Identifier();
                        // Saving the file, this creates the new version and save the new data
                        com.dotmarketing.portlets.files.model.File workingFile = null;
                        MessageDigest md = getMD5( data.getFile() );
                        byte[] md5sum = md.digest();
                        BigInteger bigInt = new BigInteger( 1, md5sum );
                        String output = bigInt.toString( 16 );
                        c.setStringProperty( config.getFilesMD5FieldName(), output );

                        Logger.info( this.getClass(), "Saving working file " + file.getFileName() );
                        workingFile = APILocator.getFileAPI().saveFile(file, data.getFile(), folder, user, true);

                        Logger.info( this.getClass(), "Publishing file " + file.getFileName() );
                        PublishFactory.publishAsset( workingFile, user, false );
                        c.setStringProperty( config.getFilesFileFieldName(), workingFile.getIdentifier() );
                    }

                    c.setLanguageId( APILocator.getLanguageAPI().getDefaultLanguage().getId() );
                    Logger.info( this.getClass(), "Checking in contentlet" );
                    if ( isNewContent ) {
                        c = cAPI.checkin( c, relations, user, false );
                    } else {
                        c = cAPI.checkin( c, user, false );
                    }
                    cAPI.publish(c, user, true);
                    Logger.info( this.getClass(), "File and content checked in." );
                    return true;
                }
            } else {
                Logger.error( this.getClass(), "Could not find folder " + config.getUpgradeFileHome() + data.getMajor() + "/" );
            }

        } else {
            Logger.error( this.getClass(), "Could not find major version contentlet for " + data.getMajor() );
        }
        return false;
    }

    public boolean hasUploadRights ( User user ) throws DotDataException, DotSecurityException {
        if ( user == null ) {
            return false;
        }
        Folder folder = APILocator.getFolderAPI().findFolderByPath(config.getUpgradeFileHome(), APILocator.getHostAPI().findDefaultHost( APILocator.getUserAPI().getSystemUser(), false ).getIdentifier(), user, true);
        return APILocator.getPermissionAPI().doesUserHavePermission( folder, PermissionAPI.PERMISSION_WRITE, user );
    }

    public MessageDigest getMD5 ( File f ) throws IOException {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance( "MD5" );
            InputStream is = new FileInputStream( f );
            byte[] buffer = new byte[8192];
            int read;
            while ( ( read = is.read( buffer ) ) > 0 ) {
                digest.update( buffer, 0, read );
            }

        } catch ( NoSuchAlgorithmException e ) {
            Logger.debug( UpdateServletLogic.class, "NoSuchAlgorithmException: "
                    + e.getMessage(), e );
        }
        return digest;
    }


    public void logRequest ( HttpServletRequest request ) {

        String remoteAddr = request.getRemoteAddr();
        String remoteHost = request.getRemoteHost();
        String reverseHost = null;

        try {
            Resolver res = new ExtendedResolver();

            Name name = ReverseMap.fromAddress( remoteAddr );
            int type = Type.PTR;
            int dclass = DClass.IN;
            Record rec = Record.newRecord( name, type, dclass );
            Message query = Message.newQuery( rec );
            Message response = res.send( query );

            Record[] answers = response.getSectionArray( Section.ANSWER );
            if ( answers.length == 0 ) {
                reverseHost = remoteAddr;
            } else {
                reverseHost = answers[0].rdataToString();
            }
        } catch ( UnknownHostException e1 ) {

            Logger.error( UpdateServletLogic.class, "UnknownHostException: " + e1.getMessage(), e1 );
        } catch ( IOException e1 ) {

            Logger.error( UpdateServletLogic.class, "IOException: " + e1.getMessage(), e1 );
        }


        String major = request.getParameter( config.getReqVersion() );
        String minorStr = request.getParameter( config.getReqBuild() );
        DotConnect dc = new DotConnect();
        dc.setSQL( "INSERT INTO autoupdater_log (remote_addr,remote_host,reverse_host,major) VALUES (?,?,?,?);" );
        dc.addObject( remoteAddr );
        dc.addObject( remoteHost );
        dc.addObject( reverseHost );
        dc.addObject( major );

        try {
            dc.loadResult();
        } catch ( DotDataException e ) {
            Logger.error( UpdateServletLogic.class, "DotDataException: " + e.getMessage(), e );
        }
    }

}

