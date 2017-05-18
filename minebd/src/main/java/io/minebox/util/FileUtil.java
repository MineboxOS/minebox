package io.minebox.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import com.google.common.base.Charsets;

/**
 * Created by andreas on 12.05.17.
 */
public class FileUtil {

    public static String readEncryptionKey(Path path) {
        try {
            return new String(Files.readAllBytes(path), Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("unable to read password file at " + path, e);
        }
    }

    public static String readLocalAuth(String filePath, Boolean ignoreMissingPaths, String defaultValue) {
        final Path path = Paths.get(filePath);
        try {
            final List<String> lines = Files.readAllLines(path);
            return lines.get(0);
        } catch (IOException e) {
            if (ignoreMissingPaths) {
                writeRootDefault(defaultValue, path);
                return defaultValue;
            }
            throw new RuntimeException("unable to read password file at " + filePath, e);
        }
    }

    private static void writeRootDefault(String defaultValue, Path path) {
        try {
            Files.createFile(path);
//            fixAttributes(path);
            Files.write(path, Collections.singletonList(defaultValue));
        } catch (IOException e1) {
            throw new RuntimeException("write default value to " + path, e1);
        }
    }

    private static void fixAttributes(Path path) throws IOException {
        final PosixFileAttributeView attrs = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        attrs.setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ));
        FileSystem fs = FileSystems.getDefault();
        UserPrincipalLookupService gLS = fs.getUserPrincipalLookupService();
        attrs.setGroup(gLS.lookupPrincipalByGroupName("root"));
        attrs.setOwner(gLS.lookupPrincipalByName("root"));
    }
}
