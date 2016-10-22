/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.mirrors;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.cache.*;
import com.vanheusden.BlackHole.storage.*;
import com.vanheusden.BlackHole.storage.files.*;
import com.vanheusden.BlackHole.sockets.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.Semaphore;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class GlobalMirror extends Mirror implements Runnable {
	static String version = "$Id: GlobalMirror.java 606 2013-07-06 22:07:22Z folkert $";
	Socket s;
	MyClientSocket mcs;
        private final Semaphore sem = new Semaphore(1, true);
	Dummy myMonitorObject = new Dummy();
	//
	Bitmap mmjb;
	int blockSize = -1;
	long totalNBlocks = -1;
	long backendSize = -1;
	//
	long fileSize = -1;
	long handle = 0;
	boolean canDiscard = false;
	//
	boolean discNotified = false;
	boolean bitmapSearchFromStart = true;

        public void lock() {
                sem.acquireUninterruptibly();
        }

        public void unlock() {
                sem.release();
        }

	public GlobalMirror(String host, int port, String mmJournalBitmap, int lun, long backendSize, int blockSize, SectorMapper sm) throws IOException {
		this.host = host;
		this.port = port;

		this.lun = lun;
		this.sm = sm;
		this.backendSize = backendSize;
		totalNBlocks = backendSize / blockSize;
		mmjb = new Bitmap(mmJournalBitmap, totalNBlocks);
		this.blockSize = blockSize;

		empty = new byte[blockSize];

		this.filenameBase = mmJournalBitmap;

		resyncProgressThreshold = backendSize / 10;

		// reconnect();
		Log.log(LogLevel.LOG_DEBUG, "GM initialized");

		loadSyncState();
	}

	public long getPendingBlockCount() throws IOException {
		lock();
		long copy = mmjb.getNBits1();
		unlock();
		return copy;
	}

	protected void triggerStart() {
		// start resync and flush of delayed blocks
		Log.log(LogLevel.LOG_DEBUG, "GM start triggered");
		doNotify();
	}

	public void resyncAllData() {
		resyncSem.acquireUninterruptibly();
		if (resync == true)
			Log.log(LogLevel.LOG_INFO, "GM were already resyncing!");
		else {
			Log.log(LogLevel.LOG_INFO, "GM start resyncing");
			resync = true;
			resyncOffset = 0;
			resyncStartedAt = System.currentTimeMillis();
			storeSyncOffset();
			doNotify();
		}
		resyncSem.release();
	}

	public boolean isSyncing() {
		boolean copy;
		resyncSem.acquireUninterruptibly();
		copy = resync;
		resyncSem.release();
		return copy;
	}

	public long getResyncOffset() {
		long copy;
		resyncSem.acquireUninterruptibly();
		copy = resyncOffset;
		resyncSem.release();
		return copy;
	}

	void reconnect() throws IOException, VHException {
		boolean fail = true;

		try {
			if (discNotified == false) Log.log(LogLevel.LOG_DEBUG, "GM reconnecting to " + host + ":" + port);
			if (s != null)
				s.close();

			s = new Socket(host, port);
			Log.log(LogLevel.LOG_INFO, "GM connected (local port: " + s.getLocalPort() + ")");
			mcs = new MyClientSocket(s);
			mcs.setTcpNoDelay();

			initSession();

			fail = false;
		}
		catch(ConnectException ce) { if (!discNotified) { Log.log(LogLevel.LOG_WARN, "GM reconnect() connect exception " + ce);  discNotified = true; } }
		catch(SocketException se) { if (!discNotified) { Log.log(LogLevel.LOG_WARN, "GM reconnect() socket exception " + se); discNotified = true; } }
		catch(IOException ie) { if (!discNotified) { Log.log(LogLevel.LOG_WARN, "GM reconnect() IO exception " + ie);  discNotified = true; } }

		if (fail) {
			try {
				if (s != null)
					s.close();
			}
			catch(IOException ie2) { }
			finally {
				mcs = null;
				s = null;
			}
		}
	}

	void initSession() throws VHException, IOException {
		Log.log(LogLevel.LOG_DEBUG, "GM init session");
		byte [] b8 = new byte[8];
		mcs.getBytes(b8); // NBDMAGIC
		String magic = new String(b8);
		if (magic.equals("NBDMAGIC") == false)
			throw new VHException("Invalid magic, expecting NBDMAGIC and got " + magic);
		mcs.getBytes(b8); // some other bytestring

		fileSize = mcs.getU64(); // file size

		byte [] flagsBytes = new byte[4];
		mcs.getBytes(flagsBytes);
		int flags = Utils.byteArrayToInt(flagsBytes);
		canDiscard = false;
		if (((flags & 1) == 1) && ((flags & 32) == 32))
			canDiscard = true;

		byte [] b124 = new byte[124];
		mcs.getBytes(b124); // padding
	}

	public boolean isSynchronous() {
		return false;
	}

	public void doWait() throws InterruptedException {
		synchronized(myMonitorObject) {
			myMonitorObject.wait();
		}
	}

	public void doNotify()  {
		synchronized(myMonitorObject){
			myMonitorObject.notify();
		}
	}

        public void flushBacklog() throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		doFlushBacklog();
        }

	private void doFlushBacklog() throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		Log.log(LogLevel.LOG_DEBUG, "GM transmitting pages");
		long n = 0, nFailed = 0;

		boolean dummy = bitmapSearchFromStart;
		bitmapSearchFromStart = false;
		for(;;) {
			lock();
			Log.log(LogLevel.LOG_DEBUG, "GM got lock (flush)");
			long usedPage = mmjb.findBit1(dummy, totalNBlocks);
			Log.log(LogLevel.LOG_DEBUG, "GM used page: " + usedPage);
			if (usedPage == -1) {
				Log.log(LogLevel.LOG_DEBUG, "GM no pages");
				unlock();
				break;
			}

			Log.log(LogLevel.LOG_DEBUG, "GM read block " + usedPage);
			byte [] data = sp.readBlock(sm.getMapOffset(lun, usedPage * blockSize) / blockSize);

			Log.log(LogLevel.LOG_DEBUG, "GM transmit block " + usedPage);
			long offset = usedPage * blockSize;
			if (transmitBlock(offset, data)) {
				assert mmjb.getBit(usedPage) == true;
				mmjb.setBit(usedPage, false);
			}
			else {
				bitmapSearchFromStart = true;
				nFailed++;
			}

			unlock();
			Log.log(LogLevel.LOG_DEBUG, "GM unlocked (flush)");
			n++;
		}

		Log.log(LogLevel.LOG_DEBUG, "GM finished transmitting " + n + " pages");
		if (nFailed > 0)
			Log.log(LogLevel.LOG_WARN, "GM failed to transmit " + nFailed + " pages. will be retried");
	}

	private void doOneBlock(long blockId) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		Log.log(LogLevel.LOG_DEBUG, "GM doOneBlock");

		lock();
		Log.log(LogLevel.LOG_DEBUG, "GM read block " + blockId);
		byte [] data = sp.readBlock(sm.getMapOffset(lun, blockId * blockSize) / blockSize);

		Log.log(LogLevel.LOG_DEBUG, "GM transmit block " + blockId);
		long offset = blockId * blockSize;
		if (transmitBlock(offset, data)) {
			assert mmjb.getBit(blockId) == true;
			mmjb.setBit(blockId, false);
		}
		else
			bitmapSearchFromStart = true;

		unlock();

		Log.log(LogLevel.LOG_DEBUG, "GM finished doOneBlock");
	}

	public void queue(long offset, byte [] notUsed) throws IOException {
		Log.log(LogLevel.LOG_DEBUG, "queueing offset " + offset);

		long blockId = offset / blockSize;

		lock();
		try {
			mmjb.setBit(blockId, true);
		}
		finally {
			unlock();
		}

		Log.log(LogLevel.LOG_DEBUG, "offset " + offset + " queued");

		doNotify();
	}

	public void close() throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
                resyncSem.acquireUninterruptibly();
                if (resync == true)
                        Log.log(LogLevel.LOG_WARN, "GM WARNING: not finished syncing! " + ((backendSize - resyncOffset) / (1024 * 1024)) + "MB left");
                resyncSem.release();

		if (mmjb.findBit1(true, totalNBlocks) != -1)
			Log.log(LogLevel.LOG_WARN, "GM WARNING some blocks were not transmitted after disconnection! (" + mmjb.getNBits1() + ")");

		doFlushBacklog();
		disconnect();
		assert mmjb.findBit1(true, totalNBlocks) == -1;
		mmjb.close();
	}

	void disconnect() throws IOException {
		try {
			if (mcs != null) {
				mcs.flush();
				mcs.closeSocket();
			}

			if (s != null)
				s.close();
		}
		finally {
			mcs = null;
			s = null;
		}
	}

	public void run() {
		Log.log(LogLevel.LOG_DEBUG, "GM thread started");

		long lastMetaStore = 0;

		for(;;) {
			try {
				doFlushBacklog();

				if (!resync) {
					doWait();
					// assert mmjb.findBit1(false, totalNBlocks) != -1;
				}
				else {
					resyncSem.acquireUninterruptibly();

					doOneBlock(resyncOffset / blockSize);
					resyncOffset += blockSize;

					long progress = resyncOffset / resyncProgressThreshold;
					if (progress != resyncLastProgress) {
						long bytesLeft = backendSize - resyncOffset;
						double runTime = (System.currentTimeMillis() - resyncStartedAt) / 1000.0;
						long MBps = (long)((resyncOffset / (1024.0 * 1024.0)) / Math.max(1, runTime));
						int eta = (int)Math.ceil((bytesLeft / (1024.0 * 1024.0)) / (double)MBps);
						Log.log(LogLevel.LOG_INFO, "GM resync progress: " + (bytesLeft / blockSize) + " blocks left (" + (bytesLeft / (1024 * 1024)) + "MB), " + MBps + "MB/s, run time: " + runTime + "s, eta: " + eta + "s");
						resyncLastProgress = progress;
					}

					if (resyncOffset == (totalNBlocks * blockSize)) {
						deleteSyncOffset();
						resync = false;
						Log.log(LogLevel.LOG_INFO, "GM finished resyncing");
					}

					long now = System.currentTimeMillis();
					if ((now - lastMetaStore) > 4000) {
						storeSyncOffset();

						lastMetaStore = now;
					}

					resyncSem.release();
				}
			}
			catch(InterruptedException ie) {
				Log.log(LogLevel.LOG_INFO, "GM thread interrupted");
				break;
			}
			catch(SocketException se) { Log.log(LogLevel.LOG_WARN, "GM run() socket exception " + se); }
			catch(IOException ie) { Log.log(LogLevel.LOG_WARN, "GM run() IO exception " + ie); }
			catch(Exception e) { // OK
				Log.showException(e);
			}
		}
	}

	public boolean transmitBlock(long offset, byte [] data) throws IOException, VHException {
		if (s == null || !s.isConnected()) {
			disconnect();
			reconnect();
		}

		if (s != null && s.isConnected()) {
			try {
				boolean all0x00 = false;
				if (canDiscard)
					all0x00 = isAll0x00(data);

				byte [] output = null;
				if (all0x00)
					output = new byte[28];
				else
					output = new byte[28 + data.length];
				Utils.putU32(output, 0, 0x25609513);	// magic
				Utils.putU32(output, 4, all0x00 ? 4 : 1);		// DISCARD / WRITE
				Utils.putU64(output, 8, ++handle);	// handle
				Utils.putU64(output, 16, offset);	// offset
				Utils.putU32(output, 24, data.length);	// len
				if (!all0x00)
					Utils.arrayCopy(output, 28, data);	// data
				mcs.putBytes(output);
				mcs.flush();

				int magic = mcs.getU32();
				if (magic != 0x67446698)	// magic
					throw new VHException("Invalid magic in reply! " + magic);
				int errorCode = mcs.getU32();
				if (errorCode != 0)
					throw new VHException("Error code != 0! " + errorCode);
				long returnedHandle = mcs.getU64();
				if (returnedHandle != handle)
					throw new VHException("Expected handle " + handle + ", got " + returnedHandle);

				Log.log(LogLevel.LOG_DEBUG, "sent ok");
				discNotified = false;
				return true;
			}
			catch(SocketException se) { if (!discNotified) { Log.log(LogLevel.LOG_WARN, "GM transmitBlock() socket exception " + se); discNotified = true; } }
			catch(IOException ie) { if (!discNotified) { Log.log(LogLevel.LOG_WARN, "GM transmitBlock() IO exception " + ie);  discNotified = true; }}
		}

		return false;
	}
}
