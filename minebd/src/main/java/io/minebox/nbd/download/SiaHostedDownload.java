package io.minebox.nbd.download;

import com.google.inject.Inject;
import io.minebox.sia.SiaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

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
    public RecoveryStatus downloadIfPossible(RecoverableFile file) {
        final String siaPath = lookup.get(file.fileName);
        if (siaPath == null) {
            return RecoveryStatus.NO_FILE;
        }
        final File firstFile = new File(file.parentDirectories.get(0), file.fileName);
        final boolean download = siaUtil.download(siaPath, firstFile.toPath());
        copyFirstToOthers(file, firstFile);
        if (download) {
            return RecoveryStatus.RECOVERED;
        }
        return RecoveryStatus.ERROR;

    }

    private void copyFirstToOthers(RecoverableFile file, File firstFile) {
        for (int i = 1; i < file.parentDirectories.size(); i++) {
            final File copy = file.parentDirectories.get(i);
            try {
                Files.copy(firstFile.toPath(), copy.toPath());
            } catch (IOException e) {
                throw new RuntimeException("error copying over file" + firstFile, e);
            }
        }
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
