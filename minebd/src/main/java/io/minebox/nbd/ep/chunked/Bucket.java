package io.minebox.nbd.ep.chunked;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Bucket {
    private static final Logger logger = LoggerFactory.getLogger(Bucket.class);

    private final FileChannel channel;
    private final long baseOffset;
    private final long size;
    /**
     * highest valid offset, given minimum length of 1
     */
    private final long upperBound;
    private RandomAccessFile randomAccessFile;
    private final String filename;
    private final long bucketNumber;

    Bucket(long bucketNumber, String parentDir, long size) {
        this.bucketNumber = bucketNumber;
        baseOffset = bucketNumber * size;
        this.size = size;
        upperBound = baseOffset + size - 1;
        filename = "minebox_v1_" + bucketNumber + ".dat";
        final File parentDirF = new File(parentDir);
        parentDirF.mkdirs();
        final File file = new File(parentDirF, filename);
        logger.debug("starting to monitor bucket {} with file {}", bucketNumber, file.getAbsolutePath());
        ensureFileExists(file);
        try {
            randomAccessFile = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }
        channel = randomAccessFile.getChannel();
    }

    private void ensureFileExists(File file) {
        if (!file.exists()) {
            final boolean created;
            try {
                created = file.createNewFile();
            } catch (IOException e1) {
                throw new IllegalStateException("unable to create file");
            }
            if (!created) {
                throw new IllegalStateException("file already existed");
            }
        }
    }

    void close() {
        try {
            if (channel.isOpen()) {
                channel.force(true);
                channel.close();
            } else {
                logger.warn("closing bucket {} without an open channel.", bucketNumber);
            }
            randomAccessFile.close();
        } catch (IOException e) {
            logger.warn("unable to flush and close file " + filename, e);
        }
    }

    long getBytes(ByteBuffer readInto, long offset, long length) throws IOException {
        final long offsetInThisBucket = offsetInThisBucket(offset);
        final long lengthInThisBucket = calcLengthInThisBucket(offsetInThisBucket, length);
        final int read;
        synchronized (this) {
            channel.position(offsetInThisBucket);
            read = channel.read(readInto);
        }
        if (read != lengthInThisBucket) {
            final byte[] zeroes;
            if (read == -1) {
                zeroes = new byte[(int) lengthInThisBucket];
            } else {
                zeroes = new byte[(int) (lengthInThisBucket - read)];
            }
            logger.debug("tried to read more bytes from this file than ever were written, replacing with {} zeroes", zeroes.length);
            readInto.put(zeroes);

        }
        return lengthInThisBucket;
    }

    @VisibleForTesting
    long calcLengthInThisBucket(long offsetInThisBucket, long length) {
        if (length < 1) {
            throw new UnsupportedOperationException("she said it's too small: " + length);
        } else if (offsetInThisBucket < 0) {
            throw new UnsupportedOperationException("unable to get offset " + offsetInThisBucket + " smaller than my base " + baseOffset);
        } else {
            final long consumableBytes = size - offsetInThisBucket;
            final long lenghtThisBucket = Math.min(consumableBytes, length);
            if (lenghtThisBucket < 0) {
                throw new UnsupportedOperationException("unable to get offset " + offsetInThisBucket + " length is negative: " + lenghtThisBucket);
            }
            return lenghtThisBucket;
        }
    }

    void flush() {
        try {
            //todo make sure this triggers after potentially different pending writes have their lock
            synchronized (this) {
                if (channel.isOpen()) {
                    channel.force(true);
                }
            }
        } catch (IOException e) {
            logger.warn("unable to flush file {}", filename);
        }
    }

    public long putBytes(long offset, ByteBuffer message) throws IOException {
        synchronized (this) {
            final long offsetInThisBucket = offsetInThisBucket(offset);
            channel.position(offsetInThisBucket);
            return channel.write(message);
        }
    }

    private long offsetInThisBucket(long offset) {
        return offset - baseOffset;
    }

    public void trim(long offset, long length) throws IOException {
        final long offsetInThisBucket = offsetInThisBucket(offset);
        final long lengthInThisBucket = calcLengthInThisBucket(offsetInThisBucket, length);
        if (lengthInThisBucket == size) {
            channel.truncate(0);
            channel.force(true);
        } else {
            final ByteBuffer bb = ByteBuffer.allocate((int) length);
            bb.put(new byte[(int) length]);
            bb.flip();
            putBytes(offset, bb);
        }
    }

    public long getBaseOffset() {
        return baseOffset;
    }

    public long getUpperBound() {
        return upperBound;
    }

}
