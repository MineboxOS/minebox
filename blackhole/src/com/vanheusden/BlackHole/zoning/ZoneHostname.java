/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.zoning;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class ZoneHostname extends Zone {
	static String version = "$Id: ZoneHostname.java 606 2013-07-06 22:07:22Z folkert $";
	String hostName;

	public ZoneHostname(String hostName) {
		this.hostName = hostName;
	}

	public int hashCode() {
		return hostName.hashCode();
	}
	
	public String toString() {
		return "hostname " + hostName;
	}
	
	public boolean isAllowed(Socket client) {
		InetAddress iaRemote = ((InetSocketAddress)client.getRemoteSocketAddress()).getAddress();
		String remoteHostName = iaRemote.getHostName();
		
		return remoteHostName.equals(hostName);
	}
}
