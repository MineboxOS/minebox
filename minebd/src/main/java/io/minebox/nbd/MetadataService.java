package io.minebox.nbd;

import java.io.File;
import java.util.Collection;

import com.google.inject.ImplementedBy;

/**
 * Created by andreas on 27.04.17.
 */
@ImplementedBy(MetadataServiceImpl.class)
public interface MetadataService {
    boolean downloadIfPossible(File file);

    boolean hasMetadata();

    boolean connectedMetadata();

    Collection<String> allFilenames();
}
