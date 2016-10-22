/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.cache.*;
import com.vanheusden.BlackHole.stats.*;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public abstract class DelayedWriteCache implements Runnable {
	static String version = "$Id$";
	WritecacheParameters wcp;
	//
	boolean didFlush = true;
	UniqueOriginalOrderList<Long, Object[]> delayedWriteList = null;
	Thread delayWriteThread;
	AtomicBoolean exitAfterFlush = new AtomicBoolean();
	String what = null;
	// protected by write lock
	boolean fwa = false;
	int avgN = 0, lastN = -1;
	long lastWrite = System.currentTimeMillis();
	//
	protected Dummy myMonitorObjectGet = new Dummy();
	protected Dummy myMonitorObjectPut = new Dummy();
	AtomicBoolean emergencyFlush = new AtomicBoolean();
	//
	double canDo = -1.0;
	AtomicLong nQueueFull = new AtomicLong();
	AtomicLong fullTime = new AtomicLong();
	AtomicLong totalFill = new AtomicLong();
	AtomicLong nMeasures = new AtomicLong();
	AtomicLong totalSortTime = new AtomicLong();
	AtomicLong nFlushed = new AtomicLong();
	AtomicLong totalWriteTime = new AtomicLong();
	AtomicLong nFlushedBlocks = new AtomicLong();
	Stats stats = Stats.getInstance();

        public void doWaitForGet() throws InterruptedException {
                synchronized(myMonitorObjectGet) {
                        myMonitorObjectGet.wait();
                }
        }

        private void doNotifyAGet() {
                synchronized(myMonitorObjectGet){
                        myMonitorObjectGet.notifyAll();
                }
        }

        public void doWaitForPut(int to) throws InterruptedException {
                synchronized(myMonitorObjectPut) {
                        myMonitorObjectPut.wait(to);
                }
        }

        private void doNotifyAPut() {
                synchronized(myMonitorObjectPut){
                        myMonitorObjectPut.notifyAll();
                }
        }

	public DelayedWriteCache() {
	}

	public void forget(long sectorNr) {
		assert checkWriteLocked();
		delayedWriteList.remove(Long.valueOf(sectorNr));
	}

	public void init() {
		delayedWriteList = new UniqueOriginalOrderList<Long, Object[]>();
		delayWriteThread = new Thread(this, "DWC flush thread " + what);
		delayWriteThread.start();

		String file = new File(what).getName();
		stats.add(new StatsElementCounter(nQueueFull, "DWC " + file + ": queue full"));
		stats.add(new StatsElementCounter(fullTime, "DWC " + file + ": total full time (ms)"));
		stats.add(new StatsElementAverage(fullTime, nQueueFull, StatsOutputType.FLOAT, false, "DWC " + file + ": average queue full time (ms)"));
		stats.add(new StatsElementAverage(totalFill, nMeasures, StatsOutputType.FLOAT, false, "DWC " + file + ": average number of blocks in cache"));
		stats.add(new StatsElementCounter(nFlushed, "DWC " + file + ": number of flushes"));
		stats.add(new StatsElementAverage(totalSortTime, nFlushed, StatsOutputType.FLOAT, false, "DWC " + file + ": average sort time (ms)"));
		stats.add(new StatsElementAverage(totalWriteTime, nFlushed, StatsOutputType.FLOAT, false, "DWC " + file + ": average write time (ms) per block"));
		stats.add(new StatsElementAverage(nFlushed, totalWriteTime, StatsOutputType.FLOAT, false, "DWC " + file + ": average number of blocks per ms"));
		stats.add(new StatsElementAverage(nFlushedBlocks, nFlushed, StatsOutputType.FLOAT, false, "DWC " + file + ": average number of blocks per write (ms)"));
	}

	public boolean [] checkFlush() throws InterruptedException {
		assert checkReadLocked() || checkWriteLocked();
		boolean triggered = delayedWriteList.size() >= wcp.getTriggerFlushThreshhold();
		boolean needFlush = delayedWriteList.size() >= wcp.getForceFlushThreshold();

		if (needFlush) {
			if (didFlush) {
				Log.log(LogLevel.LOG_INFO, what + " force flush start threshold " + delayedWriteList.size() + " " + wcp.getForceFlushThreshold());
				didFlush = false;
			}
			doNotifyAPut();
		}
		else if (triggered) {
			if (didFlush) {
				Log.log(LogLevel.LOG_INFO, what + " flush start threshold " + delayedWriteList.size() + " " + wcp.getTriggerFlushThreshhold());
				didFlush = false;
			}
			doNotifyAPut();
		}

		return new boolean [] { triggered, needFlush };
	}

	public void forceFlush() throws InterruptedException {
		nQueueFull.addAndGet(1);
		Log.log(LogLevel.LOG_INFO, what + " full " + delayedWriteList.size() + " " + wcp.getForceFlushThreshold());
		long start = System.currentTimeMillis();

		emergencyFlush.set(true);
		doNotifyAPut();
		doWaitForGet();

		Log.log(LogLevel.LOG_INFO, what + " full ok " + delayedWriteList.size() + " " + wcp.getForceFlushThreshold());
	}

	public void queueDirtyBlock(long elementNr, byte [] element) throws InterruptedException {
		assert checkWriteLocked();

		assert element != null;
		delayedWriteList.put(Long.valueOf(elementNr), new Object [] { element, null });
	}

	public void flush() {
		assert checkWriteLocked();
		fwa = true;
	}

	abstract protected void putLow(long blockNr, byte [] data, byte [] hash) throws IOException, VHException, SQLException, BadPaddingException, IllegalBlockSizeException, DataFormatException;

	abstract protected void writeLock();

	abstract protected void writeUnlock();

        abstract protected boolean checkReadLocked();

        abstract protected boolean checkWriteLocked();

	public byte [] getBlock(long elementNr) {
		assert checkReadLocked() || checkWriteLocked();

		Object [] pair = delayedWriteList.get(elementNr);
		if (pair != null)
			return (byte [])pair[0];

		return null;
	}

	private void writeCacheToDisk(boolean forceWriteAll, boolean emergency) throws IOException, VHException, SQLException, NoSuchAlgorithmException {
		assert checkWriteLocked();

		long now = System.currentTimeMillis();
		long timeSinceLastWrite = (now - lastWrite);
                long timeToNextFlush = wcp.getDelayedWriteFlushInterval() - timeSinceLastWrite;
                while(timeToNextFlush < 0)
                        timeToNextFlush += wcp.getDelayedWriteFlushInterval();

		boolean flushMore = false;
		int nAvailable = delayedWriteList.size();
		int tempAvgN = (avgN * 2 + nAvailable) / 3;
		double timeFactor = Math.min(1.01, timeSinceLastWrite / (double)wcp.getDelayedWriteFlushInterval());

		tempAvgN = Math.max(wcp.getMinFlushN(), (int)(tempAvgN * timeFactor));

		int nToDo = Math.min(tempAvgN, nAvailable);
		if (emergency)
			tempAvgN = nToDo = (wcp.getTriggerFlushThreshhold() + wcp.getForceFlushThreshold()) / 3;
		if (forceWriteAll)
			tempAvgN = nToDo = nAvailable;

		double curCanDo = 0.0;
		if (totalWriteTime.get() > 0) {
			curCanDo = (double)nFlushedBlocks.get() / (double)totalWriteTime.get();
			if (canDo < 0)
				canDo = curCanDo;
			else
				canDo = (canDo * 4 + curCanDo) / 5.0;
		}

		// if forced flush (@ exit)
		// or half of the cache is dirty
		// or last write was more than 5 seconds ago
		// the do it
		if (nAvailable > wcp.getForceFlushThreshold())
			flushMore = true;
		if (forceWriteAll || flushMore || timeSinceLastWrite > wcp.getDelayedWriteFlushInterval() || emergency) {
			if (nToDo > 0) {
				didFlush = true;
				Log.log(LogLevel.LOG_INFO, "DWC: flush " + nToDo + " blocks of " + nAvailable + ", " + (forceWriteAll ? "forced, ":"") + "avgN: " + avgN + ", time factor: " + timeFactor + ", added: " + (nAvailable - lastN) + ", can do: " + (canDo * timeToNextFlush));
				avgN = nAvailable;

				List<Map.Entry<Long, Object[]>> kvlist = delayedWriteList.getFromBottomAndRemove(nToDo);

				Hasher.getInstance().doit(kvlist);

				nFlushedBlocks.addAndGet(nToDo);

				// sort list to help seektimes on rotational media
				if (wcp.getSortWriteBackListBeforeWrite()) {
					class compare implements Comparator<Map.Entry<Long, Object[]>> {
						public int compare(Map.Entry<Long, Object[]> a, Map.Entry<Long, Object[]> b) {
							long diff = a.getKey() - b.getKey();
							if (diff < 0)
								return 1;
							else if (diff > 0)
								return -1;
							return 0;
						}
						public boolean equals(Map.Entry<Long, Object[]> a, Map.Entry<Long, Object[]> b) {
							return a.getKey() == b.getKey();
						}
					}

					long sortStart = System.currentTimeMillis();
					Collections.sort(kvlist, new compare());
					totalSortTime.addAndGet(System.currentTimeMillis() - sortStart);
				}

//System.out.println("write");
				long writeStart = System.currentTimeMillis();
				for(int processLoop=0; processLoop < nToDo; processLoop++) {
					Map.Entry<Long, Object[]> kv = kvlist.get(processLoop);
					try {
						byte [] data = (byte [])kv.getValue()[0];
						byte [] hash = (byte [])kv.getValue()[1];
						putLow(kv.getKey(), data, hash);
					}
					catch(Exception e) { // OK
						Log.log(LogLevel.LOG_EMERG, "DWC Exception in delayed write thread! (" +  what + ") " + e);
						Log.log(LogLevel.LOG_EMERG, "DWC data will be re-queued");
						Log.showException(e);

						try {
							queueDirtyBlock(kv.getKey(), (byte [])kv.getValue()[0]);
						}
						catch(InterruptedException ie) {
							Log.log(LogLevel.LOG_EMERG, "DWC failed to re-queue data after exception during put: data is lost! " + ie);
							Log.showException(ie);
						}
					}
				}
//System.out.println("finished");
				totalWriteTime.addAndGet(System.currentTimeMillis() - writeStart);
			}

			if (forceWriteAll) {
				Log.log(LogLevel.LOG_DEBUG, "DWC Finished force write all");
				fwa = false;
			}

			nFlushed.addAndGet(1);

			lastWrite = System.currentTimeMillis();
		}

		doNotifyAGet();
	}

	public void run() {
		boolean flushMore = false;
		for(;;) {
			boolean forceWriteAll = false;

			if (!flushMore && !forceWriteAll && !fwa) {
				try {
					doWaitForPut(wcp.getDelayedWriteFlushInterval());
				}
				catch(InterruptedException ie) {
					Log.log(LogLevel.LOG_INFO, "DWC Delayed write sleep interrupted (" + what + ")");
					forceWriteAll = true;
				}
			}
			else {
				// Log.log(LogLevel.LOG_INFO, "DWC " + flushMore + " " + forceWriteAll);
			}
			// Log.log(LogLevel.LOG_DEBUG, "DWC write-thread woke up");

			writeLock();

			if (fwa == true)
				forceWriteAll = true;

			totalFill.addAndGet(delayedWriteList.size());
			nMeasures.addAndGet(1);

			try {
				writeCacheToDisk(forceWriteAll, emergencyFlush.get());
			}
			catch(Exception e) { // OK
				Log.log(LogLevel.LOG_EMERG, "DWC flush thread threw exception: " + e);
				Log.showException(e);
			}
			emergencyFlush.set(false);

			int nBlocks;
			flushMore = false;
			lastN = nBlocks = delayedWriteList.size();
			if (nBlocks >= wcp.getForceFlushThreshold())
				flushMore = true;

			writeUnlock();

			if (exitAfterFlush.get()) {
				Log.log(LogLevel.LOG_INFO, "DWC " + what + " exit after flush " + nBlocks);
				if (nBlocks == 0)
					break;

				fwa = true;
			}
		}

		Log.log(LogLevel.LOG_INFO, "DWC " + what + " thread ended");
	}

	public void close() {
		int nAvailable = delayedWriteList.size();
		if (nAvailable > 0) {
			Log.log(LogLevel.LOG_INFO, "DWC " + what + ": please wait while flushing delayed write cache (" + nAvailable + " blocks)");
		}

		exitAfterFlush.set(true);

		try {
			delayWriteThread.join();
		}
		catch(InterruptedException ie) {
		}

		Log.log(LogLevel.LOG_INFO, "DelayedWriteCache thread ended with " + delayedWriteList.size() + " blocks left");
	}
}
