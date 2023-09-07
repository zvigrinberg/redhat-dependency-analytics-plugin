package redhat.jenkins.plugins.rhda.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MavenService implements PackageManagerService {

    @Override
    public String getName() {
        return "maven";
    }

    @Override
    public String readFile(Path path) throws IOException {
        validateFile(path);
        return Files.readString(path);
    }

    @Override
    public String generateSbom(Path path) throws IOException {
        validateFile(path);
        Path filtered = filterIgnoredDependencies(path);
        Process process = Runtime.getRuntime().exec("/opt/homebrew/bin/mvn org.cyclonedx:cyclonedx-maven-plugin:2.7.6:makeBom -DincludeTestScope=false -DoutputFormat=json -DoutputName=project-bom -f " + filtered);
        try {
            int exitCode = process.waitFor();
            if(exitCode != 0) {
                throw new IOException("Unable to generate SBOM for " + path + ". Maven returned exit code: " + exitCode);
            } else {
                Path bomPath = Path.of(path.getParent() + "/target/project-bom.json");
                return readFile(bomPath);
            }
        } catch (InterruptedException e) {
            throw new IOException("Unable to generate SBOM for " + path, e);
        } finally {
            Files.deleteIfExists(filtered);
        }
    }

    private void validateFile(Path filePath) {
        if(!Files.exists(filePath)) {
            throw new IllegalArgumentException("The provided file does not exist: " + filePath);
        }
        if(!Files.isReadable(filePath)) {
            throw new IllegalArgumentException("Unable to read file: " + filePath);
        }
    }

    private Path filterIgnoredDependencies(Path source) throws IOException {
        BufferedReader reader = Files.newBufferedReader(source);
        Path target = Path.of(source.getParent() + "/filtered-pom.xml");
        BufferedWriter writer = Files.newBufferedWriter(target);
        String line;
        boolean ignore = false;
        boolean isDep = false;
        List<String> depLines = new ArrayList<>();
        while((line = reader.readLine()) != null) {
            if(isDep) {
                if(line.contains("<!--crdaignore-->")) {
                    ignore = true;
                } else {
                    depLines.add(line);
                }
                if(line.contains("</dependency>")) {
                    if(ignore) {
                        ignore = false;
                    } else {
                        for(String l : depLines) {
                            writer.append(l);
                            writer.newLine();
                        }
                    }
                    isDep = false;
                    depLines.clear();
                }
            } else {
                if(line.contains("<dependency>")) {
                    depLines.add(line);
                    isDep = true;
                } else {
                    writer.append(line);
                    writer.newLine();
                }
            }
        }
        writer.close();
        reader.close();
        return target;
    }
    
}
