package io.minebox.nbd;

import java.lang.reflect.Field;
import java.util.Set;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableSet;
import com.google.inject.*;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ProviderWithDependencies;
import io.minebox.config.ApiConfig;
import io.minebox.config.MinebdConfig;

/**
 * Created by andreas on 22.04.17.
 */
public class MineBdModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NbdStatsReporter.class).asEagerSingleton();
        bind(MetricRegistry.class).asEagerSingleton();
        bindMinebdConfigFields();

    }

    private void bindMinebdConfigFields() {
        for (Field field : MinebdConfig.class.getDeclaredFields()) {

            getObjectBinderFor(field)
                    .toProvider(new ProviderWithDependencies<Object>() {
                        private Provider<MinebdConfig> minebdConfig;

                        @Inject
                        public void setMinebdConfig(Provider<MinebdConfig> minebdConfig) {
                            this.minebdConfig = minebdConfig;
                        }

                        @Override
                        public Object get() {
                            final MinebdConfig minebdConfig = this.minebdConfig.get();
                            try {
                                return field.get(minebdConfig);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException("unable to do injection of " + field.getName(), e);
                            }
                        }

                        @Override
                        public Set<Dependency<?>> getDependencies() {
                            return ImmutableSet.of(Dependency.get(Key.get(MinebdConfig.class)));
                        }
                    });
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedBindingBuilder<Object> getObjectBinderFor(Field field) {
        return (LinkedBindingBuilder<Object>) bind(field.getType())
                .annotatedWith(Names.named(field.getName()));
    }

    @Provides
    public MinebdConfig getConfig(ApiConfig apiConfig) {
        return apiConfig.minebd;
    }
}
