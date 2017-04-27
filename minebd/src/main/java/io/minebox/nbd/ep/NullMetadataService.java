package io.minebox.nbd.ep;

import java.io.File;

import io.minebox.nbd.MetadataService;

/**
 * Created by andreas on 27.04.17.
 */
public class NullMetadataService implements MetadataService {
    @Override
    public boolean downloadIfPossible(File file) {
        return false;
    }
}
