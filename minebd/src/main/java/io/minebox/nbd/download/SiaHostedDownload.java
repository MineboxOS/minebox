package io.minebox.nbd.download;

import com.google.inject.Inject;
import io.minebox.SiaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.stream.Collectors;

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
    public double completedPercent(File parentDir) {
        final long missing = lookup.keySet().stream()
                .filter(s -> !new File(parentDir, s).exists())
                .count();
        return 100.0 * (1.0 - (double) missing / lookup.size());
    }
}
