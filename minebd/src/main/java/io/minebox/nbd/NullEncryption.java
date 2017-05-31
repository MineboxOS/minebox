package io.minebox.nbd;

import java.nio.ByteBuffer;

/**
 * Created by andreas on 11.04.17.
 */
public class NullEncryption implements Encryption {

    @Override
    public ByteBuffer encrypt(long offset, ByteBuffer message) {
        return message;
    }
}
