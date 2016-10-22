/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole;

import com.vanheusden.BlackHole.protocol.*;

public class ThreadLun {
	static String version = "$Id: ThreadLun.java 606 2013-07-06 22:07:22Z folkert $";
	Thread t;
	int lun;
	ProtocolListener pl;
	String name, endPoint;
	Protocol p;

	public ThreadLun(ProtocolListener pl, Thread t, int lun, String name, String endPoint, Protocol handler) {
		this.pl = pl;
		this.t = t;
		this.lun = lun;
		this.name = name;
		this.endPoint = endPoint;
		this.p = handler;
	}

	public int getLun() {
		return lun;
	}

	public String getName() {
		return name;
	}

	public String getEndPoint() {
		return endPoint;
	}

	public Thread getThread() {
		return t;
	}

	public ProtocolListener getProtocolListener() {
		return pl;
	}

	public Protocol getHandler() {
		return p;
	}
}
