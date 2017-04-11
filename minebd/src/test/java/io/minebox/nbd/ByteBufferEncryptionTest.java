package io.minebox.nbd;

import java.nio.ByteBuffer;

import org.junit.Test;

/**
 * Created by andreas on 11.04.17.
 */
public class ByteBufferEncryptionTest {

    private BitPatternGenerator bitPatternGenerator = new BitPatternGenerator("thisIsMySecretKeyForTesting");

    @Test
    public void testSimpleXor() {
        long offset = Constants.BLOCKSIZE * 12345;
        final int msgSize = Constants.BLOCKSIZE * 64;
        final ByteBuffer result = testFor(offset, msgSize);
//        System.out.println("result = " + result.getLong());
    }

    public ByteBuffer testFor(long offset, int msgSize) {
        final byte[] plaintext = new byte[msgSize];
        final ByteBuffer plainBuffer = ByteBuffer.wrap(plaintext);

        byte[] blockXor = new byte[0];
        long curentBlockNumber = -1;
        final ByteBuffer result = ByteBuffer.wrap(new byte[msgSize]);

        while (plainBuffer.remaining() > 0) {
            final int bytes = plainBuffer.remaining();
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
        byte[] blockXor=bitPatternGenerator.createDeterministicPattern1(offset);
        return blockXor;
    }

}
