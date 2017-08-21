package io.minebox.nbd;

import net.lingala.zip4j.core.ZipFile;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

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