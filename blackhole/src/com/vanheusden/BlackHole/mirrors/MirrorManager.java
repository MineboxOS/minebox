/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.mirrors;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.protocol.*;
import com.vanheusden.BlackHole.storage.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class MirrorManager {
	static String version = "$Id: MirrorManager.java 606 2013-07-06 22:07:22Z folkert $";
	ReentrantReadWriteLock readWriteLock;
	Lock rl;
	Lock wl;
	CopyOnWriteArrayList<Mirror> mirrors = new CopyOnWriteArrayList<Mirror>();
	CopyOnWriteArrayList<MirrorParameters> mirrorPars = new CopyOnWriteArrayList<MirrorParameters>();
	CopyOnWriteArrayList<Thread> threads = new CopyOnWriteArrayList<Thread>();
	Storage storage;
	String dataPath;
	int blockSize;
	SectorMapper sm;

	public MirrorManager(SectorMapper sm, Storage s, String dataPath, int blockSize) {
		readWriteLock = new ReentrantReadWriteLock(true);
		rl = readWriteLock.readLock();
		wl = readWriteLock.writeLock();

		storage = s;
		this.dataPath = dataPath;
		this.blockSize = blockSize;
		this.sm = sm;
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

	public void queue(int lun, long offset, long len) throws Exception {
		readLock();
		try {
			for(Mirror mirror : mirrors) {
				// Log.log(LogLevel.LOG_DEBUG, "mm " + offset + " " + data.length);
				if (mirror.getLun() == lun) {
					long startOffset = offset - (offset % blockSize);
					long endOffset = offset + len;

					for(long index=startOffset; index<endOffset; index += blockSize) {
						byte [] data = storage.readBlock(sm.getMapOffset(lun, index) / blockSize);
						mirror.queue(index, data);
					}
				}
			}
		}
		catch(Exception e) { // OK
			Log.log(LogLevel.LOG_WARN, "Mirrors: exception during queue: " + e);
			throw e;
		}
		finally {
			readUnlock();
		}
	}

	public void close() throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		// bitmap will take care of early exit
		Log.log(LogLevel.LOG_INFO, "Closing mirrors");
		readLock();
		for(Mirror cm : mirrors)
			cm.close();
		readUnlock();
	}

	public void addMirror(MirrorParameters mp) throws VHException, SQLException, IOException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		writeLock();
		Mirror mirror = null;

		String subFileName = "-" + mp.getHost() + "-" + mp.getPort();

		String to =  mp.getHost() + ":" + mp.getPort();
		if (mp.getPar1() != null) {
			to += " " + mp.getPar1();
			to += " " + mp.getPar2();
		}

		long lunSize = sm.getLunSize(mp.getLun());
		if (mp.getMirrorType() == MirrorType.METRO_MIRROR) {
			Log.log(LogLevel.LOG_INFO, "Added sync mirror (metro mirror) to " + to);
			if (mp.getProtocolType() == ProtocolType.PROTOCOL_ISCSI)
				mirror = new MetroMirror_iscsi(mp.getHost(), mp.getPort(), mp.getPar1(), mp.getPar2(), dataPath + "/mm-journal-bitmap" + subFileName + ".dat", mp.getLun(), lunSize, blockSize, mp.getDisconnectBlock(), sm);
			else
				mirror = new MetroMirror(mp.getHost(), mp.getPort(), dataPath + "/mm-journal-bitmap" + subFileName + ".dat", mp.getLun(), lunSize, blockSize, mp.getDisconnectBlock(), sm);
		}
		else if (mp.getMirrorType() == MirrorType.GLOBAL_MIRROR) {
			Log.log(LogLevel.LOG_INFO, "Added a-sync mirror (global mirror) to " + to);
			if (mp.getProtocolType() == ProtocolType.PROTOCOL_ISCSI) {
				mirror = new GlobalMirror_iscsi(mp.getHost(), mp.getPort(), mp.getPar1(), mp.getPar2(), dataPath + "/gm-journal-bitmap" + subFileName + ".dat", mp.getLun(), lunSize, blockSize, sm);
			}
			else
				mirror = new GlobalMirror(mp.getHost(), mp.getPort(), dataPath + "/gm-journal-bitmap" + subFileName + ".dat", mp.getLun(), lunSize, blockSize, sm);
		}
		else {
			Log.log(LogLevel.LOG_ERR, "Unknown mirrortype?!");
			System.exit(1);
		}

		addMirror(mirror);
		mirrorPars.add(mp);

		if (mp.getMirrorType() == MirrorType.GLOBAL_MIRROR) {
			Log.log(LogLevel.LOG_INFO, "Global mirror, start thread");

			Thread mirrorThread = new Thread(mirror, "mirror " + mp.getMirrorType() + "/" + to);
			mirrorThread.start();
			threads.add(mirrorThread);
		}

		writeUnlock();
	}

	private void addMirror(Mirror mirror) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		mirrors.add(mirror);

		mirror.setCallBack(storage);
		mirror.flushBacklog();
	}

	public void flushMirrors() throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		readLock();
		for(Mirror current : mirrors) {
			Log.log(LogLevel.LOG_INFO, "Flushing mirror " + current.toString());
			current.flushBacklog();
		}
		readUnlock();
	}

	public CopyOnWriteArrayList<MirrorParameters> getMirrorPars() {
		return mirrorPars;
	}

	public CopyOnWriteArrayList<Mirror> getMirrors() {
		return mirrors;
	}
}
