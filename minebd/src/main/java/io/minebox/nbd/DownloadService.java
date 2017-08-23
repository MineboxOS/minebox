package io.minebox.nbd;

import java.io.File;
import java.util.Collection;

import com.google.inject.ImplementedBy;

/**
 * Created by andreas on 27.04.17.
 */
@ImplementedBy(SiaHostedDownload.class)
public interface DownloadService {

    enum RecoveryStatus{
        NO_FILE, RECOVERED, ERROR
    }

    RecoveryStatus downloadIfPossible(File file);

    boolean wasInitialized();

    boolean hasMetadata();

    boolean connectedMetadata();

    Collection<String> allFilenames();
}
