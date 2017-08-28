package io.minebox.nbd;

import io.minebox.nbd.download.AbstractDownload;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by andreas on 27.04.17.
 */
public class DownloadServiceTest {
    @Test
    public void getFilename() throws Exception {
        final String filename = AbstractDownload.extractFilename(" attachment; filename=backup.1492640813.zip");
        Assert.assertEquals("backup.1492640813.zip", filename);
    }

    @Test
    public void testSiaExtraction() throws Exception {


    }



}