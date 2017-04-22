package io.minebox.nbd;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.google.common.annotations.VisibleForTesting;
import io.minebox.config.MinebdConfig;
import io.minebox.nbd.ep.ExportProvider;
import io.minebox.nbd.ep.chunked.MineboxExport;
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

public class Server {

    private final int port;
    private final SystemdUtil systemdUtil;
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private final MinebdConfig config;
    private final ExportProvider exportProvider;
    private EventLoopGroup eventLoopGroup;

    @VisibleForTesting
    Server(SystemdUtil systemdUtil, MinebdConfig config) {
        this.port = config.nbdPort;
        this.systemdUtil = systemdUtil;
        this.config = config;
        final Encryption encryption = new SymmetricEncryption(this.config.encryptionSeed);
        exportProvider = new MineboxExport(this.config, encryption);
    }

    public static void main(String... args) {
        Server s = new Server(new SystemdUtil(), createDefaultConfig());
        s.startServer();
    }

    private static MinebdConfig createDefaultConfig() {
        final MinebdConfig config = new MinebdConfig();
        config.parentDir = "minedbDat";
        return config;
    }

    private void addShutownHook(ExportProvider exportProvider, EventLoopGroup eventLoopGroup) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
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
                logger.info("shutdown finally complete..");
            }
        }));
    }

    @VisibleForTesting
    void startServer() {
        eventLoopGroup = new NioEventLoopGroup();
        addShutownHook(exportProvider, eventLoopGroup);
        try {
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
            final ChannelFuture channelFuture = channel.closeFuture();
            systemdUtil.sendNotify(); //tell systemd we are ready
            channelFuture.sync();
            //wait infinitely?
            logger.info("stopped main thread");

        } catch (InterruptedException e) {
            //very unexpected
            systemdUtil.sendError(1);
            logger.info("terminated due to interrupt.", e);
        } catch (Exception e) {
            systemdUtil.sendError(1);
            logger.info("terminated due to exception at startup.", e);
            System.exit(1);
        }
    }

}
