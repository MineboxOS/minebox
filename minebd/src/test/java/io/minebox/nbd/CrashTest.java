package io.minebox.nbd;

import java.net.BindException;
import java.util.concurrent.CountDownLatch;

import com.codahale.metrics.MetricRegistry;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.ep.BucketFactory;
import io.minebox.nbd.ep.MineboxExport;
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
        cfg.nbdPort = 10811;
        final BucketFactory bucketFactory = new BucketFactory(cfg, new NullEncryption(), new MetadataService());
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
        final Process process = new ProcessBuilder("sudo", "./mountnbd1.sh")
                .inheritIO()
                .start();
        process.waitFor();
        nbdServer.stop();
    }
}
