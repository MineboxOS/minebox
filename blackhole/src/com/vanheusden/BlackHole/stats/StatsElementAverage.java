/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.stats;

import java.util.concurrent.atomic.AtomicLong;

public class StatsElementAverage extends StatsElement {
	static String version = "$Id: StatsElementAverage.java 606 2013-07-06 22:07:22Z folkert $";
	AtomicLong counter, n;
	StatsOutputType sot;
	String descr;

	public StatsElementAverage(AtomicLong counter, AtomicLong n, StatsOutputType sot, boolean abbr, String descr) {
		this.counter = counter;
		this.n = n;
		this.sot = sot;
		this.abbr = abbr;
		this.descr = descr;
	}

	public String toString() {
		if (n.get() == 0)
			return descr + ": no measurements";

		if (sot == StatsOutputType.FLOAT)
			return descr + ": "+ abbreviate((double)counter.get() / (double)n.get(), false);

		if (sot == StatsOutputType.PERCENTAGE)
			return descr + ": "+ ((double)(counter.get() * 100.0) / (double)n.get());

		if (sot == StatsOutputType.INTEGER)
			return descr + ": "+ abbreviate(counter.get() / n.get(), true);

		return null;
	}
}
