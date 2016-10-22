/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.cache;

public class ReadcacheParameters {
	static String version = "$Id: ReadcacheParameters.java 606 2013-07-06 22:07:22Z folkert $";
	CacheClearType cct;
	int removeNWhenFull;
	int cacheElements;

	public ReadcacheParameters(CacheClearType cct, int removeNWhenFull, int cacheElements) {
		this.cct = cct;
		this.removeNWhenFull = removeNWhenFull;
		this.cacheElements = cacheElements;
	}

	public String toString() {
		return "" + cct.toString() + " " + removeNWhenFull + " " + cacheElements;
	}

	public CacheClearType getCacheClearType() {
		return cct;
	}

	public int getRemoveNWhenFull() {
		return removeNWhenFull;
	}

	public int getCacheElements() {
		return cacheElements;
	}
}
