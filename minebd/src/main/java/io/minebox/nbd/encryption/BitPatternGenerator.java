package io.minebox.nbd.encryption;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

/**
 * Created by andreas on 11.04.17.
 */
public class BitPatternGenerator {

    public BitPatternGenerator(String secret) {
        secretKey = Hashing.sha256().newHasher().putString(secret, Charsets.UTF_8).hash().asBytes();
    }

    private final byte[] secretKey;

    public byte[] createDeterministicPattern(long blockNumber) {
        byte[] lastHash = Hashing.sha256().newHasher(EncConstants.SHA256_LEN + 4)
                .putLong(blockNumber)
                .putBytes(secretKey)
                .hash()
                .asBytes();
        int numHashes = EncConstants.BLOCKSIZE / EncConstants.SHA256_LEN;
        final byte[] ret = new byte[EncConstants.BLOCKSIZE];
        for (int i = 0; i < numHashes; i++) {
            final byte[] nextHash = Hashing.sha256().newHasher(32).putBytes(lastHash).hash().asBytes();
            System.arraycopy(nextHash, 0, ret, i * EncConstants.SHA256_LEN, EncConstants.SHA256_LEN);
            lastHash = nextHash;
        }
        return ret;
    }
}
