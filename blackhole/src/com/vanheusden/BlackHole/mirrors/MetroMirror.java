/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.mirrors;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.sockets.*;
import com.vanheusden.BlackHole.storage.*;
import com.vanheusden.BlackHole.storage.files.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class MetroMirror extends Mirror implements Runnable {
	static String version = "$Id: MetroMirror.java 606 2013-07-06 22:07:22Z folkert $";
	Socket s;
	MyClientSocket mcs;
	boolean connectSTE, connectIE, connectMsg;
	long fileSize = -1;
	long handle = 0;
	boolean canDiscard = false;
	//
	long backendSize = -1;
	int blockSize = -1;
	boolean disconnectedBlock = true;
	//
	Bitmap reconnectSend = null;
	long reconnectSendNBlocks = -1;
	Thread syncThread = null;
	//
	int timeout = 1000;	// 1s

	public MetroMirror(String host, int port, String mmJournalBitmap, int lun, long backendSize, int blockSize, boolean disconnectedBlock, SectorMapper sm) throws IOException {
		this.host = host;
		this.port = port;
		this.backendSize = backendSize;
		this.blockSize = blockSize;
		this.disconnectedBlock = disconnectedBlock;
		this.filenameBase = mmJournalBitmap;
		this.lun = lun;
		this.sm = sm;

		empty = new byte[blockSize];

		resyncProgressThreshold = backendSize / 10;

		if (!disconnectedBlock) {
			reconnectSendNBlocks = backendSize / blockSize;
			reconnectSend = new Bitmap(mmJournalBitmap, reconnectSendNBlocks);
		}

		loadSyncState();
	}

	protected void triggerStart() {
		Log.log(LogLevel.LOG_DEBUG, "MM start triggered");
		if (resync) {
			syncThread = new Thread(this, "MetroMirror resync(1)");
			syncThread.start();
		}
	}

	public void resyncAllData() {
		resyncSem.acquireUninterruptibly();
		if (resync == true)
			Log.log(LogLevel.LOG_WARN, "MM were already resyncing!");
		else {
			Log.log(LogLevel.LOG_INFO, "MM start resyncing");
			resync = true;
			resyncOffset = 0;
			storeSyncOffset();
			syncThread = new Thread(this, "MetroMirror resync(2)");
			syncThread.start();
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

	public void run() {
		Log.log(LogLevel.LOG_INFO, "MM starting complete resync");

		long resyncStartedAt = System.currentTimeMillis();
		long lastMetaStore = 0;

		while(resyncOffset < (backendSize)) {
			byte [] data;

			try {
				data = sp.readBlock(sm.getMapOffset(lun, resyncOffset) / blockSize);

				if (transmitBlocked(resyncOffset, data) == false) {
					Log.log(LogLevel.LOG_WARN, "MM resync problem, reconnecting & retrying");
					try {
						Thread.sleep(100);
					}
					catch(InterruptedException ie) {
						Log.log(LogLevel.LOG_WARN, "MM resync: interrupted, aborting");
						break;
					}
					reconnect();
					continue;
				}
			}
			catch(Exception e) { // OK
				Log.log(LogLevel.LOG_CRIT, "MM exception during resync! Aborting! Reason: " + e);
				break;
			}

			resyncSem.acquireUninterruptibly();
			resyncOffset += blockSize;

			long progress = resyncOffset / resyncProgressThreshold;
			if (progress != resyncLastProgress) {
				long bytesLeft = backendSize - resyncOffset;
				double runTime = (System.currentTimeMillis() - resyncStartedAt) / 1000.0;
				long MBps = (long)((resyncOffset / (1024.0 * 1024.0)) / Math.max(1, runTime));
				int eta = (int)Math.ceil((bytesLeft / (1024.0 * 1024.0)) / (double)MBps);
				Log.log(LogLevel.LOG_INFO, "MM resync progress: " + (bytesLeft / blockSize) + " blocks left (" + (bytesLeft / (1024 * 1024)) + "MB), " + MBps + "MB/s, run time: " + runTime + "s, eta: " + eta + "s");
				resyncLastProgress = progress;
			}

			long now = System.currentTimeMillis();
			if ((now - lastMetaStore) > 4000) {
				storeSyncOffset();

				lastMetaStore = now;
			}

			resyncSem.release();
		}

		resyncSem.acquireUninterruptibly();
		resync = false;
		deleteSyncOffset();
		resyncSem.release();

		Log.log(LogLevel.LOG_INFO, "MM finished complete resync");
	}

	public void close() throws IOException {
		resyncSem.acquireUninterruptibly();
		if (resync == true)
			Log.log(LogLevel.LOG_WARN, "MM WARNING: not finished syncing! " + ((backendSize - resyncOffset) / (1024 * 1024)) + "MB left"); 
		resyncSem.release();

		if (!disconnectedBlock) {
			if (reconnectSend.findBit1(true, reconnectSendNBlocks) != -1)
				Log.log(LogLevel.LOG_WARN, "MM WARNING some blocks were not transmitted after disconnection! (" + reconnectSend.getNBits1() + ")");
		}

		if (syncThread != null) {
			syncThread.interrupt();
			Log.log(LogLevel.LOG_INFO, "MM waiting for syncthread to end");
			try {
				syncThread.join();
			}
			catch(InterruptedException ie) {
				Log.log(LogLevel.LOG_INFO, "MM interrupted");
			}
			Log.log(LogLevel.LOG_INFO, "MM syncthread ended");
		}

		if (reconnectSend != null)
			reconnectSend.close();

		disconnect();
	}

	public void flushBacklog() throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		if (s == null || s.isConnected() == false)
			reconnect();

		if (!disconnectedBlock && (s != null && s.isConnected()))
			doFlushBacklog();
	}

	void reconnect() throws IOException, VHException {
		disconnect();

		try {
			if (!connectMsg) {
				Log.log(LogLevel.LOG_INFO, "MM reconnecting to " + host + ":" + port);
				connectMsg = true;
			}

			s = new Socket();
			s.connect(new InetSocketAddress(host, port), timeout);

			if (s.isConnected()) {
				mcs = new MyClientSocket(s);
				mcs.setTcpNoDelay();

				initSession();

				Log.log(LogLevel.LOG_INFO, "MM connected");

				connectMsg = connectSTE = connectIE = false;
			}
			else {
				disconnect();
			}
		}
		catch(SocketTimeoutException ste) { if (!connectSTE) { Log.log(LogLevel.LOG_WARN, "MM connecting timeout " + ste); connectSTE = true; } }
		catch(IOException ie) { if (!connectIE) { Log.log(LogLevel.LOG_WARN, "MM connecting failure: " + ie); connectIE = true; } }
	}

	void initSession() throws IOException, VHException {
		Log.log(LogLevel.LOG_DEBUG, "MM init session");
		byte [] b8 = new byte[8];
		mcs.getBytes(b8); // NBDMAGIC
		String magic = new String(b8);
		Log.log(LogLevel.LOG_DEBUG, "MM NBD magic: " + magic);
		if (magic.equals("NBDMAGIC") == false)
			throw new VHException("Invalid magic, expecting NBDMAGIC and got " + magic);
		mcs.getBytes(b8); // some other bytestring

		fileSize = mcs.getU64(); // file size
		Log.log(LogLevel.LOG_DEBUG, "MM NBD size: " + fileSize);

		byte [] flagsBytes = new byte[4];
		mcs.getBytes(flagsBytes);
		int flags = Utils.byteArrayToInt(flagsBytes);
		canDiscard = false;
		if (((flags & 1) == 1) && ((flags & 32) == 32))
			canDiscard = true;

		byte [] b124 = new byte[124];
		mcs.getBytes(b124); // padding
	}

	public void disconnect() throws IOException {
		Log.log(LogLevel.LOG_DEBUG, "MM close session");
		if (mcs != null) {
			mcs.flush();
			mcs.closeSocket();
		}
		mcs = null;
		s = null;
	}

	public boolean isSynchronous() {
		return true;
	}

	public void doFlushBacklog() throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		boolean backlog = false;
		long blStart = System.currentTimeMillis(), nBlocksBL = 0;

		for(;;) {
			long blockNr = reconnectSend.findBit1(reconnectSendNBlocks);
			if (blockNr == -1)
				break;

			if (!backlog) {
				Log.log(LogLevel.LOG_DEBUG, "MM transmitting backlog");
				backlog = true;
			}

			nBlocksBL++;

			long backlogOffset = blockNr * blockSize;
			byte [] blData = sp.readBlock(sm.getMapOffset(lun, backlogOffset) / blockSize);

			Log.log(LogLevel.LOG_DEBUG, "MM transmit block " + blockNr);

			if (transmitBlockLow(backlogOffset, blData)) {
				assert reconnectSend.getBit(blockNr) == true;
				reconnectSend.setBit(blockNr, false);
			}
		}

		if (backlog) Log.log(LogLevel.LOG_INFO, "MM finished transmitting backlog (" + nBlocksBL + " blocks), took: " + ((System.currentTimeMillis() - blStart) / 1000.0) + "s");
	}

	public void queue(long offset, byte [] data) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		if (disconnectedBlock)
			transmitBlocked(offset, data);
		else
			transmitUnblocked(offset, data);
	}

	private boolean transmitBlocked(long offset, byte [] data) throws IOException, VHException {
		long start = System.currentTimeMillis();

		resyncSem.acquireUninterruptibly();

		do {
			for(;;) {
				if (s != null && s.isConnected() == true)
					break;

				Log.log(LogLevel.LOG_INFO, "MM transmit blocked: reconnecting " + ((System.currentTimeMillis() - start) / 1000.0) + "s");

				try {
					Thread.sleep(100);
				}
				catch(InterruptedException ie) {
					return false;
				}

				reconnect();
			}

			Log.log(LogLevel.LOG_DEBUG, "MM transmit block " + (offset / blockSize));
		}
		while(!transmitBlockLow(offset, data));

		resyncSem.release();

		return true;
	}

	public long getPendingBlockCount() throws IOException {
		resyncSem.acquireUninterruptibly();
		long copy = 0;
		if (reconnectSend != null)
			copy = reconnectSend.getNBits1();
		resyncSem.release();
		return copy;
	}

	private boolean transmitUnblocked(long offset, byte [] data) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		boolean reconnected = false;

		resyncSem.acquireUninterruptibly();

		reconnectSend.setBit(offset / blockSize, true);

		if (s == null || s.isConnected() == false) {
			reconnect();
			reconnected = true;
		}

		if (s != null && s.isConnected()) {
			if (reconnected)
				doFlushBacklog();

			if(!transmitBlockLow(offset, data))
				return false;

			assert reconnectSend.getBit(offset / blockSize) == true;
			reconnectSend.setBit(offset / blockSize, false);
		}

		resyncSem.release();

		return true;
	}

	synchronized public boolean transmitBlockLow(long offset, byte [] data) throws IOException, VHException {
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

			return true;
		}
		catch(SocketException se) { Log.log(LogLevel.LOG_WARN, "MM transmitBlockLow() socket exception " + se); }
		catch(IOException ie) { Log.log(LogLevel.LOG_WARN, "MM transmitBlockLow() IO exception " + ie); }

		disconnect();

		return false;
	}

	public boolean transmitBlock(long offset, byte [] data) {
		assert false;
		return false;
	}
}
