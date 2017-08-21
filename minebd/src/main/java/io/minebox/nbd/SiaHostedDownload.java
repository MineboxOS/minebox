package io.minebox.nbd;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.minebox.SiaUtil;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SiaHostedDownload extends AbstractDownload {

    private static final Logger LOGGER = LoggerFactory.getLogger(SiaHostedDownload.class);
    private final SiaUtil siaUtil;
    private final Path siaDir;
    private JSONArray fileInfo;

    @Inject
    SiaHostedDownload(SiaUtil siaUtil,
                      @Named("httpMetadata") String metadataUrl,
                      @Named("siaDataDirectory") String siaDataDirectory,
                      RemoteTokenService remoteTokenService,
                      EncyptionKeyProvider encyptionKeyProvider) {

        super(metadataUrl, remoteTokenService, encyptionKeyProvider);

        this.siaUtil = siaUtil;
        siaDir = Paths.get(siaDataDirectory).toAbsolutePath();
        final boolean consensusExists = siaDir.resolve("consensus").toFile().exists();
        if (!consensusExists) {
            throw new IllegalStateException("does not seem to be the right place");
        }
    }

    @Override
    protected void digestRenterFile(ZipEntry entry, ZipInputStream zis) {
        final String entryName = entry.getName();
        final Path dest = siaDir.resolve(entryName);
        if (entryName.startsWith("renter")) {
            try {
                Files.deleteIfExists(dest); //yes, we overwrite everything we find
                Files.copy(zis, dest);
            } catch (IOException e) {
                throw new RuntimeException("unable to create renter file", e);
            }
        }
    }

    @Override
    protected void downloadFile(File file, String toDownload) {
        siaUtil.download(toDownload, file.toPath());
    }

    @Override
    public boolean wasInitialized() {
        return false;
    }
}
