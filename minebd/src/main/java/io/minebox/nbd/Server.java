package io.minebox.nbd;

import java.io.IOException;
import java.net.InetSocketAddress;

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

    private static final String NBD_DEFAULT_PORT = "10809";
    private int port;
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private final MineboxExport.Config config;
    private final ExportProvider exportProvider;
    private EventLoopGroup eventLoopGroup;

    private Server(int port) {
        addShutownHook();
        this.port = port;
        config = new MineboxExport.Config();
        exportProvider = new MineboxExport(config);
    }

    public static void main(String... args) {
        int nbdPort = Integer.parseInt(args.length > 1 && args[0] != null ? args[0] : NBD_DEFAULT_PORT);
        Server s = new Server(nbdPort);
        s.startServer(nbdPort);
    }

    private void addShutownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("shutdown detected..");
            SystemdUtil.sendStopping();
            try {

                if (exportProvider != null) {
//                        exportProvider.flush();
                    //close also flushes first, so we only do one of them
                    exportProvider.close();
                }
                logger.info("shutting down eventLoops");
                try {
                    eventLoopGroup.shutdownGracefully().sync();
                } catch (InterruptedException e) {
                    logger.info("error shutting down eventLoops");
                }
                logger.info("we appear to have shut down gracefully..");
                System.exit(0);
            } catch (IOException e) {
                logger.error("unable to flush and close ", e);
                SystemdUtil.sendError(2);
                System.exit(2);
            } finally {
                logger.info("shutdown finally complete..");
            }
        }));
    }

    private void startServer(int nbdPort) {
        eventLoopGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(eventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(port))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new HandshakePhase(exportProvider));
                        }
                    });
            ChannelFuture f = b.bind().sync();
            logger.info("started up minebd on port {} ", nbdPort);
            final Channel channel = f.channel();
            final ChannelFuture channelFuture = channel.closeFuture();
            SystemdUtil.sendNotify(); //tell systemd we are ready
            channelFuture.sync(); //wait infinitely?
            logger.info("stopped main thread");
        } catch (InterruptedException e) {
            //very unexpected
            SystemdUtil.sendError(1);
            logger.error("Int1", e);
        } finally {
//            try {
//                SystemdUtil.sendStopping();
//                eventLoopGroup.shutdownGracefully().sync();
//            } catch (InterruptedException e) {
//                logger.error("Int2", e);
//            }
        }
    }

}
