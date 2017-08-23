package io.minebox.nbd;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MineboxHostedDownload extends AbstractDownload {


    private static final Logger LOGGER = LoggerFactory.getLogger(MineboxHostedDownload.class);

    @Inject
    public MineboxHostedDownload(@Named("httpMetadata") String metadataUrl, RemoteTokenService remoteTokenService, EncyptionKeyProvider encyptionKeyProvider) {
        super(metadataUrl, remoteTokenService, encyptionKeyProvider);
    }

    @Override
    protected void digestRenterFile(ZipEntry entry, ZipInputStream zis) {
        //do nothing, the
    }

    protected boolean downloadFile(File destination, String toDownload) {
        final Optional<String> token = remoteTokenService.getToken();
        if (!token.isPresent()) {
            LOGGER.error("unable to obtain auth token needed to download file {}", toDownload);
            throw new RuntimeException("unable to download file " + toDownload);
        }
        try {
            LOGGER.info("downloading missing file {} from remote service... ", toDownload);
            final long start = System.currentTimeMillis();
            final InputStream is = Unirest.get(metadataUrl + "file/" + toDownload)
                    .header("X-Auth-Token", token.get())
                    .asBinary().getBody();
            Files.copy(is, Paths.get(destination.toURI()));
            final long duration = System.currentTimeMillis() - start;
            LOGGER.info("downloaded {} successfully in {} seconds", toDownload, Duration.ofMillis(duration).getSeconds());
        } catch (UnirestException | IOException e) {
            LOGGER.error("unable to download file " + toDownload, e);
            throw new RuntimeException("unable to download file " + toDownload, e);
        }
        return false;
    }

    @Override
    public boolean wasInitialized() {
        //todo
        return connectedToMetadata;
    }
}
