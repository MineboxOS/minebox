package io.minebox.nbd.ep;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface ExportProvider {

    long open(CharSequence exportName) throws IOException;

    ByteBuffer read(long offset, int length) throws IOException;

    void write(long offset, ByteBuffer message, boolean sync) throws IOException;

    void flush() throws IOException;

    void trim(long offset, int length) throws IOException;

    default boolean supportsClientFlags(int clientFlags) {
        return true; //todo find out what those actually do
    }

    void close() throws IOException;
}
