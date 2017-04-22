package io.minebox.nbd;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.ep.ExportProvider;
import io.minebox.nbd.ep.MineboxExport;

/**
 * Created by andreas on 22.04.17.
 */
public abstract class NbdModule extends AbstractModule {

    public static final String ENCRYPTION_KEY = "encryptionKey";

    @Override
    protected void configure() {
        bind(Encryption.class).to(SymmetricEncryption.class);
        bind(ExportProvider.class).to(MineboxExport.class);
    }

    @Provides
    @Named(ENCRYPTION_KEY)
    public String getEncryptionKey(MinebdConfig config) {
        return config.encryptionSeed;
    }
}
