package io.bluebank.braid;

public class BraidPluginExtension {
    private String version;
    private String releaseRepo = "https://repo1.maven.org/maven2";
    private String snapshotRepo = "https://repo1.maven.org/maven2";

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getReleaseRepo() {
        return releaseRepo;
    }

    public void setReleaseRepo(String releaseRepo) {
        this.releaseRepo = releaseRepo;
    }

    public String getSnapshotRepo() {
        return snapshotRepo;
    }

    public void setSnapshotRepo(String snapshotRepo) {
        this.snapshotRepo = snapshotRepo;
    }
}