package io.minebox.nbd;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.minebox.nbd.encryption.EncyptionKeyProvider;

/**
 * Created by andreas on 18.05.17.
 */
public class StaticEncyptionKeyProvider implements EncyptionKeyProvider {
    private final String keyForTesting;

    public StaticEncyptionKeyProvider(String keyForTesting) {
        this.keyForTesting = keyForTesting;
    }

    @Override
    public ListenableFuture<String> getMasterPassword() {
        return Futures.immediateFuture(getImmediatePassword());
    }

    @Override
    public String getImmediatePassword() {
        return keyForTesting;
    }

}
