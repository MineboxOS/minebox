package io.minebox.siaseed;

import java.math.BigInteger;
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

        final Blake2b.Digest digest = Blake2b.Digest.newInstance((new Blake2b.Param()).setDigestLength(20));
        final byte[] blake2 = digest.digest(value.toByteArray());
        final byte[] expected = {125, 12, -121, -19, 57, 28, -116, 127, -34, 65, 80, -118, 70, -1, 27, -13, 101, -80, 103, -86};
        Assert.assertEquals(expected, blake2);


    }

}