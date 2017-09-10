package io.minebox.nbd;

import io.dropwizard.util.Size;
import io.minebox.config.MinebdConfig;

import java.util.Arrays;
import java.util.Collections;

/**
 * Created by andreas on 22.04.17.
 */
public class TestUtil {
    public static MinebdConfig createSampleConfig() {
        final MinebdConfig config = new MinebdConfig();
        config.bucketSize = Size.megabytes(40);
        config.parentDirs = Collections.singletonList("testJunit");
        config.reportedSize = Size.gigabytes(4);
        config.maxOpenFiles = 10;
        return config;
    }
}
