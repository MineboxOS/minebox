package io.minebox.nbd;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.minebox.SiaUtil;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SiaHostedDownload extends AbstractDownload {

    private static final Logger LOGGER = LoggerFactory.getLogger(SiaHostedDownload.class);
    private final SiaUtil siaUtil;
    private final Path siaDir;

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
    protected void digestEntry(ZipEntry entry, ZipInputStream zis) {
        final Path dest = siaDir.resolve(entry.getName());
        try {
            Files.deleteIfExists(dest); //yes, we overwrite everything we find
            Files.copy(zis, dest);
        } catch (IOException e) {
            throw new RuntimeException("unable to create renter file", e);
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
