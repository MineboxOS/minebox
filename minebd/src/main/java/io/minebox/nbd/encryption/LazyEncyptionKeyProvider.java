package io.minebox.nbd.encryption;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(LazyEncyptionKeyProvider.class);
    private WatchKey watchKey;
    private Path seedFile;
    private WatchService watchService;
    private ListenableFuture<String> futurePassword;

    @Inject
    public LazyEncyptionKeyProvider(@Named("encryptionKeyPath") String encryptionKeyPath) {
        watchParentDirectory(encryptionKeyPath);
        ListeningExecutorService executorService = createNamedExecutorService();
        futurePassword = buildFuturePassword(executorService);
    }

    private ListenableFuture<String> buildFuturePassword(ListeningExecutorService executorService) {
        return executorService.submit(() -> {
            final String password1 = tryLoadingExistingFile();
            if (password1 != null) return password1;
            LOGGER.info("keyfile not found, watching for file creation...");
            return waitForFileCreation();
        });
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
            throw new IllegalStateException("password was not loaded yet... create one at " + seedFile.toAbsolutePath());
        }
    }

    private String waitForFileCreation() throws InterruptedException {
        while (true) {
            final WatchKey take = watchService.take();
            for (WatchEvent<?> watchEvent : take.pollEvents()) {
                WatchEvent<Path> evP = (WatchEvent<Path>) watchEvent;
                if (evP.kind() == StandardWatchEventKinds.ENTRY_CREATE || evP.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                    final Path createdFile = ((Path) take.watchable()).resolve(evP.context());
                    LOGGER.info("detected new key file: " + createdFile);
                    final String password = readCreatedFile(createdFile);
                    if (password != null) return password;
                } else {
                    LOGGER.warn("unexpected event " + watchEvent.kind());
                }
            }
            watchKey.reset();
        }
    }

    /**
     * read recently created/modified password file
     *
     * @param createdFile file can not be bigger than 4096 bytes, else its not atomic
     * @return contents of the file, if its at the expected path
     */
    private String readCreatedFile(Path createdFile) {
        if (!createdFile.equals(seedFile)) {
            LOGGER.warn("unexpected file created " + createdFile);
            return null;
        }
        return readExisingFile();
    }

    private String tryLoadingExistingFile() {
        if (!Files.exists(seedFile)) {
            return null;
        }
        return readExisingFile();
    }

    private String readExisingFile() {
        final String password = readFile();
        if (password.isEmpty()) {
            LOGGER.warn("empty password detected... please delete and create the file atomically..");
            return null;
        }
        return password;
    }

    private ListeningExecutorService createNamedExecutorService() {
        return MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(r -> new Thread(Thread.currentThread().getThreadGroup(), "encryptionKeyWatcher") {
            @Override
            public void run() {
                r.run();
            }
        }));
    }

    private void watchParentDirectory(@Named("encryptionKeyPath") String encryptionKeyPath) {
        try {
            seedFile = Paths.get(encryptionKeyPath);
            watchService = FileSystems.getDefault().newWatchService();
            final Path parent = seedFile.getParent();
            if (!Files.isDirectory(parent)) {
                Files.createDirectories(parent);
            }
            watchKey = parent.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ListenableFuture<String> getMasterPassword() {
        return futurePassword;
    }

    private String readFile() {
        return FileUtil.readEncryptionKey(seedFile);
    }
}
