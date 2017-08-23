package io.minebox.nbd;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;

import com.codahale.metrics.MetricRegistry;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.encryption.SymmetricEncryption;
import io.minebox.nbd.ep.BucketFactory;
import io.minebox.nbd.ep.MineboxExport;
import io.minebox.nbd.ep.NullDownloadService;
import io.minebox.nbd.ep.chunked.MineboxExportTest;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.fail;

@Ignore("this requires mountnbd1.sh to be whitelisted with visudo to run correctly ")
//andreas ALL=(ALL) NOPASSWD: /home/andreas/minebox/minebox-client-tools/minebd/mountnbd1.sh
public class CrashTest {

    @Test(timeout = 100000)

    public void testCrash() throws Exception {
        CountDownLatch started = new CountDownLatch(1);

        final StaticEncyptionKeyProvider keyProvider = new StaticEncyptionKeyProvider("test123");
        final SymmetricEncryption test123 = new SymmetricEncryption(keyProvider);
        MinebdConfig cfg = TestUtil.createSampleConfig();

        final BucketFactory bucketFactory = new BucketFactory(MineboxExportTest.SERIAL_NUMBER_SERVICE, cfg, test123, new NullDownloadService());
        final SystemdUtil mockSystemD = new SystemdUtil() {
            @Override
            void sendNotify() {
                started.countDown();
            }
        };
        final NbdServer nbdServer = new NbdServer(10811, mockSystemD, new MineboxExport(cfg, new MetricRegistry(), bucketFactory), keyProvider);
        new Thread(nbdServer::start).start();
        started.await();
        final long start = System.currentTimeMillis();
        final Process process = new ProcessBuilder("sudo", "./mountnbd1.sh")
                .inheritIO()
                .start();
        process.waitFor();
        final long duration = System.currentTimeMillis() - start;
        java.time.Duration d = java.time.Duration.of(duration, ChronoUnit.MILLIS);
        final long seconds = d.getSeconds();
        if (seconds == 0){
            fail();
        }
        final double MBpS = 2000 / seconds;
        System.out.println("read + wrote 1GB  + 1GB in " + seconds + " seconds");
        System.out.println(MBpS + " MB/sec");
        nbdServer.stop();
    }

}
