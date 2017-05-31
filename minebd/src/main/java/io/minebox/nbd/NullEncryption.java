package io.minebox.nbd;

import java.nio.ByteBuffer;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

/**
 * Created by andreas on 11.04.17.
 */
public class NullEncryption implements Encryption {

    public static final String PUB_ID = Hashing.sha256().newHasher().putString("Junit", Charsets.UTF_8).hash().toString();

    @Override
    public ByteBuffer encrypt(long offset, ByteBuffer message) {
        return message;
    }

    @Override
    public String getPublicIdentifier() {
        return PUB_ID;
    }
}
