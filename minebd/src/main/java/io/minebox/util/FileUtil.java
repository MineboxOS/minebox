package io.minebox.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * Created by andreas on 12.05.17.
 */
public class FileUtil {

    public static String readPassword(String filePath, Boolean ignoreMissingPaths, String defaultValue) {
        final Path path = Paths.get(filePath);
        try {
            final List<String> lines = Files.readAllLines(path);
            return lines.get(0);
        } catch (IOException e) {
            writeDefault(filePath, defaultValue, path);
            if (ignoreMissingPaths) {
                return defaultValue;
            }
            throw new RuntimeException("unable to read password file at " + filePath, e);
        }
    }

    private static void writeDefault(String filePath, String defaultValue, Path path) {
        try {
            Files.write(path, Collections.singletonList(defaultValue));
        } catch (IOException e1) {
            throw new RuntimeException("write default value to " + filePath, e1);
        }
    }
}
