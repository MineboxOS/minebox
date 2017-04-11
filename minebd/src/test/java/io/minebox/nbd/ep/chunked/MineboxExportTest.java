package io.minebox.nbd.ep.chunked;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Created by andreas on 18.02.17.
 */
public class MineboxExportTest {

    private static long startTime;
    private static MineboxExport.Config cfg;

    @BeforeClass
    public static void getMeStarted() {
        System.out.println("Setup");
        startTime = System.currentTimeMillis();
        cfg = new MineboxExport.Config();
        cfg.parentDir = "minedbTESTDat";
    }
    @Test
    public void testCache() throws IOException {
        //trivial test to check out how the cache behaves. meant to be tested with a cache size of 2
        cfg.maxOpenFiles = 2;
        final MineboxExport underTest = new MineboxExport(cfg);
        underTest.read(0, 1024, false);
        underTest.read(12 * MineboxExport.MEGABYTE, 1024, false);
        underTest.read(22 * MineboxExport.MEGABYTE, 1024, false);
        underTest.read(32 * MineboxExport.MEGABYTE, 1024, false);
        underTest.read(111 * MineboxExport.MEGABYTE, 1024, false);

        Assert.assertEquals(2, new File(cfg.parentDir).list().length);
    }

    @AfterClass
    public static void getMeStopped() throws IOException {
        long current = System.currentTimeMillis();
        System.out.println("Stopped - took me " + (current - startTime) + " sec.");
        FileUtils.deleteDirectory(new File(cfg.parentDir));
    }
}