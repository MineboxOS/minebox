package de.m3y3r.nnbd.ep.gd;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.json.JsonObject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class OAuth extends ChannelInboundHandlerAdapter implements Closeable {

	private static OAuth instance;

	private OAuthCallbackServer oauthServer;

	private static final CharSequence clientId = "clientId";
	private static final CharSequence clientSecret = "clientSecret";

	private static final String authUrl = "https://accounts.google.com/o/oauth2/auth";
	private static final String tokenUrl = "https://accounts.google.com/o/oauth2/token";
	private static final int redirectUriPort = 1324;
	private static final CharSequence redirectUri = "http://localhost:" + redirectUriPort;
	//FIXME: documentation is not clear here: https://www.googleapis.com/auth/drive.appdata or https://www.googleapis.com/auth/drive.appfolder?!
	private static final CharSequence scopes = "https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive.file";


	private Map<String, CountDownLatch> cdls;
	private Thread oauthServerThread;

	private Map<String, String> states;

	private OAuth() {
		this.oauthServer = new OAuthCallbackServer(redirectUriPort, this);
		cdls = new HashMap<>();
		states = new HashMap<>();
		oauthServerThread = new Thread(oauthServer);
	}

	public String getToken() throws IOException {
		if(!oauthServerThread.isAlive()) {
			oauthServerThread.start();
		}

		String state = UUID.randomUUID().toString();

		URI uri = UriBuilder.fromUri(authUrl)
				.queryParam("client_id", clientId)
				.queryParam("response_type", "code")
				.queryParam("state", state)
				.queryParam("scope", scopes)
				.queryParam("redirect_uri", redirectUri).build();
		GuiUtil.openUrl(uri);
		CountDownLatch cdl = new CountDownLatch(1);
		synchronized (this) {
			cdls.put(state, cdl);
		}

		/* block for callback */
		try {
			cdl.await(60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			return null;
		}

		synchronized (this) {
			return states.remove(state);
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		String[] stateCode = (String[]) evt;
		String state = stateCode[0];
		String code = stateCode[1];

		HttpAuthenticationFeature basic = HttpAuthenticationFeature.basic(clientId.toString(), clientSecret.toString());
		Form form = new Form();
		form.param("grant_type", "authorization_code")
			.param("code", code)
			.param("redirect_uri", redirectUri.toString());

		Response r = ClientBuilder.newClient().target(tokenUrl)
			.register(basic)
			.request().post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
		JsonObject jo = r.readEntity(JsonObject.class);
		String accessToken = jo.getString("access_token");

		CountDownLatch cdl;
		synchronized (this) {
			cdl = cdls.remove(state);
			states.put(state, accessToken);
		}
		cdl.countDown();
	}

	@Override
	public void close() throws IOException {
		if(oauthServerThread.isAlive())
			this.oauthServerThread.interrupt();
	}

	public synchronized static OAuth getInstance() {
		if(instance == null) {
			instance = new OAuth();
		}

		return instance;
	}
}
