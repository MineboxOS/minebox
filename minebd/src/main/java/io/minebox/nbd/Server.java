package io.minebox.nbd;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class Server {

	private int port;
	private static final Logger logger = Logger.getLogger(Server.class.getName());

	private Server(int port) {
		this.port = port;
	}

	public static void main(String... args) {
		int nbdPort = Integer.parseInt(args.length > 1 && args[0] != null ? args[0] : "10809");
		Server s = new Server(nbdPort);
		s.startServer(nbdPort);
	}

	private void startServer(int nbdPort) {
		EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(eventLoopGroup)
				.channel(NioServerSocketChannel.class)
				.localAddress(new InetSocketAddress(port))
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(new NbdHandshakeInboundHandler());
					}
				});
			ChannelFuture f = b.bind().sync();
			logger.info("started up minebd on port " + nbdPort);
			f.channel().closeFuture().sync();
		} catch (InterruptedException e) {
			logger.log(Level.SEVERE, "Int1", e);
		} finally {
			try {
				eventLoopGroup.shutdownGracefully().sync();
			} catch (InterruptedException e) {
				logger.log(Level.SEVERE, "Int2", e);
			}
		}
	}
}
