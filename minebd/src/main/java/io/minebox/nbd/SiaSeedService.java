package io.minebox.nbd;

import java.math.BigInteger;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.common.primitives.UnsignedBytes;
import com.google.inject.Inject;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import io.minebox.siaseed.EngDict;
import ove.crypto.digest.Blake2b;

/**
 * Created by andreas on 31.05.17.
 */
public class SiaSeedService {

    public static final int CHECKSUM_LENGTH = 6;
    public static final int SHALENGTH = 32;
    private final EncyptionKeyProvider encyptionKeyProvider;
    private List<String> siaSeed;

    @Inject
    public SiaSeedService(EncyptionKeyProvider encyptionKeyProvider) {
        this.encyptionKeyProvider = encyptionKeyProvider;
    }

    public String getSiaSeed() {
        if (siaSeed == null) {
            siaSeed = buildSiaSeed(encyptionKeyProvider.getImmediatePassword());
        }
        return Joiner.on(" ").join(siaSeed);
    }

    @VisibleForTesting
    static List<String> buildSiaSeed(String key) {
        final byte[] siaBytes = Hashing.sha256().newHasher().putString("siaSeed", Charsets.UTF_8).putString(key, Charsets.UTF_8).hash().asBytes();
        return buildSiaWords(siaBytes);
    }

    @VisibleForTesting
    static List<String> buildSiaWords(byte[] siaBytes) {
        byte[] seedWithChecksum = appendChecksum(siaBytes);
        final BigInteger bi = makeBigInteger(seedWithChecksum);
        return siaEncode(bi);
    }

    private static BigInteger makeBigInteger(byte[] seedWithChecksum) {
//        porting over the Sia method of this conversion
        // The conversion functions can be seen as changing the base of a number. A
// []byte can actually be viewed as a slice of base-256 numbers, and a []dict
// can be viewed as a slice of base-1626 numbers. The conversions are a little
// strange because leading 0's need to be preserved.
//
// For example, in base 256:
//
//		{0} -> 0
//		{255} -> 255
//		{0, 0} -> 256
//		{1, 0} -> 257
//		{0, 1} -> 512
//
// Every possible []byte has a unique big.Int which represents it, and every
// big.Int represents a unique []byte.

        // bytesToInt converts a byte slice to a big.Int in a way that preserves
// leading 0s, and ensures there is a perfect 1:1 mapping between Int's and
// []byte's.
        BigInteger base = BigInteger.valueOf(256);
        BigInteger exp = BigInteger.valueOf(1);
        BigInteger result = BigInteger.valueOf(-1);
        for (byte b : seedWithChecksum) {
            BigInteger tmp = BigInteger.valueOf(UnsignedBytes.toInt(b));
            tmp = tmp.add(BigInteger.ONE);
            tmp = tmp.multiply(exp);
            exp = exp.multiply(base);
            result = result.add(tmp);
        }
        return result;
        /*func bytesToInt(bs []byte) *big.Int {
            base := big.NewInt(256)
            exp := big.NewInt(1)
            result := big.NewInt(-1)
            for i := 0; i < len(bs); i++ {
                tmp := big.NewInt(int64(bs[i]))
                tmp.Add(tmp, big.NewInt(1))
                tmp.Mul(tmp, exp)
                exp.Mul(exp, base)
                result.Add(result, tmp)
            }
            return result
        }*/

    }


    private static byte[] appendChecksum(byte[] siaBytes) {
        final byte[] fullChecksum = blakeHash(siaBytes);
        byte[] seedWithChecksum = new byte[SHALENGTH + CHECKSUM_LENGTH];
        System.arraycopy(siaBytes, 0, seedWithChecksum, 0, SHALENGTH);
        System.arraycopy(fullChecksum, 0, seedWithChecksum, SHALENGTH, CHECKSUM_LENGTH);
        return seedWithChecksum;
    }

    @VisibleForTesting
    static byte[] blakeHash(byte[] siaBytes) {
        final Blake2b.Digest digest = Blake2b.Digest.newInstance((new Blake2b.Param()).setDigestLength(SHALENGTH));
        return digest.digest(siaBytes);
    }

    public static List<String> siaEncode(final BigInteger bi) {
        BigInteger base = BigInteger.valueOf(EngDict.size);
//        base := big.NewInt(DictionarySize)
        BigInteger remaining = bi;
        ImmutableList.Builder<String> b = ImmutableList.builder();
        while (remaining.compareTo(base) >= 0) {
            int i = remaining.mod(base).intValueExact();
            b.add(EngDict.dict.get(i));
            remaining = remaining.subtract(base).divide(base);
        }
        b.add(EngDict.dict.get(remaining.intValueExact()));
        return b.build();
//        for bi.Cmp(base) >= 0 {
//            i:=new (big.Int).Mod(bi, base).Int64()
//            p = append(p, dict[i])
//            bi.Sub(bi, base)
//            bi.Div(bi, base)
//        }
//        p = append(p, dict[bi.Int64()])
//        return p,nil;

    }
}
