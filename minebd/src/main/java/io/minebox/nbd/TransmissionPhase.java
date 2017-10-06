package io.minebox.nbd;

import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import com.sun.management.OperatingSystemMXBean;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.ep.ExportProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TransmissionPhase extends ByteToMessageDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransmissionPhase.class);

    private final long minFreeSystemMem;
    private final ExecutorService executor;
    private final OperatingSystemMXBean osBean;
    private final ExportProvider exportProvider;
    private final AtomicInteger numOperations = new AtomicInteger(0);
    private final AtomicInteger pendingOperations = new AtomicInteger(0);
    private final AtomicLong unflushedBytes = new AtomicLong(0);
    private final AtomicLong checkReadCacheBytes = new AtomicLong(0);
    private final long maxUnflushedBytes;
    private final AtomicReference<OperationParameters> operationParameters = new AtomicReference<>(OperationParameters.RECEIVE_STATE);
    private volatile boolean loggedHighPending = false;
    private volatile long lastLog;

    public TransmissionPhase(MinebdConfig config, ExportProvider exportProvider) {
        super();
        this.maxUnflushedBytes = config.maxUnflushed.toBytes();
        this.minFreeSystemMem = config.minFreeSystemMem.toBytes();
        this.exportProvider = exportProvider;
        executor = new BlockingExecutor(10, 20);
//        cleanupRunning = true;
        osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

/*        cleanupThread = new Thread("cleanup") {
            @Override
            public void run() {
                while (cleanupRunning) { //todo check if needed
                    checkFreeMem();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        //nothing
                    }

                }

            }
        }*/
        ;
//        cleanupThread.start();
    }

    private static boolean hasMin(ByteBuf in, long wanted) {
        final int readableBytes = in.readableBytes();
        return readableBytes >= wanted;
    }

    private void checkFreeMem() {
        final long freeMem = osBean.getFreePhysicalMemorySize();
        if (freeMem < minFreeSystemMem) {
            LOGGER.info("free mem {} too small, dropping caches", freeMem);
            final byte[] bytes = "3".getBytes(Charsets.UTF_8);
            try {
                Files.write(Paths.get("/proc/sys/vm/drop_caches"), bytes);
            } catch (AccessDeniedException e) {
                LOGGER.debug("was worth a try ;)");
            } catch (IOException e) {
                LOGGER.error("unable to free memory", e);
            }
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        LOGGER.debug("decode again...");
        for (; ; ) {
            final int i = numOperations.incrementAndGet();
            if (i != 1) {
                LOGGER.warn("parallel action {}", i);
            }
            try {
                switch (operationParameters.get().state) {
                    case TM_RECEIVE_CMD:
                        LOGGER.debug("getting new cmd");
                        if (!hasMin(in, 28)) {//28 = magic 4, flags 2, type 2, handle 8, offset 8, length 4
                            //not enough data to read yet, try again later?
                            return;
                        }

                        final int magic = in.readInt();
                        if (magic != Protocol.NBD_REQUEST_MAGIC) {
                            throw new IllegalArgumentException("Invalid request magic! 0x" + BigInteger.valueOf(magic).toString(16));
                        }
                        operationParameters.set(OperationParameters.readFromMessage(in));
                        break;

                    //FIXME: this will buffer maybe a lot of bytes?!
                    case TM_RECEIVE_CMD_DATA:
                        final OperationParameters op = this.operationParameters.getAndSet(OperationParameters.LIMBO_STATE);
                        if (op == null) {
                            throw new IllegalStateException("unexpected null state");
                        }
                        LOGGER.debug("preparing {}, handle {}", op.cmdType, op.cmdHandle);
                        ByteBuf buf = null;
                        if (op.cmdType == Protocol.NBD_CMD_WRITE) {
                            if (!hasMin(in, op.cmdLength)) {
                                operationParameters.set(op); //restore from limbo to "try again when buffer is filled"
                                return;
                            } else {
                                //enough data in buffer to get the full write operation, lets read it into a separate buffer so we can parse the next param
                                try {
                                    buf = in.readBytes(Ints.checkedCast(op.cmdLength));
                                } catch (Exception e) {
                                    LOGGER.error("error during preparation of write", e);
                                    sendTransmissionSimpleReply(ctx, Error.EIO, op.cmdHandle, null);
                                    break;
                                }
                            }
                        }

                        processOperation(ctx, buf, op);
                        LOGGER.debug("put {}, handle {} in queue", op.cmdType, op.cmdHandle);
                        operationParameters.set(OperationParameters.RECEIVE_STATE);
                        break;
                    case LIMBO:
                        LOGGER.debug("not sure what to do, im in limbo");
                        return;

                }
            } finally {
                numOperations.decrementAndGet();
            }
        }
    }

    private void processOperation(ChannelHandlerContext ctx, ByteBuf dataToWrite, OperationParameters opParams)
            throws IOException {
        pendingOperations.incrementAndGet();
        switch (opParams.cmdType) {
            case Protocol.NBD_CMD_READ: {
                freeIfNeeded(opParams);
                Runnable operation = createReadOperation(ctx, opParams);
                executor.execute(operation);
                break;
            }
            case Protocol.NBD_CMD_WRITE: {
                LOGGER.debug("writing to {} length {}", opParams.cmdOffset, opParams.cmdLength);
                freeAndFlushIfNeeded(opParams);
                Runnable operation = createWriteOperation(ctx, opParams, dataToWrite);
                executor.execute(operation);
                break;
            }
            case Protocol.NBD_CMD_DISC: {
                LOGGER.debug("got command disc " + Protocol.NBD_CMD_DISC);
                ctx.channel().close(); //
                break;
            }
            case Protocol.NBD_CMD_FLUSH: {
                LOGGER.debug("got flush..");
                checkFreeMem();
                Runnable flushOperation = createFlushOperation(ctx, opParams.cmdHandle);
                executor.execute(flushOperation);
                break;
            }
            case Protocol.NBD_CMD_TRIM: {
                LOGGER.debug("trimming from {} length {}", opParams.cmdOffset, opParams.cmdLength);

                Runnable trimOperation = () -> {
                    int err = 0;
                    try {
                        exportProvider.trim(opParams.cmdOffset, opParams.cmdLength);
                    } catch (Exception e) {
                        LOGGER.error("error during trim", e);
                        err = Error.EIO;
                    } finally {
                        sendTransmissionSimpleReply(ctx, err, opParams.cmdHandle, null);
                    }
                };
                executor.execute(trimOperation);
                break;
            }
            default:
                sendTransmissionSimpleReply(ctx, Protocol.NBD_REP_ERR_INVALID, opParams.cmdHandle, null);
        }
    }

    private void freeIfNeeded(OperationParameters operationParameters) {
        final long l = checkReadCacheBytes.addAndGet(operationParameters.cmdLength);
        if (l > maxUnflushedBytes) {
            checkFreeMem();
            checkReadCacheBytes.set(0);
        }
    }

    void freeAndFlushIfNeeded(OperationParameters operationParameters) throws IOException {
        final long sum = unflushedBytes.addAndGet(operationParameters.cmdLength);
        if (sum > maxUnflushedBytes) { //tune this number
            LOGGER.debug("Rohr voll, ZWISCHENSPÜLUNG!");
            checkFreeMem();
            exportProvider.flush(); //this hopefully flushes all and blocks until it is fully done
            unflushedBytes.set(0);
            unflushedBytes.addAndGet(operationParameters.cmdLength); //i just flushed, but this write counts already for the next
        }
    }

    private Runnable createWriteOperation(ChannelHandlerContext ctx, OperationParameters operationParameters, ByteBuf buf) {
        return () -> {
            int err = 0;
            try {
                //FIXME: use FUA/sync flag correctly
                exportProvider.write(operationParameters.cmdOffset, buf.nioBuffer(), false);
            } catch (Exception e) {
                LOGGER.error("error during write", e);
                err = Error.EIO;
            } finally {
                sendTransmissionSimpleReply(ctx, err, operationParameters.cmdHandle, null);
                buf.release();
            }
        };
    }

    private Runnable createFlushOperation(ChannelHandlerContext ctx, long cmdHandle) {
    /* todo  we must drain all NBD_CMD_WRITE and NBD_WRITE_TRIM from the queue
     * before processing NBD_CMD_FLUSH
     */
        return () -> {
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
        };
    }

    private Runnable createReadOperation(ChannelHandlerContext ctx, OperationParameters operationParameters) {
        return () -> {
            ByteBuf data = null;
            int err = 0;
            try {
                //FIXME: use FUA/sync flag correctly
                ByteBuffer bb = exportProvider.read(operationParameters.cmdOffset, Ints.checkedCast(operationParameters.cmdLength));
                data = Unpooled.wrappedBuffer(bb);
                checkReadLength(operationParameters, data);
            } catch (Exception e) {
                LOGGER.error("error during read", e);
                err = Error.EIO;
            } finally {
                sendTransmissionSimpleReply(ctx, err, operationParameters.cmdHandle, data);
            }
        };
    }

    private void checkReadLength(OperationParameters operationParameters, ByteBuf data) {
        final int actuallyRead = data.writerIndex() - data.readerIndex();
        if (actuallyRead != operationParameters.cmdLength) {

            LOGGER.error("responding to from {} length {} handle {}", operationParameters.cmdOffset, actuallyRead, operationParameters.cmdHandle);
            final String msg = "i messed up and tried to return the wrong about of read data.. " +
                    "from " + operationParameters.cmdOffset +
                    " length " + actuallyRead +
                    " requested " + operationParameters.cmdLength +
                    " handle " + operationParameters.cmdHandle;
            throw new IllegalStateException(msg);
        }
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

    private enum State {TM_RECEIVE_CMD, TM_RECEIVE_CMD_DATA, LIMBO}

    private static class Error {
        public final static int EIO = 5;
    }

    private static class OperationParameters {

        private static final OperationParameters LIMBO_STATE = new OperationParameters(State.LIMBO);
        private static final OperationParameters RECEIVE_STATE = new OperationParameters(State.TM_RECEIVE_CMD);

        final short cmdFlags;
        final short cmdType;
        final long cmdHandle;
        final long cmdOffset;
        final long cmdLength;
        final State state;

        private OperationParameters(ByteBuf message) {
            cmdFlags = message.readShort();
            cmdType = message.readShort();
            cmdHandle = message.readLong();
            cmdOffset = message.readLong();
            cmdLength = message.readUnsignedInt();
            state = State.TM_RECEIVE_CMD_DATA;
        }

        private OperationParameters(State state) {
            this.state = state;
            cmdFlags = -1;
            cmdType = -1;
            cmdHandle = -1;
            cmdOffset = -1;
            cmdLength = -1;
        }

        public static OperationParameters readFromMessage(ByteBuf message) {
            return new OperationParameters(message);
        }
    }
}
