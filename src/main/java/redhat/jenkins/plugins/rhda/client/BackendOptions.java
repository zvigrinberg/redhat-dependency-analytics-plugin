package redhat.jenkins.plugins.rhda.client;

public class BackendOptions {

    boolean verbose;
    String snykToken;

    public BackendOptions() {
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public String getSnykToken() {
        return snykToken;
    }

    public void setSnykToken(String snykToken) {
        this.snykToken = snykToken;
    }
}
