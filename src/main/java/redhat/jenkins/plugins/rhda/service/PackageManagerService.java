package redhat.jenkins.plugins.rhda.service;

import java.io.IOException;
import java.nio.file.Path;

public interface PackageManagerService {

    String getName();

    String readFile(Path path) throws IOException;

    String generateSbom(Path path) throws IOException;

}
