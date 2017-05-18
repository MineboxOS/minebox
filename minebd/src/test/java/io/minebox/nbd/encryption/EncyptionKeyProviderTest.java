package io.minebox.nbd.encryption;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.google.common.base.Charsets;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by andreas on 18.05.17.
 */
public class EncyptionKeyProviderTest {

    public static final String PW = "123";
    public static final byte[] PWBYTES = PW.getBytes(Charsets.UTF_8);

    @Test(expected = IllegalStateException.class)
    public void testNotLoadedYet() throws Exception {
        final LazyEncyptionKeyProvider key = new LazyEncyptionKeyProvider("./junit/etc/minebox/randomkey.txt");
        key.getImmediatePassword();
    }

    @Test()
    public void testKeyCreated() throws Exception {
        final Path keyfile = Paths.get("./junit/etc/minebox/randomkey.txt");
        Files.write(keyfile, PWBYTES);
        try {
            Assert.assertTrue(Files.exists(keyfile));
            final LazyEncyptionKeyProvider key = new LazyEncyptionKeyProvider("./junit/etc/minebox/randomkey.txt");
            Thread.sleep(100); //waiting 100ms to load file from disk
            Assert.assertEquals(PW, key.getImmediatePassword());
        } finally {
            Files.delete(keyfile);
        }
    }

    @Test()
    public void testTrigger() throws Exception {
        final Path keyfile = Paths.get("./junit/etc/minebox/randomkey.txt");
        try {
            Assert.assertFalse(Files.exists(keyfile));
            final LazyEncyptionKeyProvider key = new LazyEncyptionKeyProvider("./junit/etc/minebox/randomkey.txt");
            final ListenableFuture<String> masterPassword = key.getMasterPassword();
            Assert.assertFalse(masterPassword.isDone());
            Files.write(keyfile, PWBYTES);
            Thread.sleep(100);
            Assert.assertTrue(masterPassword.isDone());
            Assert.assertEquals(PW, key.getImmediatePassword());
        } finally {
            Files.delete(keyfile);
        }
    }

    @Test()
    public void testListener() throws Exception {
        final Path keyfile = Paths.get("./junit/etc/minebox/randomkey.txt");
        try {
            Assert.assertFalse(Files.exists(keyfile));
            final LazyEncyptionKeyProvider key = new LazyEncyptionKeyProvider("./junit/etc/minebox/randomkey.txt");
            final ListenableFuture<String> masterPassword = key.getMasterPassword();
            Assert.assertFalse(masterPassword.isDone());
            final CountDownLatch count = new CountDownLatch(1);
            masterPassword.addListener(count::countDown, Executors.newSingleThreadExecutor());
            Files.write(keyfile, PWBYTES);
            count.await();
            Assert.assertTrue(masterPassword.isDone());
            Assert.assertEquals(PW, key.getImmediatePassword());
        } finally {
            Files.delete(keyfile);
        }
    }
}