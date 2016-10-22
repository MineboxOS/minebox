/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.zoning;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public class ZoneIp extends Zone {
	static String version = "$Id: ZoneIp.java 606 2013-07-06 22:07:22Z folkert $";
	String ip;

	public ZoneIp(String ip) throws UnknownHostException {
		this.ip = ip;

		InetAddress dummy = InetAddress.getByName(ip); // verify it is valid
	}

	public int hashCode() {
		return ip.hashCode();
	}
	
	public String toString() {
		return "ip " + ip;
	}
	
	public boolean isAllowed(Socket client) throws UnknownHostException {
		InetAddress iaRemote = ((InetSocketAddress)client.getRemoteSocketAddress()).getAddress();
		InetAddress iaLocal = InetAddress.getByName(ip);
		
		return iaRemote.equals(iaLocal);
	}
}
