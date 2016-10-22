/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.mirrors;

import com.vanheusden.BlackHole.protocol.*;

public class MirrorParameters {
	static String version = "$Id: MirrorParameters.java 606 2013-07-06 22:07:22Z folkert $";
	MirrorType mt;
	ProtocolType pt;
	String host;
	int port;
	boolean disconnectBlock;
	String par1, par2;
	int lun;

	public MirrorParameters(MirrorType mt, ProtocolType pt, String host, int port, boolean disconnectBlock, String par1, String par2, int lun) {
		this.mt = mt;
		this.pt = pt;
		this.host = host;
		this.port = port;
		this.disconnectBlock = disconnectBlock;
		this.par1 = par1;
		this.par2 = par2;
		this.lun =  lun;
	}

	public MirrorType getMirrorType() {
		return mt;
	}

	public ProtocolType getProtocolType() {
		return pt;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public boolean getDisconnectBlock() {
		return disconnectBlock;
	}

	public String getPar1() {
		return par1;
	}

	public String getPar2() {
		return par2;
	}

	public int getLun() {
		return lun;
	}
}
