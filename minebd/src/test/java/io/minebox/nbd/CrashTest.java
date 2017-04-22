package io.minebox.nbd;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

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
        final Server server = new Server(10811, new SystemdUtil() {
            @Override
            void sendNotify() {
                started.countDown();
            }
        });
//        nbd-client -N defaultMount localhost 10811 /dev/nbd1 -n
        new Thread(server::startServer).start();
        started.await();
//        Thread.sleep(1000);
//        System.out.println(new File(".").getAbsolutePath());
        final Process process = new ProcessBuilder("sudo", "./mountnbd1.sh")
                .inheritIO()
                .start();
        process.waitFor();
    }
}
