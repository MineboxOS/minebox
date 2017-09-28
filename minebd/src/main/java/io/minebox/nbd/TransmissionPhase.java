package io.minebox.nbd;

import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import io.minebox.nbd.ep.ExportProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TransmissionPhase extends ByteToMessageDecoder {

    private long maxUnflushedBytes;
    private final ExecutorService executor;

    private enum State {TM_RECEIVE_CMD, TM_RECEIVE_CMD_DATA}

    private static final Logger LOGGER = LoggerFactory.getLogger(TransmissionPhase.class);
    private State state = State.TM_RECEIVE_CMD;

    private final ExportProvider exportProvider;
    private final AtomicInteger numOperations = new AtomicInteger(0);
    private final AtomicInteger pendingOperations = new AtomicInteger(0);

    private static class Error {
        public final static int EIO = 5;
    }

    public TransmissionPhase(long maxUnflushedBytes, ExportProvider exportProvider) {
        super();
        this.maxUnflushedBytes = maxUnflushedBytes;
        this.exportProvider = exportProvider;
        executor = new BlockingExecutor(10, 20);
    }

    private short cmdFlags;
    private short cmdType;
    private long cmdHandle;
    private long cmdOffset;
    private long cmdLength;
    private AtomicLong unflushedBytes = new AtomicLong(0);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        for (; ; ) {
            final int i = numOperations.incrementAndGet();
            if (i != 1) {
                LOGGER.warn("parallel action {}", i);
            }
            try {
                switch (state) {
                    case TM_RECEIVE_CMD:
                        if (!hasMin(in, 28))
                            return;

                        readOperationParameters(ctx, in);
                        state = State.TM_RECEIVE_CMD_DATA;
                        break;

                    //FIXME: this will buffer maybe a lot of bytes?!
                    case TM_RECEIVE_CMD_DATA:
                        if (cmdType == Protocol.NBD_CMD_WRITE && !hasMin(in, cmdLength))
                            return;

                        processOperation(ctx, in);
                        state = State.TM_RECEIVE_CMD;
                        break;
                }
            } finally {
                numOperations.decrementAndGet();
            }
        }
    }

    private static boolean hasMin(ByteBuf in, long wanted) {
        return in.readableBytes() >= wanted;
    }


    private void processOperation(ChannelHandlerContext ctx, ByteBuf in) throws IOException {
        pendingOperations.incrementAndGet();
        switch (cmdType) {
            case Protocol.NBD_CMD_READ: {
                final long cmdOffset = this.cmdOffset;
                final long cmdLength = this.cmdLength;
                final long cmdHandle = this.cmdHandle;
//                LOGGER.debug("reading from {} length {} handle {}", cmdOffset, cmdLength, cmdHandle);
                Runnable operation = () -> {
                    ByteBuf data = null;
                    int err = 0;
                    try {
                        //FIXME: use FUA/sync flag correctly
                        ByteBuffer bb = exportProvider.read(cmdOffset, Ints.checkedCast(cmdLength));
                        data = Unpooled.wrappedBuffer(bb);
                        final int actuallyRead = data.writerIndex() - data.readerIndex();
                        if (actuallyRead != cmdLength) {

                            LOGGER.error("responding to from {} length {} handle {}", cmdOffset, actuallyRead, cmdHandle);
                            final String msg = "i messed up and tried to return the wrong about of read data.. " +
                                    "from " + cmdOffset +
                                    " length " + actuallyRead +
                                    " requested " + cmdLength +
                                    " handle " + cmdHandle;
                            throw new IllegalStateException(msg);
                        }
                    } catch (Exception e) {
                        LOGGER.error("error during read", e);
                        err = Error.EIO;
                    } finally {
                        sendTransmissionSimpleReply(ctx, err, cmdHandle, data);
                    }
                };
                executor.execute(operation);
                break;
            }
            case Protocol.NBD_CMD_WRITE: {
                final long cmdOffset = this.cmdOffset;
                final long cmdLength = this.cmdLength;
                final long cmdHandle = this.cmdHandle;
                LOGGER.debug("writing to {} length {}", cmdOffset, cmdLength);
                final long sum = unflushedBytes.addAndGet(cmdLength);
                if (sum > maxUnflushedBytes) { //tune this number
                    LOGGER.debug("Rohr voll, ZWISCHENSPÜLUNG!");
                    final byte[] bytes = "3".getBytes(Charsets.UTF_8);
                    Files.write(Paths.get("/proc/sys/vm/drop_caches"), bytes);
                    exportProvider.flush(); //this hopefully flushes all and blocks until it is fully done
                    unflushedBytes.set(0);
                    unflushedBytes.addAndGet(cmdLength); //i just flushed, but this write counts already for the next
                }

                final ByteBuf buf;
                try {
                    buf = in.readBytes(Ints.checkedCast(cmdLength));
                } catch (Exception e) {
                    LOGGER.error("error during preparation of write", e);
                    sendTransmissionSimpleReply(ctx, Error.EIO, cmdHandle, null);
                    break;
                }
                Runnable operation = () -> {
                    int err = 0;
                    try {
                        //FIXME: use FUA/sync flag correctly
                        exportProvider.write(cmdOffset, buf.nioBuffer(), false);
                    } catch (Exception e) {
                        LOGGER.error("error during write", e);
                        err = Error.EIO;
                    } finally {
                        sendTransmissionSimpleReply(ctx, err, cmdHandle, null);
                        buf.release();
                    }
                };
                executor.execute(operation);
                break;
            }
            case Protocol.NBD_CMD_DISC: {
                LOGGER.debug("got command disc " + Protocol.NBD_CMD_DISC);
                ctx.channel().close(); //
                break;
            }
            case Protocol.NBD_CMD_FLUSH: {
                final long cmdOffset = this.cmdOffset;
                final long cmdLength = this.cmdLength;
                //offset + length must be zero
                final long cmdHandle = this.cmdHandle;

                LOGGER.debug("got flush..");
            /* we must drain all NBD_CMD_WRITE and NBD_WRITE_TRIM from the queue
             * before processing NBD_CMD_FLUSH
			 */
                int err = 0;
                try {
                    unflushedBytes.set(0);
                    exportProvider.flush();
                } catch (Exception e) {
                    LOGGER.error("error during flush", e);
                    err = Error.EIO;
                } finally {
                    sendTransmissionSimpleReply(ctx, err, cmdHandle, null);
                }
                break;
            }
            case Protocol.NBD_CMD_TRIM: {
                final long cmdOffset = this.cmdOffset;
                final long cmdLength = this.cmdLength;
                final long cmdHandle = this.cmdHandle;
                LOGGER.debug("trimming from {} length {}", cmdOffset, cmdLength);

                int err = 0;
                try {
                    exportProvider.trim(cmdOffset, cmdLength);
                } catch (Exception e) {
                    LOGGER.error("error during trim", e);
                    err = Error.EIO;
                } finally {
                    sendTransmissionSimpleReply(ctx, err, cmdHandle, null);
                }
                break;
            }
            default:
                sendTransmissionSimpleReply(ctx, Protocol.NBD_REP_ERR_INVALID, cmdHandle, null);
        }
    }

    private void readOperationParameters(ChannelHandlerContext ctx, ByteBuf message) throws IOException {
        if (message.readInt() != Protocol.NBD_REQUEST_MAGIC) {
            throw new IllegalArgumentException("Invalid request magic!");
        }

        cmdFlags = message.readShort();
        cmdType = message.readShort();
        cmdHandle = message.readLong();
        cmdOffset = message.readLong();
        cmdLength = message.readUnsignedInt(); //needs to be treated as long
    }

    private void sendTransmissionSimpleReply(ChannelHandlerContext ctx, int error, long handle, ByteBuf data) {
        synchronized (this) {
            ByteBuf bbr = ctx.alloc().buffer(16);
            bbr.writeInt(Protocol.REPLY_MAGIC);
            bbr.writeInt(error); // zero for okay
            bbr.writeLong(handle);
            ctx.write(bbr);
            if (data != null) {
                ctx.write(data);
            }
        }
        ctx.flush();
        logPendingOperations();
    }

    private volatile boolean loggedHighPending = false;
    private volatile long lastLog;

    private void logPendingOperations() {
        final int pendingOperations = this.pendingOperations.decrementAndGet();
        if (pendingOperations == 0 && loggedHighPending) {
            LOGGER.debug("pending operations back at 0");
            loggedHighPending = false;
        } else {
            final long now = System.currentTimeMillis();
            if (pendingOperations > 0 && (now - 1000) > lastLog) {
                lastLog = now;
                loggedHighPending = true;
                LOGGER.debug("pending operations: {}", pendingOperations);
            }
        }
    }
}
