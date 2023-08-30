package redhat.jenkins.plugins.crda.service;

import redhat.jenkins.plugins.crda.service.MavenService;
import redhat.jenkins.plugins.crda.service.PackageManagerService;

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
