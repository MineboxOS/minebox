package io.minebox.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    static String readLocalAuth(String filePath) {
        final Path path = Paths.get(filePath);
        try {
            if (!Files.exists(path)) {
                LOGGER.info("authFile not found, creating it");
                final UUID uuid = UUID.randomUUID();
                final Path temp = path.getParent().resolve(path.getFileName().toString() + ".temp");
                createFileWithPermissions(temp);
                final String written = uuid.toString();
                Files.write(temp, written.getBytes(Charsets.UTF_8));
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
                return written;
            } else {
                final List<String> lines = Files.readAllLines(path);
                Preconditions.checkState(lines.size() == 1);
                final String ret = lines.get(0);
                final UUID ignored = UUID.fromString(ret);//check if this throws an exception, that might mean that
                return ret;
            }
        } catch (IOException e) {
            throw new RuntimeException("unable to read password file at " + filePath, e);
        }
    }

    private static void createFileWithPermissions(Path path) throws IOException {
        FileAttribute<Set<PosixFilePermission>> noPermissions
                = PosixFilePermissions.asFileAttribute(Collections.emptySet());
        Files.createFile(path, noPermissions);
        String myusername = System.getProperty("user.name");
        LOGGER.info("creating auth file with user {}", myusername);
        setOwnership(path, myusername, "minebd", true);
    }

    public static void setOwnership(Path path, String username, String groupName, boolean fallbackOnGroup) throws IOException {
        final PosixFileAttributeView attrs = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        FileSystem fs = FileSystems.getDefault();
        UserPrincipalLookupService gLS = fs.getUserPrincipalLookupService();
        attrs.setOwner(gLS.lookupPrincipalByName(username));
        try {
            attrs.setGroup(gLS.lookupPrincipalByGroupName(groupName));
            final EnumSet<PosixFilePermission> ownerGroupPermissions = EnumSet.of(PosixFilePermission.GROUP_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ);
            attrs.setPermissions(ownerGroupPermissions);
        } catch (UserPrincipalNotFoundException e) {
            if (fallbackOnGroup) {
                LOGGER.warn("local auth key could not be set to group minebd. falling back to username");
                attrs.setGroup(gLS.lookupPrincipalByGroupName(System.getProperty("user.name")));
                final EnumSet<PosixFilePermission> ownerPermissions = EnumSet.of(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ);
                attrs.setPermissions(ownerPermissions);
            }else{
                throw e;
            }
        }
    }
}
