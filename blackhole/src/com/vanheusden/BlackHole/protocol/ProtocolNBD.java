/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.protocol;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.mirrors.*;
import com.vanheusden.BlackHole.snapshots.*;
import com.vanheusden.BlackHole.sockets.*;
import com.vanheusden.BlackHole.storage.*;
import com.vanheusden.BlackHole.stats.*;

import java.io.IOException;
import java.net.SocketException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class ProtocolNBD extends Protocol {
	static String version = "$Id: ProtocolNBD.java 606 2013-07-06 22:07:22Z folkert $";
	SectorMapper sm;
	int lun;
	MirrorManager mirrorMan;
	SnapshotManager snapMan;
	static AtomicLong flushDelay = new AtomicLong(0);
        static AtomicLong totalBytesWritten = new AtomicLong();
        static AtomicLong totalBytesRead = new AtomicLong();
        static AtomicLong totalIdleTime = new AtomicLong();
        static AtomicLong totalProcessingTime = new AtomicLong();
        static AtomicLong totalNCommands = new AtomicLong(), totalNRead = new AtomicLong(), totalNWrite = new AtomicLong();
        static AtomicLong nSessions = new AtomicLong();
        static AtomicLong totalSessionsLength = new AtomicLong();
	static AtomicLong totalReadTime = new AtomicLong();
	static AtomicLong totalWriteTime = new AtomicLong();
	long previousDelayTs = 0;
	boolean assumeBarriers = false;
	long transactionSizeLimit = 512 * 1024 * 1024;

        public ProtocolNBD(SectorMapper sm, int lun, Storage storage, MirrorManager mirrorMan, SnapshotManager snapMan, MyClientSocket mcs, boolean assumeBarriers) throws IOException {
		this.sm = sm;
		this.lun = lun;
                this.storage = storage;
                this.mcs = mcs;
		this.mirrorMan = mirrorMan;
		this.snapMan = snapMan;
		this.assumeBarriers = assumeBarriers;

		mcs.setTcpNoDelay();

		if (stats == null) {
			stats = Stats.getInstance();
			stats.add(new StatsElementCounter(totalBytesRead, true, "ProtocolNBD: total bytes read"));
			stats.add(new StatsElementCounter(totalBytesWritten, true, "ProtocolNBD: total bytes written"));
			stats.add(new StatsElementAverage(totalBytesRead, totalNRead, StatsOutputType.FLOAT, true, "ProtocolNBD: average bytes read per request"));
			stats.add(new StatsElementAverage(totalBytesWritten, totalNWrite, StatsOutputType.FLOAT, true, "ProtocolNBD: average bytes written per request"));
			stats.add(new StatsElementCounter(totalIdleTime, false, "ProtocolNBD: total idle time (ms)"));
			stats.add(new StatsElementAverage(totalIdleTime, totalNCommands, StatsOutputType.FLOAT, false, "ProtocolNBD: average idle time between each command (ms)"));
			stats.add(new StatsElementCounter(totalProcessingTime, false, "ProtocolNBD: total processing time (ms)"));
			stats.add(new StatsElementAverage(totalProcessingTime, totalNCommands, StatsOutputType.FLOAT, false, "ProtocolNBD: average processing time for each command (ms)"));
			stats.add(new StatsElementCounter(totalReadTime, false, "ProtocolNBD: total read time (ms)"));
			stats.add(new StatsElementAverage(totalReadTime, totalNRead, StatsOutputType.FLOAT, false, "ProtocolNBD: average read time (ms)"));
			stats.add(new StatsElementCounter(totalWriteTime, false, "ProtocolNBD: total write time (ms)"));
			stats.add(new StatsElementAverage(totalWriteTime, totalNWrite, StatsOutputType.FLOAT, false, "ProtocolNBD: average write time (ms)"));
		}
        }

	protected void initSession() throws IOException {
		Log.log(LogLevel.LOG_DEBUG, "Init session");

		Log.log(LogLevel.LOG_DEBUG, "\"password\"");
		byte passwordMsg[] = { 'N', 'B', 'D', 'M', 'A', 'G', 'I', 'C' };
		mcs.putBytes(passwordMsg);

		Log.log(LogLevel.LOG_DEBUG, "\"magic\"");
		byte magicMsg[] = { 0x00, 0x00, 0x42, 0x02, (byte)0x81, (byte)0x86, 0x12, 0x53 };
		mcs.putBytes(magicMsg);

		long fileSize = sm.getLunSize(lun);
		Log.log(LogLevel.LOG_DEBUG, "\"storage size\": " + fileSize);
		byte storageSizeMsg [] = {
				(byte)(fileSize >> 56),
				(byte)(fileSize >> 48),
				(byte)(fileSize >> 40),
				(byte)(fileSize >> 32),
				(byte)(fileSize >> 24),
				(byte)(fileSize >> 16),
				(byte)(fileSize >>  8),
				(byte)(fileSize      ) };
		mcs.putBytes(storageSizeMsg);

		Log.log(LogLevel.LOG_DEBUG, "\"flags\"");
		int flags = 1 + 4 + 8 + 32; // FLAGS, FLUSH, FUA, TRIM
		byte [] flagsBytes = Utils.intToByteArray(flags);
		mcs.putBytes(flagsBytes);

		Log.log(LogLevel.LOG_DEBUG, "\"padding\"");
		byte [] padMsg = new byte[124];
		mcs.putBytes(padMsg);
		mcs.flush();
	}

	protected void sendAck(long handle, int errorCode) throws IOException {
		byte [] output = new byte[16];
		Utils.putU32(output, 0, 0x67446698);
		Utils.putU32(output, 4, errorCode);
		Utils.putU64(output, 8, handle);
		mcs.putBytes(output);
	}

	protected void msgDiscard(long handle, long offset, long len, boolean forceToDisk) throws IOException, InterruptedException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		long orgOffset = offset;
		long orgLen = len;
		int blockSize = storage.getBlockSize();

		if (!assumeBarriers)
			storage.startTransaction();

		byte [] empty = new byte[blockSize];

		while(len > 0) {
			long blockId = offset / blockSize;
			int blockOffset = (int)(offset % blockSize);
			int cur = (int)Math.min(blockSize - blockOffset, len);
			byte [] buffer = null;

			if (blockOffset == 0 && cur == blockSize)
				storage.unmapSector(blockId);
			else {
				assert cur != blockSize;
				Log.log(LogLevel.LOG_DEBUG, "REQUEST SMALLER THAN BLOCKSIZE! cur: " + cur + ", blocksize: " + blockSize);
				byte [] smallEmpty = new byte[cur];
				storage.putBlock(blockId, smallEmpty, blockOffset, forceToDisk);
			}

			len -= cur;
			offset += cur;
		}

		if (!assumeBarriers)
			storage.commitTransaction();

		sendAck(handle, 0);
		mcs.flush();
	}

	protected void msgWrite(long handle, long offset, long len, boolean forceToDisk) throws IOException, InterruptedException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		long orgOffset = offset;
		long orgLen = len;
		int blockSize = storage.getBlockSize();

		if (!assumeBarriers)
			storage.startTransaction();

		while(len > 0) {
			long blockId = offset / blockSize;
			int blockOffset = (int)(offset % blockSize);
			int cur = (int)Math.min(blockSize - blockOffset, len);
			byte [] buffer = null;

			if (blockOffset == 0 && cur == blockSize) {
				buffer = new byte[blockSize];
				mcs.getBytes(buffer);
				storage.putBlock(blockId, buffer, forceToDisk);
			}
			else {
				assert cur != blockSize;
				Log.log(LogLevel.LOG_DEBUG, "REQUEST SMALLER THAN BLOCKSIZE! cur: " + cur + ", blocksize: " + blockSize);
				buffer = new byte[cur];
				mcs.getBytes(buffer);
				storage.putBlock(blockId, buffer, blockOffset, forceToDisk);
			}

			len -= cur;
			offset += cur;
		}

		if (!assumeBarriers)
			storage.commitTransaction();

		sendAck(handle, 0);
		mcs.flush();
	}

	protected void msgRead(long handle, long offset, long len) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		long orgOffset = offset;
		long orgLen = len;
		int blockSize = storage.getBlockSize();

		// Log.log(LogLevel.LOG_DEBUG, "read " + offset + " " + len);

		sendAck(handle, 0);

		while(len > 0) {
			byte [] buffer = storage.readBlock(offset / blockSize);
			int blockOffset = (int)(offset % blockSize);
			int cur = (int)Math.min(blockSize - blockOffset, len);

			Log.log(LogLevel.LOG_DEBUG, "put " + cur);
			if (cur < blockSize) {
				//Log.log(LogLevel.LOG_INFO, "REQUEST SMALLER THAN BLOCKSIZE! cur: " + cur + ", blocksize: " + blockSize);
				mcs.putBytes(buffer, blockOffset, cur);
			}
			else
				mcs.putBytes(buffer);

			len -= cur;
			offset += cur;
		}

		mcs.flush();
	}

	void msgFlush(long handle) throws IOException, VHException, SQLException {
		if (assumeBarriers)
			storage.commitTransaction();

		sendAck(handle, 0);

		if (assumeBarriers)
			storage.startTransaction();
	}

	public void close() throws IOException {
		mcs.closeSocket();
	}

	public void run() {
		long sessionStart = System.currentTimeMillis();
		nSessions.addAndGet(1);

		try {
			boolean usesNewCommandFlush = false, usesNewCommandDiscard = false, usesNewCommandFlushWrite = false;

			initSession();
			Log.log(LogLevel.LOG_DEBUG, " client thread running");

			if (assumeBarriers)
				storage.startTransaction();

			long transactionSize = 0;

			for(;;) {
				long startIdle = System.currentTimeMillis();

				byte [] request = new byte[28];
				mcs.getBytes(request);
				long startProcessing = System.currentTimeMillis();
				totalIdleTime.addAndGet(startProcessing - startIdle);

				int magic = Utils.byteArrayToInt(request, 0);
				if (magic != 0x25609513)
					throw new VHException("Invalid magic " + magic);
				int type   = Utils.byteArrayToInt(request,   4);
				long handle= Utils.byteArrayToLong(request,  8);
				long offset= Utils.byteArrayToLong(request, 16);
				long len   = Utils.byteArrayToInt(request,  24) & 0xffffffffl;

				totalNCommands.addAndGet(1);

				int flags = type >> 16;
				type &= 0xffff;

				// System.out.println("" + offset + " " + len);
				if (type == 1) {	// WRITE
					transactionSize += len;

					long startWrite = System.currentTimeMillis();

					snapMan.queue(lun, offset, len);

					if (offset < 0 || len < 0 || offset > sm.getLunSize(lun) || offset + len > sm.getLunSize(lun) || offset + len < offset)
						sendAck(handle, 22);	// EINVAL
					else {
						msgWrite(handle, sm.getMapOffset(lun, offset), len, (flags & 1) == 1 ? true : false);

						if (!usesNewCommandFlushWrite && (flags & 1) == 1) {
							Log.log(LogLevel.LOG_INFO, "Uses new NBD flag (FUA)");
							usesNewCommandFlushWrite = true;
						}

						mirrorMan.queue(lun, offset, len);
					}

					totalWriteTime.addAndGet(System.currentTimeMillis() - startWrite);
					totalBytesWritten.addAndGet(len);
					totalNWrite.addAndGet(1);
				}
				else if (type == 0) {	// READ
					long startRead = System.currentTimeMillis();

					if (offset < 0 || len < 0 || offset > sm.getLunSize(lun) || offset + len > sm.getLunSize(lun) || offset + len < offset)
						sendAck(handle, 22);	// EINVAL
					else
						msgRead(handle, sm.getMapOffset(lun, offset), len);

					totalReadTime.addAndGet(System.currentTimeMillis() - startRead);
					totalBytesRead.addAndGet(len);
					totalNRead.addAndGet(1);
				}
				else if (type == 2) {	// DISCONNECT
					Log.log(LogLevel.LOG_INFO, "End of session");
					if (BlackHole.isTestcaseMode()) {
						Log.log(LogLevel.LOG_INFO, "Terminating program due to testcase mode");
						if (assumeBarriers)
							storage.commitTransaction();
						System.exit(0);
					}
					break;
				}
				else if (type == 3) {	// FLUSH
					msgFlush(handle);

					transactionSize = 0;

					if (!usesNewCommandFlush) {
						Log.log(LogLevel.LOG_INFO, "Uses new NBD commands (FLUSH)");
						usesNewCommandFlush = true;
					}
				}
				else if (type == 4) {	// DISCARD (TRIM)
					transactionSize += len;

					if (offset < 0 || len < 0 || offset > sm.getLunSize(lun) || offset + len > sm.getLunSize(lun) || offset + len < offset) {
						Log.log(LogLevel.LOG_WARN, "Discard out of range " + offset + "/" + len);

						sendAck(handle, 22);	// EINVAL
					}
					else {
						if (!usesNewCommandDiscard) {
							Log.log(LogLevel.LOG_INFO, "Uses new NBD commands (DISCARD)");

							if ((flags & 1) == 1)
								Log.log(LogLevel.LOG_INFO, "Uses new NBD flag (FUA)");

							usesNewCommandDiscard = true;
						}

						msgDiscard(handle, sm.getMapOffset(lun, offset), len, (flags & 1) == 1 ? true : false);
					}
				}
				else {
					throw new Exception("Unknown message type: " + type);
				}

				if (transactionSize >= transactionSizeLimit && assumeBarriers) {
					storage.commitTransaction();
					storage.startTransaction();

					transactionSize = 0;
				}

				totalProcessingTime.addAndGet(System.currentTimeMillis() - startProcessing);
			}

			if (assumeBarriers)
				storage.commitTransaction();
		}
                catch(SocketException se) { Log.log(LogLevel.LOG_WARN, "protocolNBD run() socket exception " + se); }
                catch(IOException ie) { Log.log(LogLevel.LOG_WARN, "protocolNBD run() IO exception " + ie); }
                catch(VHException vhe) { Log.log(LogLevel.LOG_WARN, "protocolNBD run() text exception " + vhe); }
                catch(Exception e) { // OK
			Log.log(LogLevel.LOG_WARN, "protocolNBD run() other exception " + e);
			Log.showException(e);
		}
		catch(AssertionError ae) {
			Log.showAssertionError(ae);
		}
		finally
		{
			// do not commit the barrier-transaction
			// if this program crashes before or during a barrier,
			// then the client "thinks" the data was not written

			try {
				mcs.closeSocket();
			}
			catch(Exception e) {
				Log.showException(e);
			}
		}

		totalSessionsLength.addAndGet(System.currentTimeMillis() - sessionStart);

		Log.log(LogLevel.LOG_DEBUG, " client thread stopped");
	}
}
