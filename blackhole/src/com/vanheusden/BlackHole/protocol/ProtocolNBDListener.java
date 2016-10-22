/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.protocol;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.mirrors.*;
import com.vanheusden.BlackHole.snapshots.*;
import com.vanheusden.BlackHole.sockets.*;
import com.vanheusden.BlackHole.stats.*;
import com.vanheusden.BlackHole.storage.*;
import com.vanheusden.BlackHole.zoning.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProtocolNBDListener extends ProtocolListener implements Runnable {
	static String version = "$Id: ProtocolNBDListener.java 606 2013-07-06 22:07:22Z folkert $";
	LunManagement lm;
	int lun;
	Storage storage;
	String name;
	MirrorManager mirrorMan;
	SnapshotManager snapMan;
	MyServerSocket myServerSocketNBD;
	ZoneManager zm;
	boolean assumeBarriers;

	public ProtocolNBDListener(Storage storage, MirrorManager mirrorMan, SnapshotManager snapMan, LunManagement lm, int lun, String adapter, int port, String name, ZoneManager zm, boolean assumeBarriers) {
		this.lm = lm;
		this.lun = lun;
		this.adapter = adapter;
		this.port = port;
		this.storage =  storage;
		this.name = name;
		this.mirrorMan = mirrorMan;
		this.snapMan = snapMan;
		this.zm = zm;
		this.assumeBarriers = assumeBarriers;

		Log.log(LogLevel.LOG_INFO, "Starting NBD protocol listener for lun " + name + " with number " + lun + " at " + adapter + ":" + port);
	}

	public int getLun() {
		return lun;
	}

	public String getName() {
		return name;
	}

	public String getNetworkAddress() {
		return adapter + " " + port;
	}

	public String getConfigLine() {
		return lun + " nbd "+ adapter + " " + port + " " + name + " " + assumeBarriers;
	}

	public String getProtocol() {
		return "NBD";
	}

	public void stop() throws IOException {
		myServerSocketNBD.close();
	}

	public void run() {
		Log.log(LogLevel.LOG_INFO, "NBD listener thread started");
		try {
			myServerSocketNBD = new MyServerSocket(adapter, port);
		}
		catch(IOException ioe) {
			Log.log(LogLevel.LOG_CRIT, "Failed to start listen socket!");
			Log.showException(ioe);
			return;
		}

		for(;;) {
			try {
				MyClientSocket mcs = myServerSocketNBD.acceptConnection();
				if (mcs == null)
					break;

				if (zm.isAllowed(lun, mcs.getSocket())) {
					Log.log(LogLevel.LOG_INFO, "Connection " + mcs.getName() + " for " + name + " (" + lun + ")");

					ProtocolNBD handler = new ProtocolNBD(lm.getSectorMapper(), lun, storage, mirrorMan, snapMan, mcs, assumeBarriers);
					Thread t = new Thread(handler, "NBD lun " + name + "/" + lun);
					t.start();
					ThreadLunReaper.getInstance().add(new ThreadLun(this, t, lun, name, mcs.getName(), handler));
				}
				else {
					mcs.closeSocket();
					Log.log(LogLevel.LOG_INFO, "Connection " + mcs.getName() + " for " + name + " (" + lun + ") ACCESS DENIED");
				}
			}
			catch(SocketException se) {
				Log.showException(se);
			}
			catch(IOException ioe) {
				Log.showException(ioe);
			}
		}

		try {
			myServerSocketNBD.close();
		}
		catch(IOException ioe) {
			Log.log(LogLevel.LOG_CRIT, "Failed to close listen socket!");
			Log.showException(ioe);
		}
		Log.log(LogLevel.LOG_WARN, "NBD listener thread STOPPED!");
	}
}
