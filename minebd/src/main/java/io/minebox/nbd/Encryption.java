package io.minebox.nbd;

import java.nio.ByteBuffer;

/**
 * Created by andreas on 11.04.17.
 */
public interface Encryption {
    ByteBuffer encrypt(ByteBuffer message, long offset);

    String getPublicIdentifier();
}
