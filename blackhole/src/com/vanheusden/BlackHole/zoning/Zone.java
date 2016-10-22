/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.zoning;

import com.vanheusden.BlackHole.VHException;

import java.net.Socket;
import java.net.UnknownHostException;

public abstract class Zone {
	static String version = "$Id: Zone.java 606 2013-07-06 22:07:22Z folkert $";
	protected Zone() {
	}

	public static Zone getInstance(String descr) throws VHException, UnknownHostException {
		String [] parts = descr.split(" ");
		if (parts.length < 2)
			throw new VHException("Zone type \"" + descr + "\" not understood");
		
		if (parts[0].equals("ip"))
			return new ZoneIp(parts[1]);
		else if (parts[0].equals("netmask"))
			return new ZoneNetmask(parts[1], parts[2]);
		else if (parts[0].equals("hostname"))
			return new ZoneHostname(parts[1]);
		else
			throw new VHException("Zone type " + parts[0] + " not known");
	}

	public byte [] ipv4AddrToBytes(String in) throws VHException {
		String [] parts = in.split("\\.");
		if (parts.length != 4)
			throw new VHException("Invalid ipv4 notation: " + in);

		byte [] out = new byte[4];
		for(int index=0; index<4; index++) {
			int b = Integer.valueOf(parts[index]);
			out[index] = (byte)b;
		}

		return out;
	}
	
	abstract public int hashCode();
	abstract public String toString();
	abstract public boolean isAllowed(Socket client) throws UnknownHostException, VHException;
}
