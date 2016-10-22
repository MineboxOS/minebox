/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.protocol;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.sockets.*;
import com.vanheusden.BlackHole.stats.*;
import com.vanheusden.BlackHole.storage.*;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

public abstract class Protocol extends Thread {
	static String version = "$Id: Protocol.java 606 2013-07-06 22:07:22Z folkert $";
	static Stats stats;
	MyClientSocket mcs;
	Storage storage;

	abstract public void run();

	public Protocol() {
	}

	public Protocol(Storage storage, MyClientSocket mcs) throws Exception {
		this.storage = storage;
		this.mcs = mcs;

		mcs.setTcpNoDelay();
	}

	abstract public void close() throws IOException ;
}
