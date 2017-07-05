package io.minebox.siaseed;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import ove.crypto.digest.Blake2b;

public class EngDictTest {
    @Test
    public void testSize() {
        Assert.assertEquals(1626, EngDict.dict.size());
        final String hexStr = "0123456789ABCDEF0123456789ABCDEF";
        final BigInteger integer = new BigInteger(hexStr, 16);
        final BigInteger value = new BigInteger("1512366075204170929049582354406559215");
        Assert.assertEquals(value, integer);

        final List<String> encoded = new MoneroSeed().mn_encode(hexStr);

        final Blake2b.Digest digest = Blake2b.Digest.newInstance((new Blake2b.Param()).setDigestLength(32));
        final byte[] blake2 = digest.digest(value.toByteArray());
        final byte[] expected = {113, 113, 76, 105, -66, 72, 20, -70, 105, -45, -102, 17, 41, -7, 116, 24, -92, 20, 59, -35, -51, 92, -35, 91, 34, -2, -40, 78, -45, -21, 93, 114};
        Assert.assertEquals(Arrays.toString(expected), Arrays.toString(blake2));
    }
}