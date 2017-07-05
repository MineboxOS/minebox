package io.minebox.nbd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.primitives.Ints;
import io.minebox.nbd.ep.ExportProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransmissionPhase extends ByteToMessageDecoder {

    private enum State {TM_RECEIVE_CMD, TM_RECEIVE_CMD_DATA}

    private static final Logger LOGGER = LoggerFactory.getLogger(TransmissionPhase.class);
    private State state = State.TM_RECEIVE_CMD;

    private final ExportProvider exportProvider;
    private final AtomicInteger numOperations = new AtomicInteger(0);
    private final AtomicInteger pendingOperations = new AtomicInteger(0);

    private static class Error {
        public final static int EIO = 5;
    }

    public TransmissionPhase(ExportProvider exportProvider) {
        super();
        this.exportProvider = exportProvider;
    }

    private short cmdFlags;
    private short cmdType;
    private long cmdHandle;
    private long cmdOffset;
    private long cmdLength;

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
                Runnable operation = () -> {
                    ByteBuf data = null;
                    int err = 0;
                    try {
                        //FIXME: use FUA/sync flag correctly
                        ByteBuffer bb = exportProvider.read(cmdOffset, Ints.checkedCast(cmdLength));
                        data = Unpooled.wrappedBuffer(bb);
                    } catch (Exception e) {
                        LOGGER.error("error during read", e);
                        err = Error.EIO;
                    } finally {
                        sendTransmissionSimpleReply(ctx, err, cmdHandle, data);
                    }
                };
                GlobalEventExecutor.INSTANCE.execute(operation);
                break;
            }
            case Protocol.NBD_CMD_WRITE: {
                final long cmdOffset = this.cmdOffset;
                final long cmdLength = this.cmdLength;
                final long cmdHandle = this.cmdHandle;

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
                GlobalEventExecutor.INSTANCE.execute(operation);
                break;
            }
            case Protocol.NBD_CMD_DISC: {
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
                    exportProvider.flush();
                } catch (Exception e) {
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

                int err = 0;
                try {
                    exportProvider.trim(cmdOffset, cmdLength);
                } catch (Exception e) {
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

    private synchronized void sendTransmissionSimpleReply(ChannelHandlerContext ctx, int error, long handle, ByteBuf data) {
        ByteBuf bbr = ctx.alloc().buffer(16);
        bbr.writeInt(Protocol.REPLY_MAGIC);
        bbr.writeInt(error); // zero for okay
        bbr.writeLong(handle);

        ctx.write(bbr);
        if (data != null) {
            ctx.write(data);
        }
        ctx.flush();
        final int pendingOperations = this.pendingOperations.decrementAndGet();
        if (pendingOperations != 0 || error != 0) {
            LOGGER.debug("pending operations: {}, error: {}", pendingOperations, error);
        }
    }
}
