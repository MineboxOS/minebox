/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.mirrors;

public enum MirrorType { METRO_MIRROR, GLOBAL_MIRROR;
	static String version = "$Id: MirrorType.java 606 2013-07-06 22:07:22Z folkert $";
		final public String toString() {
			switch(this) {
				case METRO_MIRROR:
					return "sync";
				case GLOBAL_MIRROR:
					return "async";
			}
			return null;
		}
};
