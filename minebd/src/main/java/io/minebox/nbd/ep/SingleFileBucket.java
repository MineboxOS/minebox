package io.minebox.nbd.ep;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import io.minebox.nbd.Encryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

class SingleFileBucket implements Bucket {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleFileBucket.class);
    private final FileChannel channel;
    private final long baseOffset;
    /**
     * highest valid offset, given minimum length of 1
     */
    private final long upperBound;
    private final long bucketNumber;
    private final long bucketSize;
    private final Path filePath;
    private volatile boolean fileWasZero;
    //right now we try to keep track of the empty ranges but dont use them anywhere. there is a big optimisation opportunity here to minimize the amount of
    private RandomAccessFile randomAccessFile;
    private volatile boolean needsFlush = false;
    private volatile boolean needsTimestampUpdate = false;

    private final Encryption encryption;

    SingleFileBucket(long bucketNumber, long bucketSize, Encryption encryption, File file) {
        this.bucketSize = bucketSize;
        this.bucketNumber = bucketNumber;
        this.encryption = encryption;
        baseOffset = bucketNumber * this.bucketSize;
        upperBound = baseOffset + this.bucketSize - 1;
        LOGGER.debug("starting to monitor bucket {} with file {}", bucketNumber, file.getAbsolutePath());
        try {
            randomAccessFile = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }
        try {
            filePath = file.toPath();
            final long existingFileSize = Files.size(filePath);
            this.fileWasZero = existingFileSize == 0;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        channel = randomAccessFile.getChannel();
    }

    public void close() throws IOException {
        if (channel.isOpen()) {
            flush();
            channel.close();
        } else {
            LOGGER.warn("closing bucket {} without an open channel.", bucketNumber);
        }
        randomAccessFile.close();
    }

    public long getBytes(ByteBuffer readInto, long offset, int length) throws IOException {
        final long offsetInThisBucket = offsetInThisBucket(offset);
        final long lengthInThisBucket = calcLengthInThisBucket(offsetInThisBucket, length);
        final int read;
        ByteBuffer encrypted = ByteBuffer.allocate(length);
        synchronized (this) {
            channel.position(offsetInThisBucket);
            read = channel.read(encrypted);
            if (read > 0) {
                encrypted.flip();
                encrypted.limit(read);
                final ByteBuffer decrypted = encryption.encrypt(offset, encrypted);
                readInto.put(decrypted);
            }
        }
        if (read != lengthInThisBucket) {
            final byte[] zeroes;
            if (read == -1) {
                zeroes = new byte[(int) lengthInThisBucket];
            } else {
                zeroes = new byte[(int) (lengthInThisBucket - read)];
            }
            LOGGER.debug("tried to read more bytes from this file than ever were written, replacing with {} zeroes", zeroes.length);
            readInto.put(zeroes);

        }
        return lengthInThisBucket;
    }

    @Override
    public long bucketIndex() {
        return bucketNumber;
    }

    @VisibleForTesting
    @Override
    public long calcLengthInThisBucket(long offsetInThisBucket, long length) {
        if (length < 1) {
            throw new UnsupportedOperationException("she said it's too small: " + length);
        } else if (offsetInThisBucket < 0) {
            throw new UnsupportedOperationException("unable to get offset " + offsetInThisBucket + " smaller than my base " + baseOffset);
        } else {
            final long consumableBytes = bucketSize - offsetInThisBucket;
            final long lenghtThisBucket = Math.min(consumableBytes, length);
            if (lenghtThisBucket < 0) {
                throw new UnsupportedOperationException("unable to get offset " + offsetInThisBucket + " length is negative: " + lenghtThisBucket);
            }
            return lenghtThisBucket;
        }
    }

    public void flush() {
        if (!needsFlush) {
            return;
        }
        needsFlush = false;
        LOGGER.info("flushing bucket {}", bucketNumber);
        try {
            synchronized (this) {
                if (channel.isOpen()) {
                    channel.force(needsTimestampUpdate);
                    needsTimestampUpdate = false;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("unable to flush file of bucket {}", bucketNumber);
        }
    }

    @Override
    public long putBytes(long offset, ByteBuffer message) throws IOException {
        return putBytesInternal(offset, message, true);
    }

    private long putBytesInternal(long offset, ByteBuffer message, boolean needsMetadataUpdate) throws IOException {
        if (needsMetadataUpdate) {
            needsTimestampUpdate = true;
        }
        if (stillAllZeroes(message)) {
            return message.remaining();
        }
        synchronized (this) {
            needsFlush = true;
            final long offsetInThisBucket = offsetInThisBucket(offset);
            channel.position(offsetInThisBucket);
            final ByteBuffer encrypted = encryption.encrypt(offset, message);
            return channel.write(encrypted);
        }
    }

    private boolean stillAllZeroes(ByteBuffer message) {
        if (fileWasZero) {
            final ByteBuffer checkForZeroes = message.duplicate();
            checkForZeroes.rewind();
            while (checkForZeroes.hasRemaining()) {
                final byte b = checkForZeroes.get();
                if (b != 0) {
                    fileWasZero = false;
                }
            }
            if (fileWasZero) {
                LOGGER.debug("saved some resources by not writing zeroes");
                return true;
            }
        }
        return false;
    }

    private long offsetInThisBucket(long offset) {
        return offset - baseOffset;
    }

    @Override
    public void trim(long offset, long length) throws IOException {
        needsFlush = true;
        final long offsetInThisBucket = offsetInThisBucket(offset);
        final long lengthInThisBucket = calcLengthInThisBucket(offsetInThisBucket, length); //should be always equal to length since it is normalized in MineboxEport
        final long fileSize = channel.size();
        if (fileSize == 0 || offsetInThisBucket >= fileSize) {
            //if the file is empty, there is nothing to trim
        } else if (lengthInThisBucket == this.bucketSize) {
            //if we are trimming the whole bucket we can truncate to 0
            synchronized (this) {
                channel.truncate(0);
                channel.force(true);
            }
        } else if (offsetInThisBucket == 0 && lengthInThisBucket >= fileSize) {
            //we are trimming the whole file, so we can truncate it.
            synchronized (this) {
                channel.truncate(0);
                channel.force(true);
            }
        } else if (offsetInThisBucket + lengthInThisBucket == this.bucketSize) {
            //truncating from index until end, we can shorten the file now
            synchronized (this) {
                channel.truncate(offsetInThisBucket);
                channel.force(false); //since we assume the un-truncated file was actually backed up, we don't care if this shortened file is not the one uploaded, since truncate is a "best effort" operations btrfs should tolerate those data being non-zero
            }
        } else {
            final int intLen = Ints.checkedCast(length); //buckets can not be bigger than 2GB right now, could be fixed
            final ByteBuffer bb = ByteBuffer.allocate(intLen);
            bb.put(new byte[intLen]);
            bb.flip();
            putBytesInternal(offset, bb, false); //sadly, this will encrypt zeroes. we need a workaround
        }
    }

    @Override
    public long getBaseOffset() {
        return baseOffset;
    }

    @Override
    public long getUpperBound() {
        return upperBound;
    }

}
