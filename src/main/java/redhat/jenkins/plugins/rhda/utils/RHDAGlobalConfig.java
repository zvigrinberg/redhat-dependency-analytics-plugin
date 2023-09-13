package redhat.jenkins.plugins.rhda.utils;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;

@Extension
public class RHDAGlobalConfig extends GlobalConfiguration {

    private String uuid;

    public RHDAGlobalConfig() {
        load();
    }

    public static RHDAGlobalConfig get() {
        return GlobalConfiguration.all().get(RHDAGlobalConfig.class);
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
        // Save the configuration to disk
        save();
    }
}
