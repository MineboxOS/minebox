package io.minebox.nbd.ep;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by andreas on 24.04.17.
 */
public interface Bucket {
    long putBytes(long offset, ByteBuffer message) throws IOException;

    void trim(long offset, long length) throws IOException;

    long getBaseOffset();

    long getUpperBound();

    void close() throws IOException;

    void flush() throws IOException;

    long getBytes(ByteBuffer writeInto, long offsetForThisBucket, int length) throws IOException;
}
