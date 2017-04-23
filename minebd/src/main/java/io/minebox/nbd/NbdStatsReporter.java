package io.minebox.nbd;

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Slf4jReporter;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by andreas on 23.04.17.
 */
public class NbdStatsReporter {
    private final static Logger LOGGER = LoggerFactory.getLogger(NbdStatsReporter.class);

    @Inject
    public NbdStatsReporter(MetricRegistry metrics) {
        ScheduledReporter reporter = Slf4jReporter.forRegistry(metrics)
                .withLoggingLevel(Slf4jReporter.LoggingLevel.DEBUG)
                .outputTo(LOGGER)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(5, TimeUnit.SECONDS);
    }
}
