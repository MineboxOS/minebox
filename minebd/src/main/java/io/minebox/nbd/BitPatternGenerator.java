package io.minebox.nbd;

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

    public byte[] createDeterministicPattern1(long offset) {
        final byte[] secret = Hashing.sha256().newHasher(Constants.SHA256_LEN + 4)
                .putLong(offset)
                .putBytes(secretKey)
                .hash()
                .asBytes();

        byte[] lastHash = secret;
        int numHashes = Constants.BLOCKSIZE / 32;
        final byte[] ret = new byte[Constants.BLOCKSIZE];
        for (int i = 0; i < numHashes; i++) {
            final byte[] nextHash = Hashing.sha256().newHasher(32).putBytes(lastHash).hash().asBytes();
            System.arraycopy(nextHash, 0, ret, i * Constants.SHA256_LEN, 32);
            lastHash = nextHash;
        }
        return ret;
    }

    public byte[] createDeterministicPattern2(long offset) {
        final byte[] secret = Hashing.sha256().newHasher(Constants.SHA256_LEN + 4)
                .putLong(offset)
                .putBytes(secretKey)
                .hash()
                .asBytes();

        int numHashes = Constants.BLOCKSIZE / 32;
        final byte[] ret = new byte[Constants.BLOCKSIZE];
        for (int i = 0; i < numHashes; i++) {
            final byte[] nextHash = Hashing.sha256().newHasher(32).putInt(i).putBytes(secret).hash().asBytes();
            System.arraycopy(nextHash, 0, ret, i * Constants.SHA256_LEN, 32);
        }
        return ret;
    }
}
