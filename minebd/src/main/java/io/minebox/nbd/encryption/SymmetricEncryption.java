package io.minebox.nbd.encryption;

import java.nio.ByteBuffer;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.minebox.nbd.Encryption;
import io.minebox.nbd.NbdModule;

/**
 * Created by andreas on 11.04.17.
 */
public class SymmetricEncryption implements Encryption {
    private BitPatternGenerator bitPatternGenerator;
    private final String publicId;

    @Inject
    public SymmetricEncryption(@Named(NbdModule.ENCRYPTION_KEY) String key) {
        publicId = buildPubId(key);
        bitPatternGenerator = new BitPatternGenerator(key);
    }

    public String buildPubId(String key) {
        return Hashing.sha256().newHasher().putString("public", Charsets.UTF_8).putString(key, Charsets.UTF_8).hash().toString();
    }

    @Override
    public ByteBuffer encrypt(long offset, ByteBuffer message) {
        return encrypt(offset, message.remaining(), message);
    }

    @Override
    public String getPublicIdentifier() {
        return publicId;
    }

    private ByteBuffer encrypt(long offset, int msgSize, ByteBuffer plainBuffer) {
        byte[] blockXor = new byte[0];
        long curentBlockNumber = -1;
        final ByteBuffer result = ByteBuffer.wrap(new byte[msgSize]);

        while (plainBuffer.remaining() > 0) {
            final int pos = plainBuffer.position();

            long blockNumber = (pos + offset) / EncConstants.BLOCKSIZE;
            if (curentBlockNumber != blockNumber) {
                blockXor = createBlockXor(blockNumber);
            }
            curentBlockNumber = blockNumber;
            final byte value = plainBuffer.get();
            final int xorBlockIndex = pos % EncConstants.BLOCKSIZE;
            final byte encrypted = (byte) ((value ^ blockXor[xorBlockIndex]) & 0xFF);
            result.put(encrypted);
        }
        result.flip();
        return result;
    }

    private byte[] createBlockXor(long blockNumber) {
        return bitPatternGenerator.createDeterministicPattern(blockNumber);
    }
}
