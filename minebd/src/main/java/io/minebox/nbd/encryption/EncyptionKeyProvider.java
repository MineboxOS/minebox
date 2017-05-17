package io.minebox.nbd.encryption;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import io.minebox.config.MinebdConfig;
import io.minebox.util.FileUtil;

/**
 * Created by andreas on 12.05.17.
 */
public class EncyptionKeyProvider {

    private final MinebdConfig config;
    private WatchKey watchKey;
    private Path seedFile;
    private WatchService watchService;
    private ListenableFuture<String> futurePassword;
    private ListeningExecutorService executorService;

    @Inject
    public EncyptionKeyProvider(MinebdConfig config) {
        this.config = config;
        try {
            seedFile = Paths.get(config.encryptionSeed);
            final URI seedUri = URI.create(config.encryptionSeed);
            watchService = FileSystems.getDefault().newWatchService();
            final Path parent = seedFile.getParent();
            if (!Files.isDirectory(parent)) {
                final PosixFileAttributes attributes = Files.readAttributes(parent, PosixFileAttributes.class);
                Files.createDirectories(parent);
            }
            watchKey = parent.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        executorService = MoreExecutors.newDirectExecutorService();
    }

    public ListenableFuture<String> getMasterPassword() {
        if (futurePassword == null)
            futurePassword = executorService.submit(() -> {
                if (Files.exists(seedFile)) {
                    return readFile();
                } else {
                    final WatchKey take = watchService.take();
                    for (WatchEvent<?> watchEvent : take.pollEvents()) {
                        WatchEvent<Path> evP = (WatchEvent<Path>) watchEvent;
                        if (evP.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            evP.context(); //check if this was the file created
                            return readFile();
                        }
                    }
                    throw new IllegalStateException("failed to monitor " + config.encryptionSeed);
                }
            });
        return futurePassword;


    }

    public String readFile() {
        return FileUtil.readEncryptionKey(config.encryptionSeed);
    }

    /**
     * only call this after you are sure the password was loaded...
     *
     * @return if loaded, returns the password, else it throws an exceptiion.
     */
    public String getImmediatePassword() {
        final Future<String> future = getMasterPassword();
        if (future.isDone()) {
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("password was not loaded yet...");
        }
    }
}
