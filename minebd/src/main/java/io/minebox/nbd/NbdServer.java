package io.minebox.nbd;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import io.dropwizard.lifecycle.Managed;
import io.minebox.config.MinebdConfig;
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


public class NbdServer implements Managed {

    private final int port;
    private final SystemdUtil systemdUtil;
    private static final Logger logger = LoggerFactory.getLogger(NbdServer.class);
    private final MinebdConfig config;
    private final ExportProvider exportProvider;
    private EventLoopGroup eventLoopGroup;
    private ChannelFuture mainChannelFuture;
    private State state = IDLE;

    enum State {
        IDLE, STARTING, STARTED, SHUTTINGDOWN, SHUTDOWN;
    }

    @VisibleForTesting
    @Inject
    public NbdServer(SystemdUtil systemdUtil, MinebdConfig config, ExportProvider exportProvider) {
        this.port = config.nbdPort;
        this.systemdUtil = systemdUtil;
        this.config = config;
        this.exportProvider = exportProvider;
    }

    public static void main(String... args) {
        final Injector injector = Guice.createInjector(new NbdModule() {
            @Override
            public MinebdConfig getConfig() {
                return createDefaultConfig();
            }
        });
        NbdServer s;
        s = injector.getInstance(NbdServer.class);
        try {
            s.start();
        } catch (BindException | InterruptedException e) {
            s.sendError(e);
            System.exit(1);
        }
        s.block();
    }

    private void sendError(Throwable e) {
        systemdUtil.sendError(1);
        logger.error("terminated due to exception at startup.", e);
    }

    private void block() {
        try {
            mainChannelFuture.sync();
        } catch (InterruptedException e) {
            //very unexpected
            systemdUtil.sendError(1);
            logger.info("terminated due to interrupt.", e);
        }
    }

    private static MinebdConfig createDefaultConfig() {
        final MinebdConfig config = new MinebdConfig();
        config.parentDir = "minedbDat";
        return config;
    }

    private void addShutownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::gracefulShutdown));
    }

    private void gracefulShutdown() {
        if (state == SHUTTINGDOWN || state == SHUTDOWN) {
            logger.error("we have already attempted to shut down, not doing it twice..");
            return;
        }
        state = SHUTTINGDOWN;
        logger.info("shutdown detected..");
        systemdUtil.sendStopping();
        try {
            //                        exportProvider.flush();
            //close also flushes first, so we only do one of them
            exportProvider.close();

            logger.info("shutting down eventLoops");
            try {
                eventLoopGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                logger.info("error shutting down eventLoops");
            }
            logger.info("we appear to have shut down gracefully..");
        } catch (IOException e) {
            logger.error("unable to flush and close ", e);
            systemdUtil.sendError(2);
        } finally {
            state = SHUTDOWN;
            logger.info("shutdown finally complete..");
        }
    }

    @VisibleForTesting
    @Override
    public void start() throws BindException, InterruptedException {
        state = STARTING;
        eventLoopGroup = new NioEventLoopGroup();
        addShutownHook();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .localAddress(new InetSocketAddress(port))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new HandshakePhase(exportProvider));
                    }
                });
        final ChannelFuture bind = bootstrap.bind();
        ChannelFuture f = bind.sync();
        logger.info("started up minebd on port {} ", port);
        final Channel channel = f.channel();
        mainChannelFuture = channel.closeFuture();
        systemdUtil.sendNotify(); //tell systemd we are ready
        state = STARTED;
    }

    @Override
    public void stop() throws Exception {
        gracefulShutdown();
    }
}
