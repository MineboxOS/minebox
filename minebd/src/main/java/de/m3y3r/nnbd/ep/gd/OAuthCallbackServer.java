package de.m3y3r.nnbd.ep.gd;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.MultivaluedMap;

import org.glassfish.jersey.uri.UriComponent;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;

public class OAuthCallbackServer implements Runnable {

	private int port;
	private ChannelInboundHandler handler;
	private static final Logger logger = Logger.getLogger(OAuthCallbackServer.class.getName());

	OAuthCallbackServer(int port, ChannelInboundHandler handler) {
		this.port = port;
		this.handler = handler;
	}

	@Override
	public void run() {
		EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(eventLoopGroup)
				.channel(NioServerSocketChannel.class)
				.localAddress(new InetSocketAddress(port))
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(new HttpServerCodec(), new OAuthCallBackInboundHandler(), OAuthCallbackServer.this.handler);
					}
				});
			ChannelFuture f = b.bind().sync();
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

class OAuthCallBackInboundHandler extends ChannelInboundHandlerAdapter {

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if(msg instanceof HttpContent) {
			return;
		}

		assert msg instanceof HttpMessage;
		HttpRequest req = (HttpRequest) msg;
		String uri = req.uri();
		//TODO: may use io.netty.handler.codec.http.QueryStringDecoder here?!
		MultivaluedMap<String, String> qp = UriComponent.decodeQuery(new URI(uri), true);
		String state= qp.getFirst("state");
		String code = qp.getFirst("code");

		HttpResponse resp = new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.OK);
		ctx.write(resp);
		ctx.flush();

		ctx.fireUserEventTriggered(new String[] {state, code} );
	}
}
