package io.minebox.nbd.ep.chunked;

import java.io.IOException;

import org.junit.Test;

/**
 * Created by andreas on 18.02.17.
 */
public class MineboxExportTest {

    @Test
    public void testCache() throws IOException {
        //trivial test to check out how the cache behaves. meant to be tested with a cache size of 2
        final MineboxExport.Config cfg = new MineboxExport.Config();
        cfg.maxOpenFiles = 2;
        final MineboxExport underTest = new MineboxExport(cfg);
        underTest.read(0, 1024, false);
        underTest.read(12 * MineboxExport.MEGABYTE, 1024, false);
        underTest.read(22 * MineboxExport.MEGABYTE, 1024, false);
        underTest.read(32 * MineboxExport.MEGABYTE, 1024, false);
    }
}