/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.stats;

public class StatsElement {
	static String version = "$Id: StatsElement.java 606 2013-07-06 22:07:22Z folkert $";
	boolean abbr;
	final long TB = 1024L * 1024L * 1024L * 1024L;
	final int GB = 1024 * 1024 * 1024;
	final int MB = 1024 * 1024;
	final int KB = 1024;

	public StatsElement() {
	}

	public String abbrName(double value) {
		if (value >= TB)
			return "TB";
		else if (value >= GB)
			return "GB";
		else if (value >= MB)
			return "MB";
		else if (value >= KB)
			return "KB";

		return "B";
	}

	public long abbrValue(double value) {
		if (value >= TB)
			return TB;
		else if (value >= GB)
			return GB;
		else if (value >= MB)
			return MB;
		else if (value >= KB)
			return KB;

		return 1;
	}

	public String abbreviate(double value, boolean i) {
		if (!abbr)
			return "" + (i ? (long)value : value);

		long divider = abbrValue(value);
		String name = abbrName(value);

		value /= divider;

		return "" + (i ? (long)value : value) + name;
	}

	public String toString() {
		return "?";
	}
}
