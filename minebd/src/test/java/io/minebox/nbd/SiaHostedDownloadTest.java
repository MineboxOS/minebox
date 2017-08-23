package io.minebox.nbd;

import ch.qos.logback.classic.Level;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.minebox.SiaUtil;
import net.lingala.zip4j.core.ZipFile;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.Callable;

@Ignore //we need a solution for the big pristine file
public class SiaHostedDownloadTest {

    private static Process siadProcess;
    private static File siadDirectoryFile;
    private static SiaUtil siaUtil;

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

    static {
        final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.apache.http");
        logger.setLevel(Level.INFO);
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
        siaUtil = new SiaUtil("http://localhost:9980");
//        siadDirectoryFile = recreatePristineSiad();
        siadDirectoryFile = new File("junit/sia/Sia-v1.3.0-linux-amd64/");
//        startSiad(siadDirectoryFile);
    }

    private static void startSiad(File siadDirectoryFile) throws IOException {
        siadProcess = new ProcessBuilder("./siad")
                .directory(siadDirectoryFile)
                .inheritIO()
                .start();
        siaUtil.waitForConsensus();
        siaUtil.unlockWallet("rage return onto madness abort vegan under inwardly madness stick swept tucks demonstrate duration viking oneself extra jabbed arsenic dotted renting orchid honked mighty onslaught batch tugs jagged absorb");
    }

    @AfterClass
    public static void tearDownSiad() throws Exception {
        stopSiad();
    }

    private static void stopSiad() {
        siaUtil.gracefulStop();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (siadProcess != null) {
            siadProcess.destroyForcibly();
        }
    }


    @Test
    public void testInit() throws Exception {
        final RemoteTokenService dummyRemoteToken = new RemoteTokenService(null, null) {
            @Override
            public Optional<String> getToken() {
                return Optional.of("123");
            }
        };
        //acutally, we want to make sure, sia is not running here...
        final SiaHostedDownload underTest = new SiaHostedDownload(siaUtil,
                null,
                "junit/sia/Sia-v1.3.0-linux-amd64",
                dummyRemoteToken,
                new StaticEncyptionKeyProvider("123")) {
            @Override
            protected InputStream downloadLatestMetadataZip(String token) throws UnirestException {
                try {
                    return new FileInputStream("/home/andreas/minebox/backup.1503407403.zip");
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            protected void finishedDigest() {
                restartSiad();
            }
        };
        underTest.initKeyListener();
        for (String filename : underTest.allFilenames()) {
            final File dest = new File("/tmp/sia/", filename);
            System.out.println("attempting restore of: " + filename + " to destination " + dest.getAbsolutePath());
            final DownloadService.RecoveryStatus recoveryStatus = underTest.downloadIfPossible(dest);
            System.out.println(dest.getAbsolutePath() + " recovery: " + recoveryStatus);
        }
    }

    private void restartSiad() {
        stopSiad();
        try {
            startSiad(siadDirectoryFile);
        } catch (IOException e) {
            Assert.fail(e.getMessage());
            throw new RuntimeException(e);
        }
    }
}