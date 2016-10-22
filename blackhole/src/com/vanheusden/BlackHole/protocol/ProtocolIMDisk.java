/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.protocol;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.mirrors.*;
import com.vanheusden.BlackHole.snapshots.*;
import com.vanheusden.BlackHole.sockets.*;
import com.vanheusden.BlackHole.stats.*;
import com.vanheusden.BlackHole.storage.*;

import java.io.IOException;
import java.net.SocketException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class ProtocolIMDisk extends Protocol {
	static String version = "$Id$";
	SectorMapper sm;
	int lun;
	MirrorManager mirrorMan;
	SnapshotManager snapMan;
	long size;
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
	boolean assumeBarriers;

        public ProtocolIMDisk(SectorMapper sm, int lun, Storage storage, MirrorManager mirrorMan, SnapshotManager snapMan, MyClientSocket mcs, long size, boolean assumeBarriers) throws IOException {
		this.sm = sm;
		this.lun = lun;
                this.storage = storage;
                this.mcs = mcs;
		this.mirrorMan = mirrorMan;
		this.snapMan = snapMan;
		this.size = size;
		this.assumeBarriers = assumeBarriers;

		mcs.setTcpNoDelay();

		if (stats == null) {
			stats = Stats.getInstance();
			stats.add(new StatsElementCounter(totalBytesRead, true, "ProtocolIMDisk: total bytes read"));
			stats.add(new StatsElementCounter(totalBytesWritten, true, "ProtocolIMDisk: total bytes written"));
			stats.add(new StatsElementAverage(totalBytesRead, totalNRead, StatsOutputType.FLOAT, true, "ProtocolIMDisk: average bytes read per request"));
			stats.add(new StatsElementAverage(totalBytesWritten, totalNWrite, StatsOutputType.FLOAT, true, "ProtocolIMDisk: average bytes written per request"));
			stats.add(new StatsElementCounter(totalIdleTime, false, "ProtocolIMDisk: total idle time (ms)"));
			stats.add(new StatsElementAverage(totalIdleTime, totalNCommands, StatsOutputType.FLOAT, false, "ProtocolIMDisk: average idle time between each command (ms)"));
			stats.add(new StatsElementCounter(totalProcessingTime, false, "ProtocolIMDisk: total processing time (ms)"));
			stats.add(new StatsElementAverage(totalProcessingTime, totalNCommands, StatsOutputType.FLOAT, false, "ProtocolIMDisk: average processing time for each command (ms)"));
			stats.add(new StatsElementCounter(totalReadTime, false, "ProtocolIMDisk: total read time (ms)"));
			stats.add(new StatsElementAverage(totalReadTime, totalNRead, StatsOutputType.FLOAT, false, "ProtocolIMDisk: average read time (ms)"));
			stats.add(new StatsElementCounter(totalWriteTime, false, "ProtocolIMDisk: total write time (ms)"));
			stats.add(new StatsElementAverage(totalWriteTime, totalNWrite, StatsOutputType.FLOAT, false, "ProtocolIMDisk: average write time (ms)"));
		}
        }

	static public byte [] javaToX86Long(long in) {
		byte [] out = new byte[8];

		for(int index=7; index>=0; index--) {
			out[7 - index] = (byte)in;
			in >>= 8;
		}

		return out;
	}

	static public long x86ToJavaLong(byte [] in) {
		assert in.length == 8;

		return ((((((((long)(in[7] & 0xff) << 8) | (long)(in[6] & 0xff)) << 8 | (long)(in[5] & 0xff)) << 8 | (long)(in[4] & 0xff)) << 8 | (long)(in[3] & 0xff)) << 8 | (long)(in[2] & 0xff)) << 8 | (long)(in[1] & 0xff)) << 8 | (long)(in[0] & 0xff);
	}

	protected void initSession() throws IOException {
		Log.log(LogLevel.LOG_DEBUG, "Init session");
		//
		mcs.flush();
	}

	void sendAck(long length) throws IOException {
		byte [] len = javaToX86Long(length);
		byte [] msg = new byte[16];
		Utils.arrayCopy(msg, 8, len);
		mcs.putBytes(msg);
	}

	protected void msgWrite(long offset, long len) throws IOException, InterruptedException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		long orgOffset = offset;
		long orgLen = len;
		int blockSize = storage.getBlockSize();

		storage.startTransaction();

		while(len > 0) {
			long blockId = offset / blockSize;
			int blockOffset = (int)(offset % blockSize);
			int cur = (int)Math.min(blockSize - blockOffset, len);
			byte [] buffer = null;

			if (blockOffset == 0 && cur == blockSize) {
				buffer = new byte[blockSize];
				mcs.getBytes(buffer);
				storage.putBlock(blockId, buffer, false);
			}
			else {
				assert cur != blockSize;
				Log.log(LogLevel.LOG_DEBUG, "REQUEST SMALLER THAN BLOCKSIZE! cur: " + cur + ", blocksize: " + blockSize);
				buffer = new byte[cur];
				mcs.getBytes(buffer);
				storage.putBlock(blockId, buffer, blockOffset, false);
			}

			len -= cur;
			offset += cur;
		}

		storage.commitTransaction();

		sendAck(orgLen);

		mcs.flush();
	}

	protected void msgRead(long offset, long len) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		long orgOffset = offset;
		long orgLen = len;
		int blockSize = storage.getBlockSize();

		Log.log(LogLevel.LOG_DEBUG, "read " + offset + " " + len);

		sendAck(orgLen);

		while(len > 0) {
			byte [] buffer = storage.readBlock(offset / blockSize);
			int blockOffset = (int)(offset % blockSize);
			int cur = (int)Math.min(blockSize - blockOffset, len);

			Log.log(LogLevel.LOG_DEBUG, "put " + cur);
			if (cur < blockSize) {
				Log.log(LogLevel.LOG_INFO, "REQUEST SMALLER THAN BLOCKSIZE! cur: " + cur + ", blocksize: " + blockSize);
				mcs.putBytes(buffer, blockOffset, cur);
			}
			else
				mcs.putBytes(buffer);

			len -= cur;
			offset += cur;
		}

		mcs.flush();
	}

	void msgInfo() throws IOException {
		byte [] sizeb = javaToX86Long(size);
		byte [] aligb = javaToX86Long(512);
		byte [] msg = new byte[24];
		Utils.arrayCopy(msg, 0, sizeb);
		Utils.arrayCopy(msg, 8, aligb);
		mcs.putBytes(msg);
	}

	public void close() throws IOException {
		mcs.closeSocket();
	}

	public void run() {
		long sessionStart = System.currentTimeMillis();
		nSessions.addAndGet(1);

		try {
			initSession();

			Log.log(LogLevel.LOG_DEBUG, " client thread running");

			for(;;) {
				long startIdle = System.currentTimeMillis();

				// get request
				byte [] cmdBytes = new byte[8];
				mcs.getBytes(cmdBytes);
				long cmd = x86ToJavaLong(cmdBytes);

				long startProcessing = System.currentTimeMillis();
				totalIdleTime.addAndGet(startProcessing - startIdle);

				totalNCommands.addAndGet(1);

				if (cmd == 3) {	// WRITE
					long startWrite = System.currentTimeMillis();

					mcs.getBytes(cmdBytes);
					long offset = x86ToJavaLong(cmdBytes);
					mcs.getBytes(cmdBytes);
					long len  = x86ToJavaLong(cmdBytes);

					snapMan.queue(lun, offset, len);

					msgWrite(sm.getMapOffset(lun, offset), len);

					mirrorMan.queue(lun, offset, len);

					totalWriteTime.addAndGet(System.currentTimeMillis() - startWrite);
					totalBytesWritten.addAndGet(len);
					totalNWrite.addAndGet(1);
				}
				else if (cmd == 2) {	// READ
					long startRead = System.currentTimeMillis();

					mcs.getBytes(cmdBytes);
					long offset = x86ToJavaLong(cmdBytes);
					mcs.getBytes(cmdBytes);
					long len  = x86ToJavaLong(cmdBytes);

					msgRead(sm.getMapOffset(lun, offset), len);

					totalReadTime.addAndGet(System.currentTimeMillis() - startRead);
					totalBytesRead.addAndGet(len);
					totalNRead.addAndGet(1);
				}
				else if (cmd == 1) {	// INFO
					msgInfo();
				}
				else
					throw new Exception("Unknown message type: " + cmd);

				totalProcessingTime.addAndGet(System.currentTimeMillis() - startProcessing);
			}
		}
                catch(SocketException se) { Log.log(LogLevel.LOG_WARN, "protocolIMDisk run() socket exception " + se); }
                catch(IOException ie) { Log.log(LogLevel.LOG_WARN, "protocolIMDisk run() IO exception " + ie); }
                catch(VHException vhe) { Log.log(LogLevel.LOG_WARN, "protocolIMDisk run() text exception " + vhe); }
                catch(Exception e) { // OK
			Log.log(LogLevel.LOG_WARN, "protocolIMDisk run() other exception " + e);
			Log.showException(e);
		}
		catch(AssertionError ae) {
			Log.showAssertionError(ae);
		}
		finally
		{
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
