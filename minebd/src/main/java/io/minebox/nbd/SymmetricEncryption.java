package io.minebox.nbd;

import java.nio.ByteBuffer;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Created by andreas on 11.04.17.
 */
public class SymmetricEncryption implements Encryption {
    private BitPatternGenerator bitPatternGenerator;

    @Inject
    public SymmetricEncryption(@Named(NbdModule.ENCRYPTION_KEY) String key) {
        bitPatternGenerator = new BitPatternGenerator(key);
    }

    @Override
    public ByteBuffer encrypt(ByteBuffer message, long offset) {
        return encrypt(offset, message.remaining(), message);
    }

    private ByteBuffer encrypt(long offset, int msgSize, ByteBuffer plainBuffer) {
        byte[] blockXor = new byte[0];
        long curentBlockNumber = -1;
        final ByteBuffer result = ByteBuffer.wrap(new byte[msgSize]);

        while (plainBuffer.remaining() > 0) {
            final int pos = plainBuffer.position();

            long blockNumber = (pos + offset) / Constants.BLOCKSIZE;
            if (curentBlockNumber != blockNumber) {
                blockXor = createBlockXor(offset);
            }
            curentBlockNumber = blockNumber;
            final byte value = plainBuffer.get();
            final int xorBlockIndex = pos % Constants.BLOCKSIZE;
            final byte encrypted = (byte) ((value ^ blockXor[xorBlockIndex]) & 0xFF);
            result.put(encrypted);
        }
        result.flip();
        return result;
    }

    private byte[] createBlockXor(long offset) {
        return bitPatternGenerator.createDeterministicPattern1(offset);
    }
}
