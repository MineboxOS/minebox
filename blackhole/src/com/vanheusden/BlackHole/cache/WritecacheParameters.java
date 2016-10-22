/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.cache;

public class WritecacheParameters {
	static String version = "$Id: WritecacheParameters.java 606 2013-07-06 22:07:22Z folkert $";
	int forceFlushThreshold;
	int getTriggerFlushThreshhold;
	boolean sortWriteBackListBeforeWrite;
	int delayedWriteFlushInterval;
	int minFlushN;
	boolean readCacheDirtyBlocks;

	public WritecacheParameters(int forceFlushThreshold, int getTriggerFlushThreshhold, boolean sortWriteBackListBeforeWrite, int delayedWriteFlushInterval, int minFlushN, boolean readCacheDirtyBlocks) {
		this.forceFlushThreshold = forceFlushThreshold;
		this.getTriggerFlushThreshhold = getTriggerFlushThreshhold;
		this.sortWriteBackListBeforeWrite = sortWriteBackListBeforeWrite;
		this.delayedWriteFlushInterval = delayedWriteFlushInterval;
		this.minFlushN = minFlushN;
		this.readCacheDirtyBlocks = readCacheDirtyBlocks;
	}

	public String toString() {
		return "" + forceFlushThreshold + " " + getTriggerFlushThreshhold + " " + sortWriteBackListBeforeWrite + " " + delayedWriteFlushInterval + " " + minFlushN + " " + readCacheDirtyBlocks;
	}

	public int getForceFlushThreshold() {
		return forceFlushThreshold;
	}

	public int getTriggerFlushThreshhold() {
		return getTriggerFlushThreshhold;
	}

	public boolean getSortWriteBackListBeforeWrite() {
		return sortWriteBackListBeforeWrite;
	}

	public int getDelayedWriteFlushInterval() {
		return delayedWriteFlushInterval;
	}

	public int getMinFlushN() {
		return minFlushN;
	}

	public boolean getReadCacheDirtylocks() {
		return readCacheDirtyBlocks;
	}
}
