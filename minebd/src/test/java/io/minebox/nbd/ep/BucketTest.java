package io.minebox.nbd.ep;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.dropwizard.util.Size;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.NullEncryption;
import io.minebox.nbd.TestUtil;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Created by andreas on 20.02.17.
 */
public class BucketTest {


    private void assertExeption(Runnable r) {
        try {
            r.run();
            fail();
        } catch (UnsupportedOperationException e) {
            //success
        }
    }

    @Test
    public void testExport() throws IOException {
        final MinebdConfig config = TestUtil.createSampleConfig();
        final MineboxExport export = new MineboxExport(config, new NullEncryption());
        export.open("test");
        export.write(0, ByteBuffer.wrap(new byte[]{1, 2, 3}), true);
        export.read(0, 100);
        export.trim(0, (int) config.bucketSize.toBytes());
    }

    @Test
    public void checkPositiveBounds() throws IOException {
        long bucketSize = Size.megabytes(40).toBytes();

        final Bucket underTest = new Bucket(0, "testJunit", bucketSize);


        Assert.assertEquals(bucketSize, underTest.calcLengthInThisBucket(0, bucketSize));

        Assert.assertEquals(bucketSize, underTest.calcLengthInThisBucket(0, bucketSize + 1));


        Assert.assertEquals(bucketSize - 50, underTest.calcLengthInThisBucket(50, bucketSize + 1));

        Assert.assertEquals(1, underTest.calcLengthInThisBucket(bucketSize - 1, 1));

        assertExeption(() -> underTest.calcLengthInThisBucket(bucketSize, 0));

        assertExeption(() -> underTest.calcLengthInThisBucket(-1, 1));

    }

}