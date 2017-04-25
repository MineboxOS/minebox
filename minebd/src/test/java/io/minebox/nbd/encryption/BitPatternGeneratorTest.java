package io.minebox.nbd.encryption;

import com.google.common.primitives.Bytes;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by andreas on 25.04.17.
 */
public class BitPatternGeneratorTest {

    private BitPatternGenerator underTest = new BitPatternGenerator("test");

    @Test
    public void testSecureRandom() throws Exception { //sha-1 based
        final byte[] check = underTest.secureRandomHashing(50);
//        Assert.assertEquals(Bytes.asList(new byte[]{-29, -41, 126, 90, 68}), Bytes.asList(check).subList(0, 5));
    }

    @Test
    public void testGuava() throws Exception {
        final byte[] check = underTest.guavaHash(50);
//        Assert.assertEquals(Bytes.asList(new byte[]{54, 86, -66, 18, 104}), Bytes.asList(check).subList(0, 5));
    }

    @Test
    public void testMD() throws Exception {
        final byte[] check = underTest.digestHash(50);
//        Assert.assertEquals(Bytes.asList(new byte[]{54, 86, -66, 18, 104}), Bytes.asList(check).subList(0, 5));
    }
}

