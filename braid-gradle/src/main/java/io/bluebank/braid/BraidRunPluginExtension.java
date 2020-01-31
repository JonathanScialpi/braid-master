package io.bluebank.braid;

public class BraidRunPluginExtension {
    private int port = 8080;
    private String cordAppsDirectory = "";

    // rPC details
    private String username = "user1";
    private String password = "password";
    private String networkAndPort = "localhost:10003";

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCordAppsDirectory() {
        return cordAppsDirectory;
    }

    public void setCordAppsDirectory(String cordAppsDirectory) {
        this.cordAppsDirectory = cordAppsDirectory;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNetworkAndPort() {
        return networkAndPort;
    }

    public void setNetworkAndPort(String networkAndPort) {
        this.networkAndPort = networkAndPort;
    }
}