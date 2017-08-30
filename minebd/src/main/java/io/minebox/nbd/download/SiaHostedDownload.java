package io.minebox.nbd.download;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.minebox.SiaUtil;
import io.minebox.nbd.RemoteTokenService;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SiaHostedDownload implements DownloadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SiaHostedDownload.class);
    private final SiaUtil siaUtil;
    private final Map<String, String> lookup;


    @Inject
    SiaHostedDownload(SiaUtil siaUtil, Map<String, String> lookup) {
        this.siaUtil = siaUtil;
        this.lookup = lookup;
    }

    @Override
    public RecoveryStatus downloadIfPossible(File file) {
        final String siaPath = lookup.get(file.getName());
        if (siaPath == null) {
            return RecoveryStatus.NO_FILE;
        }
        final boolean download = siaUtil.download(siaPath, file.toPath());
        if (download) {
            return RecoveryStatus.RECOVERED;
        }
        return RecoveryStatus.ERROR;

    }

    @Override
    public boolean hasMetadata() {
        return true;
    }

    @Override
    public boolean connectedMetadata() {
        return true;
    }

    @Override
    public Collection<String> allFilenames() {
        return lookup.keySet();
    }
}
