package io.minebox.nbd;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import io.minebox.config.MinebdConfig;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Created by andreas on 19.04.17.
 */
@Ignore("this requires mountnbd1.sh to be whitelisted with visudo to run correctly ")
//andreas ALL=(ALL) NOPASSWD: /home/andreas/minebox/mineblimp/minebd/mountnbd1.sh
public class CrashTest {

    @Test(timeout = 100000)

    public void testCrash() throws InterruptedException, IOException {
        CountDownLatch started = new CountDownLatch(1);
        final MinebdConfig config = TestUtil.createSampleConfig();
        config.nbdPort = 10811;
        final Server server = new Server(new SystemdUtil() {
            @Override
            void sendNotify() {
                started.countDown();
            }
        }, config);
        new Thread(server::startServer).start();
        started.await();
        final Process process = new ProcessBuilder("sudo", "./mountnbd1.sh")
                .inheritIO()
                .start();
        process.waitFor();
    }
}
