package io.minebox.nbd.encryption;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.function.Function;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;

/**
 * Created by andreas on 11.04.17.
 */
public class BitPatternGenerator {

    private final MessageDigest digest;

    public BitPatternGenerator(String secret) {
        secretKey = Hashing.sha256().newHasher().putString(secret, Charsets.UTF_8).hash().asBytes();
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final byte[] secretKey;

    public byte[] createDeterministicPattern(long blockNumber) {
        return digestHash(blockNumber);
    }

    public byte[] guavaHash(long blockNumber) {
        return chainedHashing(blockNumber, this::guavaHash);
    }

    public byte[] digestHash(long blockNumber) {
        return chainedHashing(blockNumber, this::digestHash);
    }

    public byte[] secureRandomHashing(long blockNumber) {

        final SecureRandom secureRandom;
        try {
            secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(seedForThisBlock(blockNumber));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        final byte[] ret = new byte[EncConstants.BLOCKSIZE];
        secureRandom.nextBytes(ret);
//
//        secureRandom.longs(EncConstants.BLOCKSIZE / 8).forEach(value -> {
//        });
        return ret;
    }

    private byte[] seedForThisBlock(long blockNumber) {
        final byte[] bnarr = Longs.toByteArray(blockNumber);
        final int longArrLen = 8;
        final byte[] seedSecRandom = new byte[EncConstants.SHA256_LEN + longArrLen];

        System.arraycopy(bnarr, 0, seedSecRandom, 0, longArrLen);
        System.arraycopy(secretKey, 0, seedSecRandom, longArrLen, EncConstants.SHA256_LEN);
        return bnarr;
    }


    private byte[] chainedHashing(long blockNumber, Function<byte[], byte[]> nextHash2) {
        byte[] lastHash = Hashing.sha256().newHasher(EncConstants.SHA256_LEN + 4)
                .putLong(blockNumber)
                .putBytes(secretKey)
                .hash()
                .asBytes();
        int numHashes = EncConstants.BLOCKSIZE / EncConstants.SHA256_LEN;
        final byte[] ret = new byte[EncConstants.BLOCKSIZE];
        for (int i = 0; i < numHashes; i++) {
            final byte[] nextHash = nextHash2.apply(lastHash);
            System.arraycopy(nextHash, 0, ret, i * EncConstants.SHA256_LEN, EncConstants.SHA256_LEN);
            lastHash = nextHash;
        }
        return ret;
    }

    private byte[] guavaHash(byte[] lastHash) {
        return Hashing.sha256().newHasher(32).putBytes(lastHash).hash().asBytes();
    }

    private byte[] digestHash(byte[] lastHash) {
        digest.update(lastHash);
        final byte[] ret = this.digest.digest();
        digest.reset();
        return ret;
    }

}
