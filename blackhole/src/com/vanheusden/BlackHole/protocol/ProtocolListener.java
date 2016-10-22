/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.protocol;

import com.vanheusden.BlackHole.stats.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ProtocolListener implements Runnable {
	static String version = "$Id: ProtocolListener.java 606 2013-07-06 22:07:22Z folkert $";
	String adapter;
	int port;

	protected ProtocolListener() {
	}

	public void setAdapter(String adapter) {
		this.adapter = adapter;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public abstract String getConfigLine();

	public abstract String getNetworkAddress();

	public abstract String getProtocol();

	public abstract String getName();

	public abstract int getLun();

	public abstract void stop() throws IOException;
}
