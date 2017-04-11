package io.minebox.nbd.ep.chunked;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import io.minebox.nbd.ep.ExportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;

public class MineboxExport implements ExportProvider {

    final private Config config;

    public static class Config {
        public int maxOpenFiles = MAX_OPEN_FILES;
        public String parentDir = "minedbDat";
        public long reportedSize = 1 * GIGABYTE;
    }

    private static final Logger logger = LoggerFactory.getLogger(MineboxExport.class);

    //base-2 style size
    public static final int KILO = 1024;
    public static final long MEGABYTE = KILO * KILO;
    public static final long GIGABYTE = MEGABYTE * KILO;
    public static final long TERABYTE = GIGABYTE * KILO;
    public static final long PETABYTE = TERABYTE * KILO;
    public static final long MAX_SUPPORTED_SIZE = PETABYTE;

    public static final long FILENAME_DIGITS = (long) (Math.log(MAX_SUPPORTED_SIZE) / Math.log(16));
    public static final long BUCKET_SIZE = MEGABYTE * 40; //according to taek42 , 40 MB is the bucket size for contracts, so we use the same for efficientcy.
    public static final int MAX_OPEN_FILES = 20;


    private final LoadingCache<Long, Bucket> files;

    public MineboxExport(Config config) {
        this.config = config;

        files = CacheBuilder.newBuilder()
                .maximumSize(config.maxOpenFiles)
                .removalListener((RemovalListener<Long, Bucket>) notification -> {
                    logger.debug("no longer monitoring bucket {}", notification.getKey());
                    notification.getValue().close();
                })
                .build(new CacheLoader<Long, Bucket>() {
                    @Override
                    public Bucket load(Long key) throws Exception {
                        logger.debug("starting to monitor bucket {}", key);
                        return new Bucket(key, config.parentDir);
                    }
                });
    }

    @Override
    public String create(CharSequence exportName, long size) throws IOException {
        return null;
    }

    @Override
    public long open(CharSequence exportName) throws IOException {
        logger.debug("opening {}", exportName);
        return config.reportedSize;
    }

    @Override
    public ByteBuffer read(long offset, long length, boolean sync) throws IOException {
        final Bucket bucket = getBucket(offset);
        logger.debug("reading {} bytes from offset {} from bucket {}", length, offset, bucket.bucketNumber);
        return bucket.getBytes(offset, length);
    }

    private long bucketFromOffset(long offset) {
        return offset / BUCKET_SIZE;
    }

    @Override
    public void write(long offset, ByteBuffer message, boolean sync) throws IOException {
        final Bucket bucket = getBucket(offset);
        logger.debug("writing {} bytes from offset {} to bucket {}", message.remaining(), offset, bucket.bucketNumber);
        bucket.putBytes(offset, message);
        if (sync) {
            bucket.flush();
        }
    }

    private Bucket getBucket(long offset) throws IOException {
        return getBucketFromIndex(bucketFromOffset(offset));
    }

    private Bucket getBucketFromIndex(long bucketNumber) throws IOException {
        Bucket bucket;
        try {
            bucket = files.get(bucketNumber);
        } catch (ExecutionException e) {
            throw new IOException("unable to get bucket # " + bucketNumber, e);
        }
        return bucket;
    }

    @Override
    public void flush() throws IOException {
        logger.info("flushing all open buckets");
        files.asMap().values().forEach(Bucket::flush);
    }

    @Override
    public void trim(long offset, long length) throws IOException {
        logger.debug("trimming {} bytes from offset {} to bucket", length, offset);
        for (Integer bucketNumber : getBuckets(offset, length)) {
            final Bucket bucket = getBucketFromIndex(bucketNumber);
            final long start = Math.max(offset, bucket.getBaseOffset());
            final long lengthForBucket = Math.min(bucket.getUpperBound() + 1, offset + length) - start;
            bucket.trim(start, lengthForBucket);
        }
    }

    private List<Integer> getBuckets(long offset, long length) {
        final long startIndex = bucketFromOffset(offset);
        final long endIndex = bucketFromOffset(offset + length - 1);
        final List<Integer> ret = IntStream.range((int) startIndex, (int) endIndex + 1)
                .boxed()
                .collect(toList());
        logger.debug("i see {} buckets at offset {} length {}", ret.size(), offset, length);
        return ret;
    }

    @Override
    public void close() throws IOException {
        files.asMap().values().forEach(Bucket::close);
    }

}
