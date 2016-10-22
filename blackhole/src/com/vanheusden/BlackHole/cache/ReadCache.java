/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.cache;

import com.vanheusden.BlackHole.Utils;
import com.vanheusden.BlackHole.stats.*;
import com.vanheusden.BlackHole.Log;
import com.vanheusden.BlackHole.LogLevel;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.Semaphore;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Random;

public class ReadCache {
	static String version = "$Id: ReadCache.java 606 2013-07-06 22:07:22Z folkert $";
	final ReadcacheParameters rcp;
	LinkedHashMap<Long, byte []> cache = null;
	int cacheElements = 0;
	Random random = new Random();
	static int totalElementSize;
	ReentrantReadWriteLock cacheReadWriteLock = new ReentrantReadWriteLock(true);
	Lock cacheRL = cacheReadWriteLock.readLock();
	Lock cacheWL = cacheReadWriteLock.writeLock();
	//
	AtomicLong cntAdd = new AtomicLong(), cntGet = new AtomicLong(), cntRemove = new AtomicLong(), cntFull = new AtomicLong();
	AtomicLong getHit = new AtomicLong(), getMis = new AtomicLong();
	Stats stats = Stats.getInstance();
	String name;

	public void cacheReadLock() {
		cacheRL.lock();
	}

	public void cacheReadUnlock() {
		cacheRL.unlock();
	}

	public void cacheWriteLock() {
		cacheWL.lock();
	}

	public void cacheWriteUnlock() {
		cacheWL.unlock();
	}

	public static int getTotalElementSize() {
		return totalElementSize;
	}

	public ReadCache(ReadcacheParameters rcp, int elementSize, String name) {
		this.rcp = rcp;
		this.name = name;

		cacheElements = Math.max(2, rcp.getCacheElements());
		Log.log(LogLevel.LOG_INFO, "ReadCache: " + cacheElements + " elements");
		cache = new LinkedHashMap<Long, byte []>();

		totalElementSize += elementSize;

		stats.add(new StatsElementCounter(cntAdd, "RC " + name + ": cnt add"));
		stats.add(new StatsElementCounter(cntGet, "RC " + name + ": cnt get"));
		stats.add(new StatsElementCounter(cntRemove, "RC " + name + ": cnt remove"));
		stats.add(new StatsElementAverage(cntFull, cntAdd, StatsOutputType.PERCENTAGE, false, "RC " + name + ": % full when add"));
		stats.add(new StatsElementAverage(getHit, cntGet, StatsOutputType.PERCENTAGE, false, "RC " + name + ": % hit when get"));
		stats.add(new StatsElementAverage(getMis, cntGet, StatsOutputType.PERCENTAGE, false, "RC " + name + ": % mis when get"));
	}

	public void add(long elementNr, byte [] element) {
		cacheWriteLock();

		assert element != null;

		cntAdd.addAndGet(1);

		if (cache.size() >= cacheElements) {
			int n = cache.size();

			cntFull.addAndGet(1);

			if (rcp.getCacheClearType() == CacheClearType.LRU) {
				Iterator it = cache.keySet().iterator();
				for(int loop=0; loop<rcp.getRemoveNWhenFull(); loop++) {
					if (!it.hasNext())
						break;
					it.next();
					it.remove();
				}
			}
			else if (rcp.getCacheClearType() == CacheClearType.RR) {
				int step = cache.size() / rcp.getRemoveNWhenFull();
				Iterator it = cache.keySet().iterator();

				for(int loop=0; loop<rcp.getRemoveNWhenFull(); loop++) {

					int skipN = random.nextInt(step) + 1;
					for(int skip=0; skip<skipN; skip++) {
						assert it.hasNext();
						it.next();
					}

					it.remove();
				}
			}
			else if (rcp.getCacheClearType() == CacheClearType.MRU) {
				Iterator it = cache.keySet().iterator();
				for(int loop=0; loop<(n - rcp.getRemoveNWhenFull()); loop++)
					it.next();
				for(;;) {
					it.remove();
					if (!it.hasNext())
						break;
					it.next();
				}
			}
		}

		cache.put(Long.valueOf(elementNr), element);

		cacheWriteUnlock();
	}

	public void update(long elementNr, byte [] element) {
		cacheWriteLock();
		if (cache.get(Long.valueOf(elementNr)) != null)
			cache.put(Long.valueOf(elementNr), element);
		cacheWriteUnlock();
	}

	boolean reshuffleCache(long elementNr) {
		Long el = Long.valueOf(elementNr);
		if (el == null)
			return false;
		byte [] element = cache.get(el);
		cache.remove(el);
		cache.put(el, element);
		return true;
	}

	public byte [] get(long elementNr) {
		cacheReadLock();
		byte [] out = cache.get(Long.valueOf(elementNr));
		cacheReadUnlock();

		cntGet.addAndGet(1);
		if (out == null)
			getMis.addAndGet(1);
		else
			getHit.addAndGet(1);

		return out;
	}

	public void remove(long elementNr) {
		cache.remove(Long.valueOf(elementNr));
		cntRemove.addAndGet(1);
	}
}
