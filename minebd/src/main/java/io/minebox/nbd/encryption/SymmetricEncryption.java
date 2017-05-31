package io.minebox.nbd.encryption;

import java.nio.ByteBuffer;

import com.google.inject.Inject;
import io.minebox.nbd.Encryption;

/**
 * Created by andreas on 11.04.17.
 */
public class SymmetricEncryption implements Encryption {
    private final EncyptionKeyProvider encyptionKeyProvider;
    private BitPatternGenerator bitPatternGenerator;

    @Inject
    public SymmetricEncryption(EncyptionKeyProvider encyptionKeyProvider) {
        this.encyptionKeyProvider = encyptionKeyProvider;
    }

    @Override
    public ByteBuffer encrypt(long offset, ByteBuffer message) {
        return encrypt(offset, message.remaining(), message);
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
        if (bitPatternGenerator == null) {
            bitPatternGenerator = new BitPatternGenerator(encyptionKeyProvider.getImmediatePassword());
        }
        return bitPatternGenerator.createDeterministicPattern(blockNumber);
    }
}
