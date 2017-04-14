package io.minebox.nbd;

import java.nio.ByteBuffer;

import org.junit.Test;

/**
 * Created by andreas on 11.04.17.
 */
public class ByteBufferEncryptionTest {


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
        final Encryption encryption = new SymmetricEncryption("keyForTesting");
        final ByteBuffer result = encryption.encrypt(plainBuffer, offset);
        return result;
    }


}
