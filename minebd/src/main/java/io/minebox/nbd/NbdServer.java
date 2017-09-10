package io.minebox.nbd;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import io.minebox.nbd.ep.ExportProvider;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.minebox.nbd.NbdServer.State.*;
import static io.netty.util.NetUtil.*;


@Singleton
public class NbdServer implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(NbdServer.class);
    private final int port;
    private final SystemdUtil systemdUtil;
    private final ExportProvider exportProvider;
    private final EncyptionKeyProvider encyptionKeyProvider;
    private EventLoopGroup eventLoopGroup;
    private volatile State state = IDLE;

    @VisibleForTesting
    @Inject
    public NbdServer(@Named("nbdPort") Integer nbdPort, SystemdUtil systemdUtil, ExportProvider exportProvider, EncyptionKeyProvider encyptionKeyProvider) {
        this.port = nbdPort;
        this.systemdUtil = systemdUtil;
        this.exportProvider = exportProvider;
        this.encyptionKeyProvider = encyptionKeyProvider;
    }

    @Override
    public void stop() {
        if (state == SHUTTINGDOWN || state == SHUTDOWN) {
            LOGGER.error("we have already attempted to shut down to {}, not doing it twice..", state);
            return;
        }
        if (state != STARTED) {
            LOGGER.info("we were not started completely, unable to shut down cleanly..", state);
            return;
        }
        state = SHUTTINGDOWN;
        LOGGER.info("shutdown detected..");
        systemdUtil.sendStopping();
        try {
            exportProvider.close();
            LOGGER.info("shutting down eventLoops");
            try {
                eventLoopGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                LOGGER.info("error shutting down eventLoops");
            }
            LOGGER.info("we appear to have shut down gracefully..");
        } catch (IOException e) {
            LOGGER.error("unable to flush and close ", e);
            systemdUtil.sendError(2);
        } finally {
            state = SHUTDOWN;
            LOGGER.info("shutdown finally complete..");
        }
    }

    @VisibleForTesting
    @Override
    public void start() {
        state = STARTING;
        //we want to delay the initialisation until the master password is ready
        LOGGER.info("starting up NBD service , waiting for encryption key to be present until we expose the port...");
        encyptionKeyProvider.onLoadKey(() -> {
            if (state != STARTING) {
                throw new IllegalStateException("i expected to be starting");
            }
            state = KEY_DETECTED;
            eventLoopGroup = new NioEventLoopGroup();
            ServerBootstrap bootstrap = new ServerBootstrap();
            final boolean hasIpv6 = LOCALHOST.equals(LOCALHOST6);
            final ServerBootstrap serverBootstrap = bootstrap.group(eventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(LOCALHOST, port));
            if (hasIpv6) {
                //also add v4
                serverBootstrap.localAddress(LOCALHOST4, port);
            }
            serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new HandshakePhase(exportProvider));
                }
            });
            initializeBlockZero();
            final ChannelFuture bind = bootstrap.bind();
            ChannelFuture f;
            try {
                f = bind.sync();
            } catch (InterruptedException e) {
                throw new RuntimeException("unable to start without being interrupted...", e);
            }
            LOGGER.info("started up minebd on port {} ", port);
            final Channel channel = f.channel();
            systemdUtil.sendNotify(); //tell systemd we are ready
            state = STARTED;
        });
    }

    private void initializeBlockZero() {
        LOGGER.info("trying to obtain block 0");
        //we try to read a single byte from the beginning of the "disk".
        // this should in turn trigger download of the file minebox_v1_0.dat since it looks like a user.
        try {
            exportProvider.read(0, 1);
        } catch (IOException e) {
            throw new RuntimeException("problem getting block 0", e);
        }
        LOGGER.info("successfully read block 0");
    }

    enum State {
        IDLE, STARTING, KEY_DETECTED, STARTED, SHUTTINGDOWN, SHUTDOWN;
    }
}
