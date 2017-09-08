package io.minebox.nbd.download;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

//todo if needed, implement this differenty for unit tests/local tests. the live system uses systemctl
public class SiaProcessController {

    private static Process siadProcess;
    private static final Logger LOGGER = LoggerFactory.getLogger(SiaProcessController.class);

    private static void startJavaControlledProcess(File siaDirectory) {
        Preconditions.checkState(siadProcess == null);
        try {
            siadProcess = new ProcessBuilder("./siad")
                    .directory(siaDirectory)
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("unable to start siad");
        }
    }


    public void startProcess() {
        LOGGER.info("starting siad via systemctl");
        try {
            Runtime.getRuntime().exec(new String[]{"/usr/bin/systemctl", "start", "sia"});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopProcess() {
        LOGGER.info("stopping siad via systemctl");
        try {
            Runtime.getRuntime().exec(new String[]{"/usr/bin/systemctl", "stop", "sia"});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
     /*   try {
            siaCommand(Command.STOP, ImmutableMap.of());
        } catch (NoConnectException e) {
            LOGGER.warn("unable to stop gracefully");
            return;
        }
        try {
            while (true) {
                LOGGER.info("waiting for shutdown, querying version");
                final HttpResponse<String> response = siaCommand(Command.VERSION, ImmutableMap.of());
                Thread.sleep(1000);
            }
        } catch (NoConnectException | InterruptedException ignored) {
            LOGGER.info("shutdown seems complete");
            return;
        }*/
    }

}
