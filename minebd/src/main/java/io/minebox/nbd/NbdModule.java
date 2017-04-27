package io.minebox.nbd;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import io.minebox.config.MinebdConfig;

/**
 * Created by andreas on 22.04.17.
 */
public abstract class NbdModule extends AbstractModule {

    public static final String ENCRYPTION_KEY = "encryptionKey";

    @Override
    protected void configure() {
        bind(NbdStatsReporter.class).asEagerSingleton();
        bind(MetricRegistry.class).asEagerSingleton();
//        bind(MetricRegistry.class).asEagerSingleton();
    }

    @Provides
    @Named(ENCRYPTION_KEY)
    public String getEncryptionKey(MinebdConfig config) {
        return config.encryptionSeed;
    }

    @Provides
    @Named("httpMetadata")
    public String getHttpMetadata(MinebdConfig config) {
        return config.httpMetadata;
    }
}
