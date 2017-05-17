package io.minebox.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Created by andreas on 12.05.17.
 */
public class FileUtil {

    private static FileSystem fs;

    public static String readEncryptionKey(String path) {
        final List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException("unable to read password file at " + path, e);
        }
        return lines.get(0);
    }

    public static String readLocalAuth(String filePath, Boolean ignoreMissingPaths, String defaultValue) {
        final Path path = Paths.get(filePath);
        try {
            final List<String> lines = Files.readAllLines(path);
            return lines.get(0);
        } catch (IOException e) {
            if (ignoreMissingPaths) {
                writeRootDefault(filePath, defaultValue, path);
                return defaultValue;
            }
            throw new RuntimeException("unable to read password file at " + filePath, e);
        }
    }

    private static void writeRootDefault(String filePath, String defaultValue, Path path) {
        try {
            final PosixFileAttributeView attrs = Files.getFileAttributeView(path, PosixFileAttributeView.class);
            attrs.setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ));
            fs = FileSystems.getDefault();
            UserPrincipalLookupService gLS = fs.getUserPrincipalLookupService();
            attrs.setGroup(gLS.lookupPrincipalByGroupName("root"));
            attrs.setOwner(gLS.lookupPrincipalByName("root"));
            Files.write(path, Collections.singletonList(defaultValue));

        } catch (IOException e1) {
            throw new RuntimeException("write default value to " + filePath, e1);
        }
    }
}
