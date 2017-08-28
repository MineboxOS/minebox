package io.minebox.nbd.ep;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.Encryption;
import io.minebox.nbd.download.DownloadService;
import io.minebox.nbd.SerialNumberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BucketFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(BucketImpl.class);
    private final SerialNumberService serialNumberService;
    private final String parentDir;
    private final long size;
    private final Encryption encryption;
    private final Provider<DownloadService> downloadService;
    private File parentFolder;

    @Inject
    public BucketFactory(SerialNumberService serialNumberService, MinebdConfig config, Encryption encryption, Provider<DownloadService> downloadService) {
        this.serialNumberService = serialNumberService;
        this.parentDir = config.parentDir;
        this.size = config.bucketSize.toBytes();
        this.encryption = encryption;
        this.downloadService = downloadService;
    }

    private File createParentFolder(SerialNumberService serialNumberService) {
        if (this.parentFolder == null) {
            parentFolder = new File(parentDir, serialNumberService.getPublicIdentifier());
            parentFolder.mkdirs();
        }
        return parentFolder;

    }

    public Bucket create(Integer bucketIndex) {
        return new BucketImpl(bucketIndex);
    }

    class BucketImpl implements Bucket {

        private final FileChannel channel;
        private final long baseOffset;
        /**
         * highest valid offset, given minimum length of 1
         */
        private final long upperBound;
        private final String filename;
        private final long bucketNumber;
        //right now we try to keep track of the empty ranges but dont use them anywhere. there is a big optimisation opportunity here to minimize the amount of
        private RandomAccessFile randomAccessFile;
        private volatile boolean needsFlush = false;

        BucketImpl(long bucketNumber) {
            this.bucketNumber = bucketNumber;
            baseOffset = bucketNumber * size;
            upperBound = baseOffset + size - 1;
            filename = "minebox_v1_" + bucketNumber + ".dat";
            final File parentFolder = createParentFolder(serialNumberService);
            final File file = new File(parentFolder, filename);
            LOGGER.debug("starting to monitor bucket {} with file {}", bucketNumber, file.getAbsolutePath());
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
                DownloadService.RecoveryStatus wasDownloaded = downloadService.get().downloadIfPossible(file);
                if (DownloadService.RecoveryStatus.ERROR.equals(wasDownloaded)) {
                    throw new RuntimeException("i was unable to obtain the expected file");
                } else if (DownloadService.RecoveryStatus.NO_FILE.equals(wasDownloaded)) {
                    createEmptyFile(file);
                } else {
                    LOGGER.info("bucket {} is now happy that we got the file {}", bucketNumber, file.getName());
                }
            }
        }

        private void createEmptyFile(File file) {
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

        public void close() throws IOException {
            if (channel.isOpen()) {
                channel.force(true);
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

        public void flush() {
            if (!needsFlush) {
                return;
            }
            needsFlush = false;
            LOGGER.info("flushing bucket {}", bucketNumber);
            try {
                //todo make sure this triggers after potentially different pending writes have their lock
                synchronized (this) {
                    if (channel.isOpen()) {
                        channel.force(true);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("unable to flush file {}", filename);
            }
        }

        @Override
        public long putBytes(long offset, ByteBuffer message) throws IOException {
            synchronized (this) {
                needsFlush = true;
                final long offsetInThisBucket = offsetInThisBucket(offset);
                channel.position(offsetInThisBucket);
                final ByteBuffer encrypted = encryption.encrypt(offset, message);
                return channel.write(encrypted);
            }
        }

        private long offsetInThisBucket(long offset) {
            return offset - baseOffset;
        }

        @Override
        public void trim(long offset, long length) throws IOException {
            needsFlush = true;
            final long offsetInThisBucket = offsetInThisBucket(offset);
            final long lengthInThisBucket = calcLengthInThisBucket(offsetInThisBucket, length); //should be always equal to length since it is normalized in MineboxEport
            if (lengthInThisBucket == size) {
                synchronized (this) {
                    channel.truncate(0);
                    channel.force(true);
                }
            } else {
                final int intLen = Ints.checkedCast(length); //buckets can not be bigger than 2GB right now, could be fixed
                final ByteBuffer bb = ByteBuffer.allocate(intLen);
                bb.put(new byte[intLen]);
                bb.flip();
                putBytes(offset, bb); //sadly, this will encrypt zeroes. we need a workaround
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
}
