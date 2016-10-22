/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.cache.*;
import com.vanheusden.BlackHole.storage.files.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public abstract class Storage extends Thread {
	final static boolean testMode = false;
	TableOnDisk testModeSectorHash = null, testModeAction = null;
	Bitmap testModeInUse = null;
	//
	boolean concurrentRead = false;
	static long nrRead = 0;
	static boolean exitOnClose = false;
	long fileSize = -1;
	int blockSize = -1;
	long nBlocks = -1;
	int flushDelay = 5000;
	long previousDelayTs = 0;
	ReentrantReadWriteLock readWriteLock;
	Lock rl;
	Lock wl;
	long lastSectorWritten = -1;
	int lastWriteAction = -1;
	Thread lastWriteThread = null;
	String dataPath = null;
	String relocateJournalFilename = "relocate-journal.dat";
	boolean unsafe = false;
	//
	DelayedWriteCache dwc = null;
	WritecacheParameters wcp = null;
	ReadCache rc = null;
	//
	Thread cleanThread = null;
	//
	AtomicLong nReads = new AtomicLong(), totalReadTime = new AtomicLong(), nReadFromWriteCache = new AtomicLong(), nReadFromReadCache = new AtomicLong(), nReadFromDisk = new AtomicLong();
	AtomicLong nWrites = new AtomicLong(), totalWriteTime = new AtomicLong(), totalLockWriteTime = new AtomicLong();

	abstract public void delete() throws IOException, VHException, SQLException;

	public Storage(String dataPath, ReadcacheParameters rcp, WritecacheParameters wcp, long fileSize, int blockSize, HashType hashType) throws IOException, VHException, NoSuchAlgorithmException, SQLException {
		readWriteLock = new ReentrantReadWriteLock(true);
		rl = readWriteLock.readLock();
		wl = readWriteLock.writeLock();

		Hasher.getInstance(hashType);

                if (rcp != null)
                        Log.log(LogLevel.LOG_INFO, "Read cache enabled: " + rcp.toString());
                if (wcp != null) {
                        Log.log(LogLevel.LOG_INFO, "Write cache enabled: " + wcp.toString());

			if (unsafe)
				Log.log(LogLevel.LOG_WARN, "Note: disk-flush=true is ignored due to delayed write cache!");
		}

                this.fileSize = fileSize;
                this.blockSize = blockSize;

		nBlocks = fileSize / blockSize;

		cleanThread = new Thread(this, "Storage: clean-up thread");
		cleanThread.start();

		this.wcp = wcp;
                if (wcp != null)
                        dwc = new DelayedWriteCacheStorage(dataPath + "/Storage", this, wcp);

		if (rcp != null)
			rc = new ReadCache(rcp, blockSize, "Storage");

		if (testMode) {
			testModeSectorHash = new TableOnDisk(dataPath + "/testMode-sectorHash.dat", 16, nBlocks, null);
			testModeInUse = new Bitmap(dataPath + "/testMode-bitmap.dat", nBlocks);
			testModeAction = new TableOnDisk(dataPath + "/testMode-action.dat", 1, nBlocks, null);
		}
	}

	public static byte [] compress(CompressionType ct, int level, byte [] in, int blockSize) {
		assert in.length == blockSize;
		assert ct == CompressionType.C_ZLIB;

		Deflater compresser = new Deflater(level);
		compresser.setInput(in);
		compresser.finish();
		byte [] out = new byte[in.length * 2];
		int outLen = compresser.deflate(out);
		compresser.end();

		byte [] finalOut = new byte[outLen];
		System.arraycopy(out, 0, finalOut, 0, outLen);

		return finalOut;
	}

	public static byte [] uncompress(CompressionType ct, byte [] in, int blockSize) throws DataFormatException {
		assert ct == CompressionType.C_ZLIB;

		Inflater decompresser = new Inflater();
		decompresser.setInput(in);

		byte[] out = new byte[blockSize];
		int outLength = decompresser.inflate(out);
		assert outLength == blockSize;

		assert decompresser.finished();
		decompresser.end();

		return out;
	}

	public abstract void growDatastore(long newSize) throws IOException;

        public abstract boolean fsck() throws IOException, VHException, SQLException, BadPaddingException, IllegalBlockSizeException, DataFormatException;

	public void setUnsafe(boolean u) {
		unsafe = u;
		if (unsafe)
			Log.log(LogLevel.LOG_NOTICE, "Note: won't force blocks to disk");
	}

	protected void readLock() {
		rl.lock();
	}

	protected void readUnlock() {
		rl.unlock();
	}

	protected void writeLock() {
		wl.lock();
	}

	protected void writeUnlock() {
		wl.unlock();
	}

	protected boolean checkWriteLocked() {
		return readWriteLock.isWriteLocked();
	}

	protected boolean checkReadLocked() {
		return readWriteLock.getReadLockCount() > 0;
	}

	public long getSize() {
		return fileSize;
	}

	public int getBlockSize() {
		return blockSize;
	}

	abstract public void unmapSector(long sector) throws IOException, VHException, SQLException;

	public void unmapLun(SectorMapper sm, int lun) throws IOException, VHException, SQLException {
		long startTs = System.currentTimeMillis();

		long nBlocks = sm.getLunSize(lun) / blockSize;

		startTransaction();

		for(long sector=0; sector<nBlocks; sector++) {
			unmapSector(sm.getMapOffset(lun, sector));

			long nowTs = System.currentTimeMillis();
			if (nowTs - startTs >= flushDelay) {
				commitTransaction();
				startTransaction();

				startTs = nowTs;
			}
		}

		commitTransaction();
	}

	abstract public void startTransaction() throws IOException, VHException, SQLException;

	abstract public void commitTransaction() throws IOException, VHException, SQLException;

	abstract protected byte [] calcHash(byte [] data);

	abstract protected Double getPercentageBlockUseDecrease() throws IOException, VHException, SQLException;

	abstract void putBlockLow(long sectorNr, byte [] data, byte [] hash ) throws VHException, BadPaddingException, IllegalBlockSizeException, DataFormatException, FileNotFoundException, IOException, SQLException;

	void putBlockLow(long sectorNr, byte [] data) throws VHException, BadPaddingException, IllegalBlockSizeException, DataFormatException, FileNotFoundException, IOException, SQLException {
		putBlockLow(sectorNr, data, calcHash(data));
	}

	public void putBlock(long sectorNr, byte [] data, boolean forceToDisk) throws IOException, InterruptedException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		putBlock(sectorNr, data, 0, forceToDisk);
	}

	abstract protected byte [] readBlockLow(long sectorNr) throws IOException, VHException, DataFormatException, IllegalBlockSizeException, BadPaddingException, SQLException;

	private byte [] mergeBlock(long sectorNr, byte [] in, int offset) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		assert (offset + in.length) <= blockSize;
		byte [] dummy = null;

		if (wcp != null) {
			dummy = dwc.getBlock(sectorNr);
		}
		if (dummy == null)
			dummy = readBlockLow(sectorNr);

		dummy = Utils.arrayDuplicate(dummy);
		Utils.arrayCopy(dummy, offset, in);

		return dummy;
	}

	public void putBlock(long sectorNr, byte [] data, int offset, boolean forceToDisk) throws IOException, InterruptedException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		assert data.length <= blockSize;

		nWrites.addAndGet(1);

		assert (sectorNr * blockSize) < fileSize;
		// Log.log(LogLevel.LOG_DEBUG, "putBlock " + sectorNr);

		long start = System.currentTimeMillis();
		writeLock();
		totalLockWriteTime.addAndGet(System.currentTimeMillis() - start);

		// verify if the delayed write cache is almost or completely full
		// in the first case, trigger its flush-thread to start flushing asap
		// in the second case: immediately start flushing
		// must be called first in putBlock as it can release the lock
		if (wcp != null) {
			boolean [] cf = dwc.checkFlush();

			if (cf[1]) {
				writeUnlock();
				dwc.forceFlush();
				writeLock();
			}
		}

		// if this write is a partial write, e.g. halfway a sector, then merge it
		// with a complete sector
		if (offset != 0 || data.length != blockSize) {
			data = mergeBlock(sectorNr, data, offset);
		}

		if (testMode) {
			testModeSectorHash.storeElement(sectorNr, calcHash(data), -1);
			testModeInUse.setBit(sectorNr, true);
		}

		if (rc != null)
			rc.update(sectorNr, data);

		if (wcp != null && forceToDisk == false) {
			dwc.queueDirtyBlock(sectorNr, data);
		}
		else {
			putBlockLow(sectorNr, data);

			if (forceToDisk && wcp != null)
				dwc.forget(sectorNr);
		}
		writeUnlock();

		totalWriteTime.addAndGet(System.currentTimeMillis() - start);
	}

	abstract public void testModeVerifyData(long sectorNr, byte [] data, int readFrom) throws IOException, VHException, SQLException;

	public byte [] readBlock(long sectorNr) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		nReads.addAndGet(1);
		long start = System.currentTimeMillis();

		assert (sectorNr * blockSize) < fileSize;
		byte [] data = null;

		// Log.log(LogLevel.LOG_DEBUG, "readBlock " + sectorNr);

		nrRead++;

		boolean locked = false;

		if (wcp != null) {
			readLock();

			data = dwc.getBlock(sectorNr);
			if (data != null)
				nReadFromWriteCache.addAndGet(1);

			readUnlock();
		}
		if (data == null && rc != null) {
			readLock();

			data = rc.get(sectorNr);
			if (data != null)
				nReadFromReadCache.addAndGet(1);

			readUnlock();
		}
		if (data == null) {
			if (concurrentRead && !testMode)
				readLock();
			else
				writeLock();

			data = readBlockLow(sectorNr);
			if (rc != null)
				rc.add(sectorNr, data);
			if (data != null)
				nReadFromDisk.addAndGet(1);

			testModeVerifyData(sectorNr, data, 3);

			if (concurrentRead && !testMode)
				readUnlock();
			else
				writeUnlock();
		}

		totalReadTime.addAndGet(System.currentTimeMillis() - start);

		return data;
	}

	public void flushAll() throws IOException, VHException, SQLException {
		// Log.log(LogLevel.LOG_INFO, "Commit data");
		if (dwc != null) {
			Log.log(LogLevel.LOG_INFO, "Flushing delayed write cache");
			writeLock();
			dwc.flush();
			writeUnlock();
		}
	}

	public void run() {
		Log.log(LogLevel.LOG_DEBUG, "Storage cleanup thread started");

		for(;;) {
			try {
				long delay = Math.min(5000, Math.max(125, flushDelay));
				// Log.log(LogLevel.LOG_DEBUG, "Sleeping for " + delay + "ms");
				Thread.sleep(delay);
			}
			catch(InterruptedException ie) {
				Log.log(LogLevel.LOG_WARN, "Clean thread got interrupted");
				break;
			}
		}
	}

	public void closeStorageBackend() throws IOException, VHException, SQLException {
		flushAll();

		if (wcp != null) {
			Log.log(LogLevel.LOG_INFO, "Closing delayed write cache");
			dwc.close();
			Log.log(LogLevel.LOG_INFO, "DWC closed");
		}

		if (cleanThread != null) {
			Log.log(LogLevel.LOG_INFO, "Waiting for clean thread to end");
			Utils.intJoinIgnoreEx(cleanThread);
			Log.log(LogLevel.LOG_INFO, "Clean thread finished");
		}

		Log.log(LogLevel.LOG_INFO, "Finished closing storage");
	}
}
