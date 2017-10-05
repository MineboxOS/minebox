package io.minebox.nbd;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import io.minebox.config.MinebdConfig;
import io.minebox.nbd.ep.ExportProvider;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandshakePhase extends ByteToMessageDecoder {

    private final MinebdConfig config;
    private final ExportProvider exportProvider;

    public HandshakePhase(MinebdConfig config, ExportProvider exportProvider) {
        this.config = config;
        this.exportProvider = exportProvider;
    }

    private enum State {HS_CLIENT_FLAGS, HS_OPTION_HAGGLING, HS_OPTION_DATA}


    private static final Logger LOGGER = LoggerFactory.getLogger(HandshakePhase.class);

    private State state;

    private short handshakeFlags;
    private int clientFlags;

    private int currentOption;
    private long currentOptionLen;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        initHandshake(ctx);
        state = State.HS_CLIENT_FLAGS;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        out:
        for (; ; ) {
            switch (state) {
                case HS_CLIENT_FLAGS:
                    if (!hasMin(in, 4))
                        return;
                    receiveHandshakeClientFlag(ctx, in);
                    state = State.HS_OPTION_HAGGLING;
                    break;

                case HS_OPTION_HAGGLING:
                    if (!hasMin(in, 16))
                        return;

                    receiveHandshakeOption(ctx, in);
                    break;

                case HS_OPTION_DATA:
                    if (!hasMin(in, (int) currentOptionLen))
                        return;

                    String o = processOption(ctx, in);
                    if (o != null) {
                        LOGGER.info("got option: {}", o);
                    }

                    state = State.HS_OPTION_HAGGLING;
                    break;
            }
        }
    }

    private String processOption(ChannelHandlerContext ctx, ByteBuf in) throws IOException {
        switch (currentOption) {
            case Protocol.NBD_OPT_EXPORT_NAME:
                CharSequence exportName = in.readCharSequence((int) currentOptionLen, Charset.forName("UTF-8"));

                if (!exportProvider.supportsClientFlags(clientFlags)) {
                    LOGGER.error("client flags {} not supported", clientFlags);
                    ctx.channel().close();
                    break;
                }

                long exportSize = 0;
                if ((exportSize = exportProvider.open(exportName)) < 0) {
                    // ep us unwilling to export this name
                    ctx.channel().close();
                    break;
                }

			/* build response */
                ByteBuf resp = ctx.alloc().buffer(256);
                resp.writeLong(exportSize);
                short transmissionFlags =
                        Protocol.NBD_FLAG_HAS_FLAGS
                                | Protocol.NBD_FLAG_SEND_FLUSH
                                | Protocol.NBD_FLAG_SEND_TRIM;
                resp.writeShort(transmissionFlags);

                if ((clientFlags & Protocol.NBD_FLAG_NO_ZEROES) == 0) {
                    resp.writeZero(124);
                }
                ctx.writeAndFlush(resp);

                //FIXME: transfer any remaining bytes into the transmission phase!
            /* The NBD protocol has two phases: the handshake (HS_) and the transmission (TM_) */
                // Handshake complete, switch to transmission phase
                ctx.pipeline().addLast("transmission", new TransmissionPhase(config, exportProvider));
                ctx.pipeline().remove(this);
                return exportName.toString();

            default:
                sendHandshakeOptionHagglingReply(ctx, currentOption, Protocol.NBD_REP_ERR_UNSUP, null);
        }
        return null;
    }

    private static boolean hasMin(ByteBuf in, int wanted) {
        return in.readableBytes() >= wanted;
    }

    private void initHandshake(ChannelHandlerContext ctx) throws IOException {
        /* initiate handshake */
        ByteBuf bb = ctx.alloc().buffer(20);
        bb.writeLong(Protocol.NBDMAGIC);
        bb.writeLong(Protocol.IHAVEOPT);

        // "handshake flags"
        handshakeFlags = Protocol.NBD_FLAG_FIXED_NEWSTYLE & Protocol.NBD_FLAG_NO_ZEROES;
        bb.writeShort(handshakeFlags);
        ctx.channel().writeAndFlush(bb);
    }

    private void receiveHandshakeClientFlag(ChannelHandlerContext ctx, ByteBuf message) throws IOException {
        clientFlags = message.readInt();
        LOGGER.info("got client flags {}", clientFlags);
//
        /*if ((clientFlags & Protocol.NBD_FLAG_FIXED_NEWSTYLE) == 0)
            ctx.channel().close();*/
    }

    private void receiveHandshakeOption(ChannelHandlerContext ctx, ByteBuf message) throws IOException {

        final long ihaveopt = message.readLong();
        if (ihaveopt != Protocol.IHAVEOPT)
            throw new IllegalArgumentException("Invalid negotiation magic != 'IHAVEOPT'");

        int option = message.readInt();
        long optionLen = message.readUnsignedInt();

        currentOption = option;
        currentOptionLen = optionLen;
        LOGGER.debug("currentOption {} currentOptionLen {}", currentOption, currentOptionLen);
        state = State.HS_OPTION_DATA;
    }

    private void sendHandshakeOptionHagglingReply(ChannelHandlerContext ctx, int option, int error, ByteBuf data) throws IOException {
        ByteBuf optionReply = ctx.alloc().buffer(18);
        optionReply.writeLong(Protocol.HANDSHAKE_REPLY_MAGIC);
        optionReply.writeInt(option);
        optionReply.writeInt(error); // aka. reply type
        int len = 0;
        if (data != null)
            len = data.readableBytes();
        optionReply.writeInt(len);
        ctx.write(optionReply);
        if (data != null)
            ctx.write(data);
    }
}
