package io.minebox.nbd.ep.chunked;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import io.minebox.nbd.ep.ExportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineboxExport implements ExportProvider {

    private static final Logger logger = LoggerFactory.getLogger(MineboxExport.class);

    //base-2 style size
    public static final int KILO = 1024;
    public static final long MEGABYTE = KILO * KILO;
    public static final long GIGABYTE = MEGABYTE * KILO;
    public static final long TERABYTE = GIGABYTE * KILO;
    public static final long PETABYTE = TERABYTE * KILO;
    public static final long MAX_SUPPORTED_SIZE = PETABYTE;

    public static final long FILENAME_DIGITS = (long) Math.log(MAX_SUPPORTED_SIZE);
    public static final long BUCKET_SIZE = MEGABYTE * 10;
    public static final CacheLoader<Long, Bucket> CACHE_LOADER = new CacheLoader<Long, Bucket>() {
        @Override
        public Bucket load(Long key) throws Exception {
            logger.debug("starting to monitor bucket {}", key);
            return new Bucket(key);
        }
    };
    public static final int MAX_OPEN_FILES = 20;


    private final LoadingCache<Long, Bucket> files = CacheBuilder.newBuilder()
            .maximumSize(MAX_OPEN_FILES)
            .removalListener((RemovalListener<Long, Bucket>) notification -> {
                logger.debug("no longer monitoring bucket {}", notification.getKey());
                notification.getValue().close();
            })
            .build(CACHE_LOADER);


    @Override
    public String create(CharSequence exportName, long size) throws IOException {
        return null;
    }

    @Override
    public long open(CharSequence exportName) throws IOException {
        logger.debug("opening {}", exportName);
        return 1 * GIGABYTE; //todo read the total amount of potentially available space
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
        final Bucket bucket;
        final long bucketNumber = bucketFromOffset(offset);
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
    public void trim() throws IOException {

    }

    @Override
    public void close() throws IOException {

    }

}
