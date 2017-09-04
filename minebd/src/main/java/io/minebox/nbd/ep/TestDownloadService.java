package io.minebox.nbd.ep;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import io.minebox.nbd.download.DownloadService;

/**
 * Created by andreas on 27.04.17.
 */
public class TestDownloadService implements DownloadService {
    @Override
    public RecoveryStatus downloadIfPossible(File file) {
        return RecoveryStatus.NO_FILE;
    }

    @Override
    public boolean hasMetadata() {
        return false;
    }

    @Override
    public boolean connectedMetadata() {
        return false;
    }

    @Override
    public Collection<String> allFilenames() {
        return Collections.emptyList();
    }
}
