package io.minebox.nbd;

import java.io.File;

import com.google.inject.ImplementedBy;
import io.minebox.nbd.ep.NullMetadataService;

/**
 * Created by andreas on 27.04.17.
 */
@ImplementedBy(NullMetadataService.class)
public interface MetadataService {
    boolean downloadIfPossible(File file);
}
