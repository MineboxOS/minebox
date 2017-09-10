package io.minebox.nbd.download;

import java.io.File;

public class NothingTodoDownloadService implements DownloadService {
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
        return true;
    }

    @Override
    public double completedPercent(File parentDir) {
        return 100.0;
    }
}
