package io.minebox.nbd;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.ep.ExportProvider;
import io.minebox.nbd.ep.chunked.MineboxExport;

/**
 * Created by andreas on 22.04.17.
 */
public abstract class NbdModule extends AbstractModule {


    public static final String ENCRYPTION_KEY = "encryptionKey";


    @Override
    protected void configure() {
        bind(Encryption.class).to(SymmetricEncryption.class);
        bind(ExportProvider.class).to(MineboxExport.class);
        bind(MinebdConfig.class).toProvider(new Provider<MinebdConfig>() {
            @Override
            public MinebdConfig get() {
                return getConfig();
            }
        });
       /* bind(String.class).annotatedWith(Names.named(ENCRYPTION_KEY)).toProvider(new ProviderWithDependencies<String>() {
            @Override
            public String get() {
                return null;
            }

            @Override
            public Set<Dependency<?>> getDependencies() {
                return null;
            }
        })*/

    }

    public abstract MinebdConfig getConfig();

    @Provides
    @Named(ENCRYPTION_KEY)
    public String getEncryptionKey() {
        return getConfig().encryptionSeed;
    }
}
