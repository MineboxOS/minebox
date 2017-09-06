package io.minebox.nbd.download;

import java.io.File;
import java.util.Collections;

public class NothingTodoDownloadService implements DownloadService {
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
        return true;
    }

    @Override
    public double completedPercent(File parentDir) {
        return 100.0;
    }
}
