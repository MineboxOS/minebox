package io.minebox.nbd;

import java.io.File;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Created by andreas on 24.04.17.
 */
@Singleton
public class MetadataService {

    public static final String TEST_METADATA_DIR = "/home/andreas/minebox/restoreSample/backups/backup.1492640813";

    @Inject
    public MetadataService() {
        /*
        preparation:
        get clean siad
        siac wallet init-seed


         */

//        then...
//        obtainLatestPackageWithPub();
//        copy sia files to renter dir
//        restart siad

    }

    public boolean downloadIfPossible(File file) {

        return false;
    }
}
