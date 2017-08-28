package io.minebox.nbd.download;

import java.io.File;
import java.util.Collection;

import com.google.inject.ProvidedBy;

/**
 * Created by andreas on 27.04.17.
 */
@ProvidedBy(DownloadFactory.class)
public interface DownloadService {

    enum RecoveryStatus{
        NO_FILE, RECOVERED, ERROR
    }

    RecoveryStatus downloadIfPossible(File file);

    boolean hasMetadata();

    boolean connectedMetadata();

    Collection<String> allFilenames();
}
