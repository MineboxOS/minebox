package io.minebox.nbd.ep.chunked;

import java.io.IOException;

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
        final MineboxExport export = new MineboxExport(new MineboxExport.Config());
        export.trim(0,MineboxExport.BUCKET_SIZE);
    }
    @Test
    public void checkPositiveBounds() throws IOException {
        final Bucket underTest = new Bucket(0, "testJunit");

        underTest.checkRange(0, MineboxExport.BUCKET_SIZE);
        assertExeption(() -> underTest.checkRange(0, MineboxExport.BUCKET_SIZE + 1));

        underTest.checkRange(MineboxExport.BUCKET_SIZE - 1, 1);


    }

}