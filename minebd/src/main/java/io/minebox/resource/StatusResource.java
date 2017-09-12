package io.minebox.resource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.minebox.nbd.SerialNumberService;
import io.minebox.nbd.download.DownloadFactory;
import io.minebox.nbd.download.DownloadService;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.List;

@Path(StatusResource.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Api(StatusResource.PATH)
@Singleton
public class StatusResource {
    public static final String PATH = "/status";
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusResource.class);
    final private EncyptionKeyProvider encyptionKeyProvider;
    private final List<String> parentDirs;
    final private DownloadFactory downloadFactory;
    private final SerialNumberService serialNumberService;

    @Inject
    public StatusResource(EncyptionKeyProvider encyptionKeyProvider
            , @Named("parentDirs") List<String> parentDirs
            , DownloadFactory downloadFactory
            , SerialNumberService serialNumberService) {

        this.encyptionKeyProvider = encyptionKeyProvider;
        this.parentDirs = parentDirs;
        this.downloadFactory = downloadFactory;
        this.serialNumberService = serialNumberService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Status getStatus() {
        //todo extract to status service
        final Status status = new Status();
        status.hasEncryptionKey = encyptionKeyProvider.getMasterPassword().isDone();
        if (!status.hasEncryptionKey) {
            return status;
        }
        if (downloadFactory.hasDownloadService()) {
            final DownloadService downloadService = downloadFactory.get();
            status.connectedMetadata = downloadService.connectedMetadata();
            if (!status.connectedMetadata) {
                return status;
            }
            status.remoteMetadataDetected = downloadService.hasMetadata();
            if (!status.remoteMetadataDetected) {
                return status;
            }
            if (status.hasEncryptionKey) {
                final File firstDir = new File(parentDirs.get(0), serialNumberService.getPublicIdentifier());
                status.completedRestorePercent = downloadService.completedPercent(firstDir);
            }
            status.restoreRunning = status.completedRestorePercent < 100.0;
        } else {
            status.connectedMetadata = false;
            status.hasEncryptionKey = false;
            status.remoteMetadataDetected = false;
            status.restoreRunning = false;
        }
        return status;
    }


    public static class Status {
        public boolean hasEncryptionKey = false;
        public boolean remoteMetadataDetected = false;
        public boolean restoreRunning = false;
        public boolean connectedMetadata = false;
        public double completedRestorePercent;
    }
}