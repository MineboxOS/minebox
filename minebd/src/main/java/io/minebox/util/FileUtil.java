package io.minebox.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by andreas on 12.05.17.
 */
public class FileUtil {

    public static String readPassword(String filePath, Boolean ignoreMissingPaths, String defaultValue) {
        String password;
        try {
            final List<String> lines = Files.readAllLines(Paths.get(filePath));
            password = lines.get(0);
        } catch (IOException e) {
            if (ignoreMissingPaths) {
                return defaultValue;
            }
            throw new RuntimeException("unable to read password file at " + filePath, e);
        }
        return password;
    }
}
