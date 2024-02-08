package redhat.jenkins.plugins.rhda.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class BaseTest {

    protected String getStringFromFile(String... list) {
        byte[] bytes;
        try {
            InputStream resourceAsStream = this.getClass().getModule().getResourceAsStream(String.join("/", list));
            bytes = resourceAsStream.readAllBytes();
            resourceAsStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new String(bytes);
    }

    protected String getFileFromResource(String fileName, String... pathList) {
        Path tmpFile;
        try {
            var tmpDir = Files.createTempDirectory("rhda_plugin_");
            tmpFile = Files.createFile(tmpDir.resolve(fileName));
            try (var is = getClass().getModule().getResourceAsStream(String.join("/", pathList))) {
                Files.write(tmpFile, is.readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return tmpFile.toString();
    }

    protected String getFileFromString(String fileName, String content) {
        Path tmpFile;
        try {
            var tmpDir = Files.createTempDirectory("exhort_test_");
            tmpFile = Files.createFile(tmpDir.resolve(fileName));
            Files.write(tmpFile, content.getBytes());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return tmpFile.toString();
    }

}
