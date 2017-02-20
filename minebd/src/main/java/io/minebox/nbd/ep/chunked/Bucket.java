package io.minebox.nbd.ep.chunked;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Bucket {
    private static final Logger logger = LoggerFactory.getLogger(Bucket.class);

    private final FileChannel channel;
    private final long baseOffset;
    private long upperBound;
    private RandomAccessFile randomAccessFile;
    private final String filename;
    final long bucketNumber;

    Bucket(long bucketNumber, String parentDir) {
        this.bucketNumber = bucketNumber;
        baseOffset = bucketNumber * MineboxExport.BUCKET_SIZE; //rounded down
        upperBound = baseOffset + MineboxExport.BUCKET_SIZE;
        final String leadingZeros = String.format("%0" + MineboxExport.FILENAME_DIGITS + "X", bucketNumber);
        filename = "minebox_v1_" + leadingZeros + ".dat";
        final File parentDirF = new File(parentDir);
        parentDirF.mkdirs();
        final File file = new File(parentDirF, filename);

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
            channel.force(true);
            channel.close();
            randomAccessFile.close();
        } catch (IOException e) {
            logger.warn("unable to flush and close file {}", filename);
        }
    }

    ByteBuffer getBytes(long offset, long length) throws IOException {
        checkRange(offset, length);
        ByteBuffer bb = ByteBuffer.allocate((int) length);
        final int read;
        synchronized (this) {
            channel.position(offsetInThisBucket(offset));
            read = channel.read(bb);
        }
        if (read != length) {
            ;
            final byte[] zeroes;
            if (read == -1) {
                zeroes = new byte[(int) length];
            } else {
                zeroes = new byte[(int) (length - read)];
            }
            logger.debug("tried to read more bytes from this file than ever were written, replacing with {} zeroes", zeroes.length);
            bb.put(zeroes);

        }
        bb.flip();
        return bb;
    }

    private void checkRange(long offset, long length) {
        if (offset < baseOffset) {
            throw new UnsupportedOperationException("unable to get offset " + offset + " smaller than my base " + baseOffset);
        } else {
            if (offset + length >= upperBound) {
                throw new UnsupportedOperationException("unable to get offset " + offset + " greater than my base " + upperBound);
            }
        }
    }

    void flush() {
        try {
            channel.force(true);
        } catch (IOException e) {
            logger.warn("unable to flush file {}", filename);
        }
    }

    void putBytes(long offset, ByteBuffer message) throws IOException {
        synchronized (this) {
            channel.position(offsetInThisBucket(offset));
            channel.write(message);
        }
    }

    private long offsetInThisBucket(long offset) {
        return offset - baseOffset;
    }
}
