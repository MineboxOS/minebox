package io.minebox.nbd;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.minebox.config.ApiConfig;
import io.minebox.config.MinebdConfig;

/**
 * Created by andreas on 22.04.17.
 */
public class NbdModule extends AbstractModule {


    @Provides
    public MinebdConfig getConfig(ApiConfig apiConfig) {
        return apiConfig.minebd;
    }

    @Override
    protected void configure() {
    }
}
