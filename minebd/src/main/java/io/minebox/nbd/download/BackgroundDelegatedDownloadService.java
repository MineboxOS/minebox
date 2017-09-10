package io.minebox.nbd.download;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.minebox.sia.SiaFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class BackgroundDelegatedDownloadService implements DownloadService {

    public static final ExecutorService BACKGROUND_SCHEDULER = Executors.newFixedThreadPool(2); //for scheduling background downloads
    public static final ExecutorService DOWNLOADER = Executors.newCachedThreadPool(); //infinite "foreground downloader slots.
    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundDelegatedDownloadService.class);
    private final DownloadService delegate;
    private final Deque<File> allRecoverableFiles;
    private final TreeSet<UserFileRequest> userRequestedFiles; //todo limit this size to same as background_downloader

    private final LoadingCache<File, RecoveryStatus> requestCache = CacheBuilder.newBuilder().build(new CacheLoader<File, RecoveryStatus>() {
        @Override
        public RecoveryStatus load(File maxPriorityFile) throws Exception {
            return getSubmitter(maxPriorityFile, DOWNLOADER).call();
        }
    });

    private static class UserFileRequest implements Comparable<UserFileRequest> {
        Instant requestedAt;
        File file;
        int bucketNumber;

        public UserFileRequest(Instant requestedAt, File file) {
            this.requestedAt = requestedAt;
            this.file = file;
        }

        @Override
        public int compareTo(UserFileRequest o) {
            return this.requestedAt.compareTo(o.requestedAt);
        }
    }

    public BackgroundDelegatedDownloadService(DownloadService delegate, Collection<File> recoverableFiles) {
        this.delegate = delegate;
        LOGGER.info("found {} files to potentially download in background", recoverableFiles.size());
        this.allRecoverableFiles = new ArrayDeque<>(recoverableFiles);
        userRequestedFiles = new TreeSet<>();
        new Thread("downloadInserter") {
            @Override
            public void run() {
                while (!allRecoverableFiles.isEmpty()) { //todo find a way to shut this down gracefully if needed
                    submitBackgroundDownload();
                }
                LOGGER.info("all download tasks are finished now, quitting..");
            }
        }.start();
    }

    private void sortByDistanceToRequested() {
        List<File> list = new ArrayList<>(this.allRecoverableFiles);
        //todo find a way to do this more elegantly, this is O(n²) and lots of linear overhead.. bad.
        list.sort((o1, o2) -> {
            final int o1BucketNumber = SiaFileUtil.fileToNumber(o1);
            final int o2BucketNumber = SiaFileUtil.fileToNumber(o2);
            int mino1 = Integer.MAX_VALUE;
            int mino2 = Integer.MAX_VALUE;
            for (UserFileRequest userRequestedFile : userRequestedFiles) {
                final int o1Distance = userRequestedFile.bucketNumber - o1BucketNumber;
                final int o2Distance = userRequestedFile.bucketNumber - o2BucketNumber;
                if (o1Distance >= 0 && o1Distance < mino1) {
                    mino1 = o1Distance;
                }
                if (o2Distance >= 0 && o2Distance < mino1) {
                    mino2 = o2Distance;
                }
            }
            final int distanceDifference = mino1 - mino2;
            if (distanceDifference == 0) {
                //todo check access times to prioritize
            }
            return distanceDifference;

        });
        LOGGER.info("determining top prio files");
        LOGGER.debug("download queue: {}", list);
        LOGGER.debug("userRequestedFiles {}", userRequestedFiles);
        allRecoverableFiles.clear();
        allRecoverableFiles.addAll(list);
    }

    private void submitBackgroundDownload() {
        try {
            CountDownLatch started = new CountDownLatch(1);
            BACKGROUND_SCHEDULER.submit(() -> {
                sortByDistanceToRequested();
                final File maxPriorityFile = allRecoverableFiles.removeFirst();
                LOGGER.info("putting {} in the queue", maxPriorityFile.getName());
                started.countDown();
                requestCache.getUnchecked(maxPriorityFile);
            });
            LOGGER.debug("waiting for download...");
            started.await(); //block until download has been triggered, not until download is complete
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Callable<RecoveryStatus> getSubmitter(File maxPriorityFile, ExecutorService service) {
        return () -> service.submit(() -> {
            LOGGER.debug("delegating download of {}", maxPriorityFile.getName());
            return this.delegate.downloadIfPossible(maxPriorityFile);
        }).get();
    }

    @Override
    public RecoveryStatus downloadIfPossible(File file) {
        LOGGER.info("directly requesting {} in the immediate downloader", file.getName());
        userRequestedFiles.add(new UserFileRequest(Instant.now(), file));
        return requestCache.getUnchecked(file);
    }

    @Override
    public boolean hasMetadata() {
        return delegate.hasMetadata();
    }

    @Override
    public boolean connectedMetadata() {
        return delegate.connectedMetadata();
    }

    @Override
    public double completedPercent(File parentDir) {
        return delegate.completedPercent(parentDir);
    }
}
