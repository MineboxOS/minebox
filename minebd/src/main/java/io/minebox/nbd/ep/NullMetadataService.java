package io.minebox.nbd.ep;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import io.minebox.nbd.MetadataService;

/**
 * Created by andreas on 27.04.17.
 */
public class NullMetadataService implements MetadataService {
    @Override
    public boolean downloadIfPossible(File file) {
        return false;
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
