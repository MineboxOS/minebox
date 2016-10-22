/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.cache;

public enum CacheClearType { MRU, LRU, RR;
	static String version = "$Id: CacheClearType.java 606 2013-07-06 22:07:22Z folkert $";
		final public String getName() {
			switch(this) {
				case MRU:
					return "MRU"; // most recently used
				case LRU:
					return "LRU"; // least recently used
				case RR:
					return "RR"; // random replacement
			}

			return null;
		}

		final static public CacheClearType getType(String name) {
			if (name.equalsIgnoreCase("MRU"))
				return MRU;
			if (name.equalsIgnoreCase("LRU"))
				return LRU;
			if (name.equalsIgnoreCase("RR"))
				return RR;

			return null;
		}
 };
