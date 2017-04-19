package io.minebox.nbd.ep.chunked;

import com.google.common.primitives.Ints;
import io.minebox.nbd.Constants;

/**
 * Created by andreas on 11.04.17.
 */
public class MinebdConfig {
    public int maxOpenFiles = MineboxExport.MAX_OPEN_FILES;
    public String parentDir = "minedbDat";
    public long reportedSize = 4 * Constants.GIGABYTE;
    public String encryptionSeed = "test";
    public int bucketSize = Ints.checkedCast(Constants.MEGABYTE * 40);

}
