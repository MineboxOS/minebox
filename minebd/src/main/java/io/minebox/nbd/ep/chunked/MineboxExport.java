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
import com.google.common.primitives.Ints;
import io.minebox.nbd.Constants;
import io.minebox.nbd.Encryption;
import io.minebox.nbd.ep.ExportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;

public class MineboxExport implements ExportProvider {

    public static final long FILENAME_DIGITS = log16(Constants.MAX_SUPPORTED_SIZE);
    private final long bucketSize;//according to taek42 , 40 MB is the bucket size for contracts, so we use the same for efficientcy.
    public static final int MAX_OPEN_FILES = 20;
    private static final Logger logger = LoggerFactory.getLogger(MineboxExport.class);
    final private MinebdConfig config;
    private final Encryption encryption;
    private final LoadingCache<Integer, Bucket> files;

    public MineboxExport(MinebdConfig config, Encryption encryption) {
        this.config = config;
        files = createFilesCache(config);
        this.encryption = encryption;
        this.bucketSize = config.bucketSize;
    }

    public long getBucketSize() {
        return bucketSize;
    }

    private LoadingCache<Integer, Bucket> createFilesCache(final MinebdConfig config) {
        return CacheBuilder.newBuilder()
                .maximumSize(config.maxOpenFiles)
                .removalListener((RemovalListener<Integer, Bucket>) notification -> {
                    logger.debug("no longer monitoring bucket {}", notification.getKey());
                    notification.getValue().close();
                })
                .build(new CacheLoader<Integer, Bucket>() {
                    @Override
                    public Bucket load(Integer key) throws Exception {
                        final Bucket ret = new Bucket(key, config.parentDir, getBucketSize());
                        logger.debug("starting to monitor bucket {} with file {}", key);
                        return ret;
                    }
                });
    }

    private static long log16(long value) {
        return (long) (Math.log(value) / Math.log(16));
    }

    @Override
    public long open(CharSequence exportName) throws IOException {
        logger.debug("opening {}", exportName);
        return config.reportedSize;
    }

    //todo all lengths should be ints not longs
    @Override
    public ByteBuffer read(final long offset, final int length) throws IOException {
        final ByteBuffer origMessage = ByteBuffer.allocate(length);
        for (Integer bucketIndex : getBuckets(offset, length)) { //eventually make parallel
            Bucket bucket = getBucketFromIndex(bucketIndex);
            final long absoluteOffsetForThisBucket = Math.max(offset, bucket.getBaseOffset());
            final int lengthForBucket = Ints.checkedCast(Math.min(bucket.getUpperBound() + 1, offset + length) - absoluteOffsetForThisBucket);
            final int dataOffset = Ints.checkedCast(Math.max(0, bucket.getBaseOffset() - offset));
            final ByteBuffer pseudoCopy = bufferForBucket(origMessage, lengthForBucket, dataOffset);

            bucket.getBytes(pseudoCopy, absoluteOffsetForThisBucket, lengthForBucket);
        }
        return origMessage;
    }

    private int bucketFromOffset(long offset) {
        return Ints.checkedCast(offset / getBucketSize());
    }

    @Override
    public void write(long offset, ByteBuffer origMessage, boolean sync) throws IOException {
//        logger.debug("writing {} bytes to offset {}", origMessage.remaining(), offset);

        final int length = origMessage.remaining();

        for (Integer bucketIndex : getBuckets(offset, length)) { //eventually make parallel
            Bucket bucket = getBucketFromIndex(bucketIndex);
            writeDataToBucket(bucket, offset, length, origMessage);
        }
    }

    private void writeDataToBucket(Bucket bucket, long offset, int length, ByteBuffer origMessage) throws IOException {
        final long start = Math.max(offset, bucket.getBaseOffset());
        final int lengthForBucket = Ints.checkedCast(Math.min(bucket.getUpperBound() + 1, offset + length) - start);
        final int dataOffset = Ints.checkedCast(Math.max(0, bucket.getBaseOffset() - offset));
        final ByteBuffer pseudoCopy = bufferForBucket(origMessage, lengthForBucket, dataOffset);
        final long writtenBytes = bucket.putBytes(start, pseudoCopy);
//        logger.debug("wrote {} bytes to bucket {}", writtenBytes, bucket.bucketNumber);
    }

    private ByteBuffer bufferForBucket(ByteBuffer origMessage, int lengthForBucket, int dataOffset) {
        final ByteBuffer pseudoCopy = origMessage.slice();
        pseudoCopy.position(dataOffset);
        pseudoCopy.limit(dataOffset + Ints.checkedCast(lengthForBucket));
        return pseudoCopy;
    }

    private Bucket getBucketFromIndex(int bucketNumber) throws IOException {
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
    public void trim(long offset, int length) throws IOException {
        logger.debug("trimming {} bytes from offset {} to bucket", length, offset);
        for (Integer bucketNumber : getBuckets(offset, length)) {
            final Bucket bucket = getBucketFromIndex(bucketNumber);
            final long start = Math.max(offset, bucket.getBaseOffset());
            final long lengthForBucket = Math.min(bucket.getUpperBound() + 1, offset + length) - start;
            bucket.trim(start, lengthForBucket);
        }
    }

    private List<Integer> getBuckets(long offset, int length) {
        final IntStream intStream = getBucketsStream(offset, length);
        final List<Integer> ret = intStream
                .boxed()
                .collect(toList());
        if (ret.size() != 1) {
            logger.debug("i see {} buckets at offset {} length {}", ret.size(), offset, length);
        }
        return ret;
    }

    private IntStream getBucketsStream(long offset, int length) {
        final long startIndex = bucketFromOffset(offset);
        final long endIndex = bucketFromOffset(offset + length - 1);
        return IntStream.range((int) startIndex, (int) endIndex + 1);
    }

    @Override
    public void close() throws IOException {
        files.asMap()
                .values()
                .forEach(Bucket::close);
    }
}
