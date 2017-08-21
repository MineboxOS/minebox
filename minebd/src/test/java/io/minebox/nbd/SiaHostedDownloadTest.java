package io.minebox.nbd;

import com.mashape.unirest.http.exceptions.UnirestException;
import io.minebox.SiaUtil;
import net.lingala.zip4j.core.ZipFile;
import org.apache.commons.io.FileUtils;
import org.junit.*;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.Callable;

public class SiaHostedDownloadTest {

    private static Process siadProcess;

    private static <T> T doTimed(Callable<T> runnable) {
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

    private static File recreatePristineSiad() throws IOException {
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

    @BeforeClass
    public static void setupSiad() throws IOException {

        final File siadDirectoryFile = recreatePristineSiad();

        siadProcess = new ProcessBuilder("./siad")
                .directory(siadDirectoryFile)
                .inheritIO()
                .start();
    }

    @AfterClass
    public static void tearDownSiad() throws Exception {
        siadProcess.destroyForcibly();
    }


    @Test
    public void testInit() throws Exception {
        final SiaUtil siaUtil = new SiaUtil("http://localhost:9980");
        siaUtil.waitForConsensus();
        siaUtil.unlockWallet("rage return onto madness abort vegan under inwardly madness stick swept tucks demonstrate duration viking oneself extra jabbed arsenic dotted renting orchid honked mighty onslaught batch tugs jagged absorb");

        final RemoteTokenService dummyRemoteToken = new RemoteTokenService(null, null) {
            @Override
            public Optional<String> getToken() {
                return Optional.of("123");
            }
        };
        final SiaHostedDownload underTest = new SiaHostedDownload(siaUtil,
                null,
                "junit/sia/Sia-v1.3.0-linux-amd64",
                dummyRemoteToken,
                new StaticEncyptionKeyProvider("123")) {
            @Override
            protected InputStream downloadLatestMetadataZip(String token) throws UnirestException {
                try {
                    return new FileInputStream("/home/andreas/minebox/backup.1503060902.zip");
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

        };
        underTest.initKeyListener();
        final boolean result = underTest.downloadIfPossible(new File("minebox_v1_0.dat"));


    }
}