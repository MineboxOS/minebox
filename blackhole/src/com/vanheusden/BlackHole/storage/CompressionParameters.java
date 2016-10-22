/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

public class CompressionParameters {
	static String version = "$Id: CompressionParameters.java 606 2013-07-06 22:07:22Z folkert $";
	CompressionType ct;
	int level;

	public CompressionParameters(CompressionType ct, int level) {
		this.ct = ct;
		this.level = level;
	}

	public CompressionParameters(String pars) {
		String [] elems = pars.split(" ");

		ct = CompressionType.getCompressionType(elems[0]);
		level = Integer.valueOf(elems[1]);
	}

	public String toString() {
		return "" + ct.toString() + " " + level;
	}

	public CompressionType getType() {
		return ct;
	}

	public int getLevel() {
		return level;
	}
}
