package io.minebox.util;

import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalErrorHandler implements SubscriberExceptionHandler, Thread.UncaughtExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalErrorHandler.class);

    public static void handleError(String message, Throwable exception) {
        LOGGER.error(message, exception);
    }

    @Override
    public void handleException(Throwable exception, SubscriberExceptionContext context) {
        handleError("error in thread '" + Thread.currentThread().getName()
                + "' dispatching event to " + context.getSubscriber().getClass() + "." + context.getSubscriberMethod().getName(), exception);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        handleError("uncaught exception in thread " + t.getName(), e);
    }

}
