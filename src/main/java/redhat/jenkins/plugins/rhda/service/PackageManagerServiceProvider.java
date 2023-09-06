package redhat.jenkins.plugins.rhda.service;

import java.io.File;

public class PackageManagerServiceProvider {

    public static PackageManagerService get(File file) {
        switch (file.getName()) {
            case "pom.xml":
                return new MavenService();
            default:
                throw new IllegalArgumentException("Unsupported package manager file" + file.getName());
        }

    }
}
