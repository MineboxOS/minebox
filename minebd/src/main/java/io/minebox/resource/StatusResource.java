package io.minebox.resource;

import java.nio.file.Files;
import java.nio.file.Paths;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.minebox.nbd.DownloadService;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(StatusResource.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Api(StatusResource.PATH)
@Singleton
public class StatusResource {
    public static final String PATH = "/status";
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusResource.class);
    final private EncyptionKeyProvider encyptionKeyProvider;
    private final String parentDir;
    final private DownloadService downloadService;

    @Inject
    public StatusResource(EncyptionKeyProvider encyptionKeyProvider, @Named("parentDir") String parentDir, DownloadService downloadService) {
        this.encyptionKeyProvider = encyptionKeyProvider;
        this.parentDir = parentDir;
        this.downloadService = downloadService;
    }

    @GET
    @Produces("text/json")
    @PermitAll
    public Status getStatus() {
        //todo extract to status service
        final Status status = new Status();
        status.hasEncryptionKey = encyptionKeyProvider.getMasterPassword().isDone();
        if (!status.hasEncryptionKey) {
            return status;
        }
        status.connectedMetadata = downloadService.connectedMetadata();
        if (!status.connectedMetadata) {
            return status;
        }
        status.remoteMetadataDetected = downloadService.hasMetadata();
        if (!status.remoteMetadataDetected) {
            return status;
        }
        for (String fileName : downloadService.allFilenames()) {
            if (!Files.exists(Paths.get(parentDir).resolve(fileName))) {
                status.restoreRunning = true;
                return status;
            }
        }
        status.restoreRunning = false;
        return status;
    }


    public static class Status {
        public boolean hasEncryptionKey = false;
        public boolean remoteMetadataDetected = false;
        public boolean restoreRunning = false;
        public boolean connectedMetadata = false;
    }
}