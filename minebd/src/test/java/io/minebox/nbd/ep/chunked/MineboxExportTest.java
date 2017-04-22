package io.minebox.nbd.ep.chunked;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import io.dropwizard.util.Size;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.Constants;
import io.minebox.nbd.NullEncryption;
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
    private static MinebdConfig cfg;

    @BeforeClass
    public static void getMeStarted() {
        System.out.println("Setup");
        startTime = System.currentTimeMillis();
        cfg = new MinebdConfig();
        cfg.parentDir = "minedbTESTDat";
    }

    @Test
    public void testCache() throws IOException {
        //trivial test to check out how the cache behaves. meant to be tested with a cache size of 2
        cfg.maxOpenFiles = 2;
        final MineboxExport underTest = new MineboxExport(cfg, new NullEncryption());
        underTest.read(0, 1024);
        underTest.read(12 * Constants.MEGABYTE, 1024);
        underTest.read(22 * Constants.MEGABYTE, 1024);
        underTest.read(32 * Constants.MEGABYTE, 1024);
        underTest.read(111 * Constants.MEGABYTE, 1024);

        Assert.assertEquals(2, new File(cfg.parentDir).list().length);
    }

    @AfterClass
    public static void getMeStopped() throws IOException {
        long current = System.currentTimeMillis();
        System.out.println("Stopped - took me " + (current - startTime) + " sec.");
        FileUtils.deleteDirectory(new File(cfg.parentDir));
    }

    @Test
    public void testBoundaries() throws IOException {
        final MinebdConfig cfg = new MinebdConfig();

        cfg.bucketSize = Size.bytes(16); //buckets for ants
        cfg.maxOpenFiles = 3;
        cfg.parentDir = "tinyfiles";

        final MineboxExport underTest = new MineboxExport(cfg, new NullEncryption());
        final byte[] data = new byte[257];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        Assert.assertEquals(0, data[0]);
        Assert.assertEquals(127, data[127]);
        Assert.assertEquals(-1, data[255]);
        Assert.assertEquals(0, data[256]);


        underTest.write(0, ByteBuffer.wrap(data), false);
        final ByteBuffer read = underTest.read(0, 257);
        Assert.assertEquals
                (0, read.get(0));
        Assert.assertEquals(0, read.get(256));
        Assert.assertEquals(25, read.get(25));


    }
}