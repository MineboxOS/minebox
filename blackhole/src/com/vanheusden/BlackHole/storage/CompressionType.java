/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

public enum CompressionType { C_NONE, C_ZLIB;
	static String version = "$Id: CompressionType.java 606 2013-07-06 22:07:22Z folkert $";
	final public String toString() {
		switch(this) {
			case C_NONE:
				return "none";
			case C_ZLIB:
				return "zlib";
		}
		return "???";
	}

	static CompressionType getCompressionType(String which) {
                if (which.equalsIgnoreCase("none"))
                        return C_NONE;
                if (which.equalsIgnoreCase("zlib"))
                        return C_ZLIB;
		return null;
	}
}
