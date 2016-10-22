/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
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

public class ProtocolIMDiskListener extends ProtocolListener implements Runnable {
	static String version = "$Id$";
	LunManagement lm;
	int lun;
	Storage storage;
	String name;
	MirrorManager mirrorMan;
	SnapshotManager snapMan;
	MyServerSocket myServerSocketIMDisk;
	ZoneManager zm;
	long size;
	boolean assumeBarriers;

	public ProtocolIMDiskListener(Storage storage, MirrorManager mirrorMan, SnapshotManager snapMan, LunManagement lm, int lun, String adapter, int port, String name, ZoneManager zm, long size, boolean assumeBarriers) {
		this.lm = lm;
		this.lun = lun;
		this.adapter = adapter;
		this.port = port;
		this.storage =  storage;
		this.name = name;
		this.mirrorMan = mirrorMan;
		this.snapMan = snapMan;
		this.zm = zm;
		this.size = size;
		this.assumeBarriers = assumeBarriers;

		Log.log(LogLevel.LOG_INFO, "Starting IMDisk protocol listener for lun " + name + " with number " + lun + " at " + adapter + ":" + port);
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
		return lun + " imdisk "+ adapter + " " + port + " " + name + " " + assumeBarriers;
	}

	public String getProtocol() {
		return "IMDisk";
	}

	public void stop() throws IOException {
		myServerSocketIMDisk.close();
	}

	public void run() {
		Log.log(LogLevel.LOG_INFO, "IMDisk listen thread started");
		try {
			myServerSocketIMDisk = new MyServerSocket(adapter, port);
		}
		catch(IOException ioe) {
			Log.log(LogLevel.LOG_CRIT, "Failed to start listen socket!");
			Log.showException(ioe);
			return;
		}

		for(;;) {
			try {
				MyClientSocket mcs = myServerSocketIMDisk.acceptConnection();
				if (mcs == null)
					break;

				if (zm.isAllowed(lun, mcs.getSocket())) {
					Log.log(LogLevel.LOG_INFO, "Connection " + mcs.getName() + " for " + name + " (" + lun + ")");

					ProtocolIMDisk handler = new ProtocolIMDisk(lm.getSectorMapper(), lun, storage, mirrorMan, snapMan, mcs, size, assumeBarriers);
					Thread t = new Thread(handler, "IMDisk lun " + name + "/" + lun);
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
			myServerSocketIMDisk.close();
		}
		catch(IOException ioe) {
			Log.log(LogLevel.LOG_CRIT, "Failed to close listen socket!");
			Log.showException(ioe);
		}
		Log.log(LogLevel.LOG_WARN, "IMDisk listen thread STOPPED");
	}
}
