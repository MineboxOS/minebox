package io.minebox.nbd;

import info.faljse.SDNotify.SDNotify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SystemdUtil {
    private static final boolean hasEnv;
    private final static Logger LOGGER = LoggerFactory.getLogger(SystemdUtil.class);

    static {
        final String notifySocket = System.getenv().get("NOTIFY_SOCKET");
        hasEnv = !(notifySocket == null || notifySocket.length() == 0);
        if (!hasEnv) {
            LOGGER.info("we appear to run outside systemd");
        } else {
            LOGGER.info("we appear to run inside systemd");
        }
    }

    void sendStopping() {
        LOGGER.info("sendStopping");
        if (hasEnv) {
            SDNotify.sendStopping();
        }
    }

    void sendError(int errno) {
        LOGGER.info("sendErrno {}", errno);
        if (hasEnv) {
            SDNotify.sendErrno(errno);
        }
    }

    void sendNotify() {
        LOGGER.info("sendNotify");
        if (hasEnv) {
            SDNotify.sendNotify();
        }
    }
}
