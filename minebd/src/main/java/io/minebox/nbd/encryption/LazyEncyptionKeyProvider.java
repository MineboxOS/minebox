package io.minebox.nbd.encryption;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.minebox.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by andreas on 12.05.17.
 */
@Singleton
public class LazyEncyptionKeyProvider implements EncyptionKeyProvider {

    private final String encryptionKeyPath;
    private WatchKey watchKey;
    private Path seedFile;
    private WatchService watchService;
    private ListenableFuture<String> futurePassword;

    private static final Logger LOGGER = LoggerFactory.getLogger(LazyEncyptionKeyProvider.class);

    @Inject
    public LazyEncyptionKeyProvider(@Named("encryptionKeyPath") String encryptionKeyPath) {
        this.encryptionKeyPath = encryptionKeyPath;
        try {
            seedFile = Paths.get(encryptionKeyPath);
            watchService = FileSystems.getDefault().newWatchService();
            final Path parent = seedFile.getParent();
            if (!Files.isDirectory(parent)) {
                Files.createDirectories(parent);
            }
            watchKey = parent.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(Thread.currentThread().getThreadGroup(), "encryptionKeyWatcher") {
                    @Override
                    public void run() {
                        r.run();
                    }
                };
            }
        }));
        futurePassword = executorService.submit(() -> {
            if (Files.exists(seedFile)) {
                final String password = readFile();
                if (!password.isEmpty()) {
                    return password;
                } else {
                    LOGGER.warn("empty password detected... please delete and create the file atomically..");
                }
            }
            LOGGER.info("keyfile not found, watching for file creation...");
            while (true) {
                final WatchKey take = watchService.take();
                for (WatchEvent<?> watchEvent : take.pollEvents()) {
                    WatchEvent<Path> evP = (WatchEvent<Path>) watchEvent;
                    if (evP.kind() == StandardWatchEventKinds.ENTRY_CREATE || evP.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        final Path createdFile = ((Path) take.watchable()).resolve(evP.context());
                        if (createdFile.equals(seedFile)) {
                            LOGGER.info("detected new key file creation: " + createdFile);
                            final String password = readFile();
                            if (!password.isEmpty()) {
                                return password;
                            } else {
                                LOGGER.warn("empty password detected... please delete and create the file atomically..");
                            }
                        } else {
                            LOGGER.warn("unexpected file created " + createdFile);
                        }
                    } else {
                        LOGGER.warn("unexpected event " + watchEvent.kind());
                    }
                }
                watchKey.reset();
            }
        });
    }

    @Override
    public ListenableFuture<String> getMasterPassword() {
        return futurePassword;
    }

    private String readFile() {
        return FileUtil.readEncryptionKey(seedFile);
    }

    /**
     * only call this after you are sure the password was loaded...
     *
     * @return if loaded, returns the password, else it throws an exceptiion.
     */
    @Override
    public String getImmediatePassword() {
        final Future<String> future = getMasterPassword();
        if (future.isDone()) {
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalStateException("password was not loaded yet...");
        }
    }
}
