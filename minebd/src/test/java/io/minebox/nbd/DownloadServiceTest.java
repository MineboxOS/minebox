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

        final File siadDirectoryFile = recreatePristineSiad();

        final Process siadProcess = new ProcessBuilder("./siad")
                .directory(siadDirectoryFile)
                .inheritIO()
                .start();

        Thread.sleep(10000);
        siadProcess.destroy();


    }

    private File recreatePristineSiad() throws IOException {
        doTimed(() -> {
            File zipFile = new File("../../sia-preloaded.zip");
            ZipFile preloaded = new ZipFile(zipFile);
            final File extractTo = new File("junit/sia").getCanonicalFile();
            FileUtils.deleteDirectory(extractTo);

            preloaded.extractAll(extractTo.getCanonicalPath());
            return null;
        });
        final String siadDir = "junit/sia/Sia-v1.3.0-linux-amd64/";
        final File siadDirectoryFile = new File(siadDir).getCanonicalFile();
        final boolean result = new File(siadDirectoryFile, "siad").setExecutable(true);
        Assert.assertTrue("unable to set siad executable", result);
        return siadDirectoryFile;
    }

    private <T> T doTimed(Callable<T> runnable) {
        final long start = System.currentTimeMillis();
        System.out.println("starting...");
        try {
            final T ret = runnable.call();
            System.out.println("finished in " + (System.currentTimeMillis() - start) + " ms");
            return ret;
        } catch (Exception e) {
            System.out.println("finished with error in " + (System.currentTimeMillis() - start) + " ms");
            throw new RuntimeException(e);
        }
    }
}