/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.io.minebox;

import com.vanheusden.BlackHole.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;

public class SimpleNBD extends Thread {
    static AtomicLong flushDelay = new AtomicLong(0);
    static AtomicLong totalBytesWritten = new AtomicLong();
    static AtomicLong totalBytesRead = new AtomicLong();
    static AtomicLong totalIdleTime = new AtomicLong();
    static AtomicLong totalProcessingTime = new AtomicLong();
    static AtomicLong totalNCommands = new AtomicLong(), totalNRead = new AtomicLong(), totalNWrite = new AtomicLong();
    static AtomicLong nSessions = new AtomicLong();
    static AtomicLong totalSessionsLength = new AtomicLong();
    static AtomicLong totalReadTime = new AtomicLong();
    static AtomicLong totalWriteTime = new AtomicLong();
    final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final MineboxBackend storage;
    boolean assumeBarriers = false;
    long transactionSizeLimit = 512 * 1024 * 1024;

    public SimpleNBD(Socket socket) throws IOException {
        this.socket = socket;
        setNoDelay(this.socket);

        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        storage = new MineboxBackend();

    }

    private void setNoDelay(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    protected void initSession() throws IOException {
        Log.log(LogLevel.LOG_DEBUG, "Init session");

        Log.log(LogLevel.LOG_DEBUG, "\"password\"");
        byte passwordMsg[] = {'N', 'B', 'D', 'M', 'A', 'G', 'I', 'C'};
        putBytes(passwordMsg);

        Log.log(LogLevel.LOG_DEBUG, "\"magic\"");
        byte[] magicMsg = {0x00, 0x00, 0x42, 0x02, (byte) 0x81, (byte) 0x86, 0x12, 0x53};
        putBytes(magicMsg);

        long fileSize = 100 * 1024 * 1024;//100MB
        Log.log(LogLevel.LOG_DEBUG, "\"storage size\": " + fileSize);
        byte storageSizeMsg[] = {
                (byte) (fileSize >> 56),
                (byte) (fileSize >> 48),
                (byte) (fileSize >> 40),
                (byte) (fileSize >> 32),
                (byte) (fileSize >> 24),
                (byte) (fileSize >> 16),
                (byte) (fileSize >> 8),
                (byte) (fileSize)};
        putBytes(storageSizeMsg);

        Log.log(LogLevel.LOG_DEBUG, "\"flags\"");
        int flags = 1 + 4 + 8 + 32; // FLAGS, FLUSH, FUA, TRIM
        byte[] flagsBytes = Utils.intToByteArray(flags);
        putBytes(flagsBytes);

        Log.log(LogLevel.LOG_DEBUG, "\"padding\"");
        byte[] padMsg = new byte[124];
        putBytes(padMsg);
        outputStream.flush();
    }

    private void putBytes(byte[] msg) {
        try {
            outputStream.write(msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void sendAck(long handle, int errorCode) throws IOException {
        byte[] output = new byte[16];
        Utils.putU32(output, 0, 0x67446698);
        Utils.putU32(output, 4, errorCode);
        Utils.putU64(output, 8, handle);
        putBytes(output);
    }

    protected void msgDiscard(long handle, long offset, long len, boolean forceToDisk) throws IOException, InterruptedException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
        long orgOffset = offset;
        long orgLen = len;
        int blockSize = 4096;


        byte[] empty = new byte[blockSize];

        while (len > 0) {
            long blockId = offset / blockSize;
            int blockOffset = (int) (offset % blockSize);
            int cur = (int) Math.min(blockSize - blockOffset, len);
            byte[] buffer = null;

            if (blockOffset == 0 && cur == blockSize)
                storage.unmapSector(blockId);
            else {
                assert cur != blockSize;
                Log.log(LogLevel.LOG_DEBUG, "REQUEST SMALLER THAN BLOCKSIZE! cur: " + cur + ", blocksize: " + blockSize);
                byte[] smallEmpty = new byte[cur];
                storage.putBlock(blockId, smallEmpty, blockOffset, forceToDisk);
            }

            len -= cur;
            offset += cur;
        }

        if (!assumeBarriers)
            storage.commitTransaction();

        sendAck(handle, 0);
        flushSocket();
    }

    private void flushSocket() {
        try {
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void msgWrite(long handle, long offset, long len, boolean forceToDisk) throws IOException, InterruptedException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
        long orgOffset = offset;
        long orgLen = len;
        int blockSize = storage.getBlockSize();

        if (!assumeBarriers)
            storage.startTransaction();

        while (len > 0) {
            long blockId = offset / blockSize;
            int blockOffset = (int) (offset % blockSize);
            int cur = (int) Math.min(blockSize - blockOffset, len);
            byte[] buffer = null;

            if (blockOffset == 0 && cur == blockSize) {
                buffer = new byte[blockSize];
                getBytes(buffer);
                storage.putBlock(blockId, buffer, forceToDisk);
            } else {
                assert cur != blockSize;
                Log.log(LogLevel.LOG_DEBUG, "REQUEST SMALLER THAN BLOCKSIZE! cur: " + cur + ", blocksize: " + blockSize);
                buffer = new byte[cur];
                getBytes(buffer);
                storage.putBlock(blockId, buffer, blockOffset, forceToDisk);
            }

            len -= cur;
            offset += cur;
        }

        if (!assumeBarriers)
            storage.commitTransaction();

        sendAck(handle, 0);
        flushSocket();
    }

    private void getBytes(byte[] buffer) {
        int totalRead = 0;
        int len = buffer.length;
        int left = buffer.length;
        while (left > 0) {
            int nRead = 0;
            try {
                nRead = inputStream.read(buffer, 0, left);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (nRead == -1) {
                throw new RuntimeException("read from socket error, expected: " + len + ", got: " + totalRead);
            }
            if (Thread.interrupted()) {
                throw new RuntimeException("read from socket interrupted");
            }
            left -= nRead;
            totalRead += nRead;
        }

        assert totalRead == len;

    }

    protected void msgRead(long handle, long offset, long len) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
        long orgOffset = offset;
        long orgLen = len;
        int blockSize = storage.getBlockSize();

        // Log.log(LogLevel.LOG_DEBUG, "read " + offset + " " + len);

        sendAck(handle, 0);

        while (len > 0) {
            byte[] buffer = storage.readBlock(offset / blockSize);
            int blockOffset = (int) (offset % blockSize);
            int cur = (int) Math.min(blockSize - blockOffset, len);

            Log.log(LogLevel.LOG_DEBUG, "put " + cur);
            if (cur < blockSize) {
                //Log.log(LogLevel.LOG_INFO, "REQUEST SMALLER THAN BLOCKSIZE! cur: " + cur + ", blocksize: " + blockSize);
                putBytes(buffer, blockOffset, cur);
            } else
                putBytes(buffer);

            len -= cur;
            offset += cur;
        }

        flushSocket();
    }

    private void putBytes(byte[] buffer, int blockOffset, int cur) {
        try {
            outputStream.write(buffer, blockOffset, cur);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void msgFlush(long handle) throws IOException, VHException, SQLException {
        if (assumeBarriers)
            storage.commitTransaction();

        sendAck(handle, 0);

        if (assumeBarriers)
            storage.startTransaction();
    }

    public void close() throws IOException {
        closeSocket();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        long sessionStart = System.currentTimeMillis();
        nSessions.addAndGet(1);

        try {
            boolean usesNewCommandFlush = false, usesNewCommandDiscard = false, usesNewCommandFlushWrite = false;

            initSession();
            Log.log(LogLevel.LOG_DEBUG, " client thread running");

            if (assumeBarriers)
                storage.startTransaction();

            long transactionSize = 0;

            for (; ; ) {
                long startIdle = System.currentTimeMillis();

                byte[] request = new byte[28];
                getBytes(request);
                long startProcessing = System.currentTimeMillis();
                totalIdleTime.addAndGet(startProcessing - startIdle);

                int magic = Utils.byteArrayToInt(request, 0);
                if (magic != 0x25609513)
                    throw new VHException("Invalid magic " + magic);
                int type = Utils.byteArrayToInt(request, 4);
                long handle = Utils.byteArrayToLong(request, 8);
                long offset = Utils.byteArrayToLong(request, 16);
                long len = Utils.byteArrayToInt(request, 24) & 0xffffffffl;

                totalNCommands.addAndGet(1);

                int flags = type >> 16;
                type &= 0xffff;

                // System.out.println("" + offset + " " + len);
                if (type == 1) {    // WRITE
                    transactionSize += len;

                    long startWrite = System.currentTimeMillis();

//                    snapMan.queue(lun, offset, len);

                    if (offset < 0 || len < 0 || offset > getLunSize() || offset + len > getLunSize() || offset + len < offset)
                        sendAck(handle, 22);    // EINVAL
                    else {
                        msgWrite(handle, getMapOffset(offset), len, (flags & 1) == 1 ? true : false);

                        if (!usesNewCommandFlushWrite && (flags & 1) == 1) {
                            Log.log(LogLevel.LOG_INFO, "Uses new NBD flag (FUA)");
                            usesNewCommandFlushWrite = true;
                        }

                        queue(offset, len);
                    }

                    totalWriteTime.addAndGet(System.currentTimeMillis() - startWrite);
                    totalBytesWritten.addAndGet(len);
                    totalNWrite.addAndGet(1);
                } else if (type == 0) {    // READ
                    long startRead = System.currentTimeMillis();

                    if (offset < 0 || len < 0 || offset > getLunSize() || offset + len > getLunSize() || offset + len < offset)
                        sendAck(handle, 22);    // EINVAL
                    else
                        msgRead(handle, getMapOffset(offset), len);

                    totalReadTime.addAndGet(System.currentTimeMillis() - startRead);
                    totalBytesRead.addAndGet(len);
                    totalNRead.addAndGet(1);
                } else if (type == 2) {    // DISCONNECT
                    Log.log(LogLevel.LOG_INFO, "End of session");
                    if (BlackHole.isTestcaseMode()) {
                        Log.log(LogLevel.LOG_INFO, "Terminating program due to testcase mode");
                        if (assumeBarriers)
                            storage.commitTransaction();
                        System.exit(0);
                    }
                    break;
                } else if (type == 3) {    // FLUSH
                    msgFlush(handle);

                    transactionSize = 0;

                    if (!usesNewCommandFlush) {
                        Log.log(LogLevel.LOG_INFO, "Uses new NBD commands (FLUSH)");
                        usesNewCommandFlush = true;
                    }
                } else if (type == 4) {    // DISCARD (TRIM)
                    transactionSize += len;

                    if (offset < 0 || len < 0 || offset > getLunSize() || offset + len > getLunSize() || offset + len < offset) {
                        Log.log(LogLevel.LOG_WARN, "Discard out of range " + offset + "/" + len);

                        sendAck(handle, 22);    // EINVAL
                    } else {
                        if (!usesNewCommandDiscard) {
                            Log.log(LogLevel.LOG_INFO, "Uses new NBD commands (DISCARD)");

                            if ((flags & 1) == 1)
                                Log.log(LogLevel.LOG_INFO, "Uses new NBD flag (FUA)");

                            usesNewCommandDiscard = true;
                        }

                        msgDiscard(handle, getMapOffset(offset), len, (flags & 1) == 1 ? true : false);
                    }
                } else {
                    throw new Exception("Unknown message type: " + type);
                }

                if (transactionSize >= transactionSizeLimit && assumeBarriers) {
                    storage.commitTransaction();
                    storage.startTransaction();

                    transactionSize = 0;
                }

                totalProcessingTime.addAndGet(System.currentTimeMillis() - startProcessing);
            }

            if (assumeBarriers)
                storage.commitTransaction();
        } catch (SocketException se) {
            Log.log(LogLevel.LOG_WARN, "protocolNBD run() socket exception " + se);
        } catch (IOException ie) {
            Log.log(LogLevel.LOG_WARN, "protocolNBD run() IO exception " + ie);
        } catch (VHException vhe) {
            Log.log(LogLevel.LOG_WARN, "protocolNBD run() text exception " + vhe);
        } catch (Exception e) { // OK
            Log.log(LogLevel.LOG_WARN, "protocolNBD run() other exception " + e);
            Log.showException(e);
        } catch (AssertionError ae) {
            Log.showAssertionError(ae);
        } finally {
            // do not commit the barrier-transaction
            // if this program crashes before or during a barrier,
            // then the client "thinks" the data was not written

            try {
                closeSocket();
            } catch (Exception e) {
                Log.showException(e);
            }
        }

        totalSessionsLength.addAndGet(System.currentTimeMillis() - sessionStart);

        Log.log(LogLevel.LOG_DEBUG, " client thread stopped");
    }

    private void queue(long offset, long len) {
        //do nothing
//        mirrorMan.queue(lun, offset, len);
    }

    private long getMapOffset(long offset) {
        return 0;
//        return sm.getMapOffset(lun, offset);
    }

    private double getLunSize() {
        return 0;
//        return getLunSize();
    }
}
