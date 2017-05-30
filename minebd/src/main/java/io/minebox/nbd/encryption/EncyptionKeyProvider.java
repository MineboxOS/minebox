package io.minebox.nbd.encryption;

import javax.annotation.Nullable;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.ImplementedBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by andreas on 18.05.17.
 */
@ImplementedBy(LazyEncyptionKeyProvider.class)
public interface EncyptionKeyProvider {
    Logger LOGGER = LoggerFactory.getLogger(EncyptionKeyProvider.class);

    ListenableFuture<String> getMasterPassword();

    String getImmediatePassword();

    default void onLoadKey(Runnable runnable) {
        Futures.addCallback(getMasterPassword(), new FutureCallback<String>() {
            @Override
            public void onSuccess(@Nullable String result) {
                runnable.run();
            }

            @Override
            public void onFailure(Throwable t) {
                LOGGER.error("failed to run callback " + runnable.toString() + " due to unexpected error while waiting for keyfile: ", t);
            }
        });
    }
}
