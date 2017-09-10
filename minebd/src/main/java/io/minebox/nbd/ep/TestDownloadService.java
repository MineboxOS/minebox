package io.minebox.nbd.ep;

import java.io.File;

import io.minebox.nbd.download.DownloadService;
import io.minebox.nbd.download.RecoverableFile;

/**
 * Created by andreas on 27.04.17.
 */
public class TestDownloadService implements DownloadService {
    @Override
    public RecoveryStatus downloadIfPossible(RecoverableFile file) {
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
    public double completedPercent(File parentDir) {
        return 100.0;
    }
}
