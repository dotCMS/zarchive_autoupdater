package org.dotcms.autoupdater.servlet;

public class UpdateServletLogicConfig {

    private String versionRelationship = "Parent_Versions-Child_Files";
    private String versionStructure = "AutoupdaterVersions";
    private String versionMajorField = "major";
    private String versionRequiresAuthField = "requiresauth";

    private String filesStructure = "AutoupdaterFiles";
    private String filesMinorField = "text1";
    private String filesMinorFieldName = "minor";
    private String filesFileFieldName = "file";
    private String filesPrettyNameFieldName = "prettyName";
    private String filesMD5FieldName = "md5";
    private String filesDownloadLinkFieldName = "downloadLink";
    private String upgradeFileHome = "/global/upgrade/";

    private String releasedField = "released";
    private String allowTestingBuilds = "allowTestingBuilds";
    private String releaseDateField = "releasedDate";
    private String specificVersion = "specificVersion";

    private String buildNumberField = "buildNumber";

    private String reqVersion = "version";
    private String reqBuild = "buildNumber";
    private String reqAgentVersion = "agent_version";
    private String reqCheckValue = "check_version";

    private Boolean is2XBuild;

    public UpdateServletLogicConfig ( Boolean is2XBuild ) {
        this.is2XBuild = is2XBuild;
    }

    public String getBuildNumberField () {
        return buildNumberField;
    }

    public String getReleaseDateField () {
        return releaseDateField;
    }

    public String getReleasedField () {
        return releasedField;
    }

    public String getVersionRelationship () {
        return versionRelationship;
    }

    public String getVersionStructure () {
        return versionStructure;
    }

    public String getVersionMajorField () {
        return versionMajorField;
    }

    public String getFilesStructure () {
        return filesStructure;
    }

    public String getFilesMinorField () {
        return filesMinorField;
    }

    public String getFilesMinorFieldName () {
        return filesMinorFieldName;
    }

    public String getFilesFileFieldName () {
        return filesFileFieldName;
    }

    public String getUpgradeFileHome () {
        return upgradeFileHome;
    }

    public String getFilesMD5FieldName () {
        return filesMD5FieldName;
    }

    public String getFilesDownloadLinkFieldName () {
        return filesDownloadLinkFieldName;
    }

    public String getVersionRequiresAuthField () {
        return versionRequiresAuthField;
    }

    public String getFilesPrettyNameFieldName () {
        return filesPrettyNameFieldName;
    }

    public String getReqVersion () {
        return reqVersion;
    }

    public String getReqBuild () {
        return reqBuild;
    }

    public String getReqAgentVersion () {
        return reqAgentVersion;
    }

    public String getReqCheckValue () {
        return reqCheckValue;
    }

    public String getAllowTestingBuilds () {
        return allowTestingBuilds;
    }

    public String getSpecificVersion () {
        return specificVersion;
    }

    public Boolean is2XBuild () {
        return is2XBuild;
    }

    public void setIs2XBuild ( Boolean is2XBuild ) {
        this.is2XBuild = is2XBuild;
    }

}