package io.minebox.nbd.download;

import io.minebox.sia.SiaFileUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class ExtractNameTest {
    @Test
    public void testExtract() {
        final File file = new File("minebox_v1_1234.dat");
        final int num = SiaFileUtil.fileToNumber(file.getName());
        Assert.assertEquals(1234, num);
        SiaFileUtil.getFileTime(" minebox_v1_9.1504724036.dat");
    }

}