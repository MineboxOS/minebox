package io.minebox.nbd.download;

import java.io.File;
import java.util.Collection;
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
    public Collection<String> allFilenames() {
        return Collections.emptyList();
    }
}
