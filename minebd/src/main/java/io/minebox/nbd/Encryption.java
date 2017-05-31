package io.minebox.nbd;

import java.nio.ByteBuffer;

import com.google.inject.ImplementedBy;
import io.minebox.nbd.encryption.SymmetricEncryption;

/**
 * Created by andreas on 11.04.17.
 */
@ImplementedBy(NullEncryption.class)
public interface Encryption {
    ByteBuffer encrypt(long offset, ByteBuffer message);

    String getPublicIdentifier();
}
