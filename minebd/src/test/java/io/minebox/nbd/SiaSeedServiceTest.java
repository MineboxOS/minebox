package io.minebox.nbd;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.primitives.UnsignedBytes;
import io.minebox.siaseed.EngDict;
import org.junit.Assert;
import org.junit.Test;
import ove.crypto.digest.Blake2b;

/**
 * Created by andreas on 05.07.17.
 */
public class SiaSeedServiceTest {

    public static final List<String> TEST_VECTOR_WODS = Arrays.asList(
            "remedy"
            , "circle"
            , "muddy"
            , "kiwi"
            , "avoid"
            , "last"
            , "emit"
            , "topic"
            , "tether"
            , "uncle"
            , "otter"
            , "february"
            , "bemused"
            , "reheat"
            , "tamper"
            , "went"
            , "etiquette"
            , "does"
            , "against"
            , "organs"
            , "bypass"
            , "ferry"
            , "likewise"
            , "alchemy"
            , "rhythm"
            , "aztec"
            , "joking"
            , "lakes"
            , "ablaze");
    public static final int[] TESTVECTOR_UNSIGNED_BLAKE_HASH = new int[]{
            105, 209, 92, 67, 166, 27, 27, 108, 218, 193, 51, 73, 244, 86, 251, 200, 210, 80, 201, 51, 158, 60, 61, 195, 222, 189, 38, 239, 122, 3, 191, 72};
    public static final int[] TESTVECTOR_UNSIGED_SIA_BYTES = new int[]{145, 22, 237, 123, 122, 60, 52, 32, 120, 204, 197, 45, 153, 25, 15, 183, 169, 246, 78, 38, 35, 38, 68, 69, 38, 176, 7, 235, 234, 172, 124, 101};

    @Test
    public void testServerSeed() throws Exception {
        ;
        StaticEncyptionKeyProvider
                encyptionKeyProvider = new StaticEncyptionKeyProvider("deposit cotton rib long badge flip butter pipe garbage kind energy inherit");
        String siaSeed = new SiaSeedService(encyptionKeyProvider).getSiaSeed();
        System.out.println(siaSeed);
    }

    @Test
    public void makeSingleSeed() {
        final SiaSeedService siaSeedService = new SiaSeedService(null);
        final List<String> words = siaSeedService.buildSiaSeed("123");
        final String wordsList = Joiner.on(" ").join(words);
        Assert.assertEquals("hotel musical lending pheasants tidy awkward owed viking mews pimple river coexist noises bikini cowl enjoy rebel vapidly envy twofold peculiar unnoticed tobacco punch gels avidly pepper nimbly agile", wordsList);

    }


    @Test
    public void getSiaSeed() throws Exception {
        final SiaSeedService siaSeedService = new SiaSeedService(new StaticEncyptionKeyProvider("123"));
//        final List<String> strings = siaSeedService.buildSiaSeed("123");
        final Multiset<Long> counts;
        counts = HashMultiset.create();
        for (int i = 0; i < 100000; i++) {
            final String secretKey = "abc123782567825784__" + i;
            final List<String> words = siaSeedService.buildSiaSeed(secretKey);
            final String wordsList = Joiner.on(" ").join(words);
            final String errrorMessage = "secret produced unexpected length: " + secretKey + " words: " + wordsList;
            counts.add((long) words.size());
//            Assert.assertEquals(errrorMessage, 29, words.size());

        }
        counts.forEachEntry((length, count) -> System.out.println(length + " occurred " + count + " times"));
    }


    @Test
    public void testBlakeHash() {
        Assert.assertEquals(32, TESTVECTOR_UNSIGED_SIA_BYTES.length);
        Assert.assertEquals(32, TESTVECTOR_UNSIGNED_BLAKE_HASH.length);

        byte[] signedSiaBytes = new byte[32];
        for (int i = 0; i < TESTVECTOR_UNSIGED_SIA_BYTES.length; i++) {
            signedSiaBytes[i] = UnsignedBytes.checkedCast(TESTVECTOR_UNSIGED_SIA_BYTES[i]);
        }
        final byte[] result = SiaSeedService.blakeHash(signedSiaBytes);

        for (int i = 0; i < result.length; i++) {
            byte b = result[i];
            Assert.assertEquals(UnsignedBytes.checkedCast(TESTVECTOR_UNSIGNED_BLAKE_HASH[i]), b);
        }
        final List<String> strings = SiaSeedService.buildSiaWords(signedSiaBytes);
        Assert.assertEquals(TEST_VECTOR_WODS, strings);

    }

    @Test
    public void testSize() {
        Assert.assertEquals(1626, EngDict.dict.size());
        final String hexStr = "0123456789ABCDEF0123456789ABCDEF";
        final BigInteger integer = new BigInteger(hexStr, 16);
        final BigInteger value = new BigInteger("1512366075204170929049582354406559215");
        Assert.assertEquals(value, integer);

        final List<String> encoded = SiaSeedService.siaEncode(integer);

        final Blake2b.Digest digest = Blake2b.Digest.newInstance((new Blake2b.Param()).setDigestLength(32));
        final byte[] blake2 = digest.digest(value.toByteArray());
        final byte[] expected = {113, 113, 76, 105, -66, 72, 20, -70, 105, -45, -102, 17, 41, -7, 116, 24, -92, 20, 59, -35, -51, 92, -35, 91, 34, -2, -40, 78, -45, -21, 93, 114};
        Assert.assertEquals(Arrays.toString(expected), Arrays.toString(blake2));
    }
}