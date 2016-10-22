/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.stats;

import java.util.concurrent.atomic.AtomicLong;

public class StatsElementCounter extends StatsElement {
	static String version = "$Id: StatsElementCounter.java 606 2013-07-06 22:07:22Z folkert $";
	AtomicLong counter;
	String descr;

	public StatsElementCounter(AtomicLong counter, String descr) {
		this.counter = counter;
		this.descr = descr;
		this.abbr = false;
	}

	public StatsElementCounter(AtomicLong counter, boolean a, String descr) {
		this.counter = counter;
		this.descr = descr;
		this.abbr = a;
	}

	public String toString() {
		return descr + ": " + abbreviate(counter.get(), true);
	}
}
