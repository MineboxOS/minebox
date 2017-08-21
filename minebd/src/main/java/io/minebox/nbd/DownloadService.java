package io.minebox.nbd;

import java.io.File;
import java.util.Collection;

import com.google.inject.ImplementedBy;

/**
 * Created by andreas on 27.04.17.
 */
@ImplementedBy(MineboxHostedDownload.class)
public interface DownloadService {

    boolean downloadIfPossible(File file);

    boolean wasInitialized();

    boolean hasMetadata();

    boolean connectedMetadata();

    Collection<String> allFilenames();
}
