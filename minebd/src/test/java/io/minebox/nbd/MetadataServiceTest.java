package io.minebox.nbd;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by andreas on 27.04.17.
 */
public class MetadataServiceTest { //this could be more thorough
    @Test
    public void getFilename() throws Exception {
        final String filename = MetadataServiceImpl.extractFilename(" attachment; filename=backup.1492640813.zip");
        Assert.assertEquals("backup.1492640813.zip", filename);
    }

}