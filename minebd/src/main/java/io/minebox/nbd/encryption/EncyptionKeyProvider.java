package io.minebox.nbd.encryption;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.ImplementedBy;

/**
 * Created by andreas on 18.05.17.
 */
@ImplementedBy(LazyEncyptionKeyProvider.class)
public interface EncyptionKeyProvider {
    ListenableFuture<String> getMasterPassword();

    String getImmediatePassword();
}
