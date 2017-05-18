package io.minebox.nbd;

import java.nio.ByteBuffer;

import io.minebox.nbd.encryption.EncConstants;
import io.minebox.nbd.encryption.SymmetricEncryption;
import org.junit.Test;

/**
 * Created by andreas on 11.04.17.
 */
public class ByteBufferEncryptionTest {


    @Test
    public void testSimpleXor() {
        long offset = EncConstants.BLOCKSIZE * 12345;
        final int msgSize = Math.toIntExact(Constants.MEGABYTE); //
        final ByteBuffer result = testFor(offset, msgSize);
//        System.out.println("result = " + result.getLong());
    }

    public ByteBuffer testFor(long offset, int msgSize) {
        final byte[] plaintext = new byte[msgSize];
        final ByteBuffer plainBuffer = ByteBuffer.wrap(plaintext);
        final Encryption encryption = new SymmetricEncryption(new StaticEncyptionKeyProvider("keyForTesting"));
        return encryption.encrypt(offset, plainBuffer);
    }


}
