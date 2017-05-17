package io.minebox.nbd;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import io.minebox.config.ApiConfig;
import io.minebox.config.MinebdConfig;

/**
 * Created by andreas on 22.04.17.
 */
public class NbdModule extends AbstractModule {
    
    @Override
    protected void configure() {
        bind(NbdStatsReporter.class).asEagerSingleton();
        bind(MetricRegistry.class).asEagerSingleton();
//        bind(MetricRegistry.class).asEagerSingleton();
    }

    @Provides
    @Named("httpMetadata")
    public String getHttpMetadata(MinebdConfig config) {
        return config.httpMetadata;
    }

    @Provides
    public MinebdConfig getConfig(ApiConfig apiConfig) {
        return apiConfig.minebd;
    }
}
