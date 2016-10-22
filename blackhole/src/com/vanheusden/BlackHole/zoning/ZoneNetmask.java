/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.zoning;

import com.vanheusden.BlackHole.VHException;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

class ZoneNetmask extends Zone {
	static String version = "$Id: ZoneNetmask.java 606 2013-07-06 22:07:22Z folkert $";
	String ip, netmask;

	public ZoneNetmask(String ip, String netmask) {
		this.ip = ip;
		this.netmask = netmask;
	}

	public int hashCode() {
		return (ip + netmask).hashCode();
	}
	
	public String toString() {
		return "netmask " + ip + " " + netmask;
	}
	
	public boolean isAllowed(Socket client) throws UnknownHostException, VHException {
		byte [] remoteBytes = ((InetSocketAddress)client.getRemoteSocketAddress()).getAddress().getAddress();
		byte [] netmaskBytes = ipv4AddrToBytes(netmask);
		byte [] localBytes = ipv4AddrToBytes(ip);
		
		if (remoteBytes.length != netmaskBytes.length || remoteBytes.length != localBytes.length)
			return false;
			
		for(int index=0; index<remoteBytes.length; index++) {
			if ((remoteBytes[index] & netmaskBytes[index]) != localBytes[index])
				return false;
		}
		
		return true;
	}
}
