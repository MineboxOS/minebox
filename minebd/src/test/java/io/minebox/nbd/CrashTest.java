package io.minebox.nbd;

import java.net.BindException;
import java.util.concurrent.CountDownLatch;

import com.codahale.metrics.MetricRegistry;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.encryption.SymmetricEncryption;
import io.minebox.nbd.ep.BucketFactory;
import io.minebox.nbd.ep.MineboxExport;
import javafx.util.Duration;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Created by andreas on 19.04.17.
 */
@Ignore("this requires mountnbd1.sh to be whitelisted with visudo to run correctly ")
//andreas ALL=(ALL) NOPASSWD: /home/andreas/minebox/mineblimp/minebd/mountnbd1.sh
public class CrashTest {

    @Test(timeout = 100000)

    public void testCrash() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        final MinebdConfig cfg = TestUtil.createSampleConfig();
        cfg.encryptionSeed = "testDD";
        cfg.nbdPort = 10811;
        final SymmetricEncryption test123 = new SymmetricEncryption("test123");

        final BucketFactory bucketFactory = new BucketFactory(cfg, test123, new MetadataService());
        final NbdServer nbdServer = new NbdServer(new SystemdUtil() {
            @Override
            void sendNotify() {
                started.countDown();
            }
        }, cfg, new MineboxExport(cfg, new MetricRegistry(), bucketFactory));
        new Thread(() -> {
            try {
                nbdServer.start();
            } catch (BindException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        started.await();
        final long start = System.currentTimeMillis();
        final Process process = new ProcessBuilder("sudo", "./mountnbd1.sh")
                .inheritIO()
                .start();
        process.waitFor();
        final long duration = System.currentTimeMillis() - start;
        final Duration d = Duration.millis(duration);
        final double MBpS = 2000 / d.toSeconds();
        System.out.println("read + wrote 1GB  + 1GB in " + d.toSeconds() + " seconds");
        System.out.println(MBpS + " MB/sec");
        nbdServer.stop();
    }
}
