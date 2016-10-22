/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.snapshots;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.storage.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class SnapshotManager {
	static String version = "$Id: SnapshotManager.java 606 2013-07-06 22:07:22Z folkert $";
	ReentrantReadWriteLock readWriteLock;
	Lock rl;
	Lock wl;
	String snapshotJournal = null;
	CopyOnWriteArrayList<Snapshot> snapshots = new CopyOnWriteArrayList<Snapshot>();
	CopyOnWriteArrayList<Thread> threads = new CopyOnWriteArrayList<Thread>();
	Storage storage;
	SectorMapper sm;
	int blockSize;

	public SnapshotManager(String dataPath, Storage s, SectorMapper sm, int blockSize) throws IOException, VHException {
		readWriteLock = new ReentrantReadWriteLock(true);
		rl = readWriteLock.readLock();
		wl = readWriteLock.writeLock();

		snapshotJournal = dataPath + "/snapshot-journal.dat";

		storage = s;
		this.sm = sm;
		this.blockSize = blockSize;

		restartSnapshots();
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

	public void close() throws InterruptedException, IOException {
		Log.log(LogLevel.LOG_INFO, "Stopping snapshot-threads");
		readLock();
		for(Thread t : threads) {
			Utils.intJoinIgnoreEx(t);
		}
		readUnlock();
		Log.log(LogLevel.LOG_INFO, "Writing snapshot journal");
		writeSnapshotJournal();
	}

	public void queue(int lun, long offset, long len) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		readLock(); // only reads the SnapshotManager structures
		long startOffset = offset - (offset % blockSize);
		long endOffset = offset + len;

		for(int li=0; li<snapshots.size(); li++) {
			Snapshot s = snapshots.get(li);
			if (s.getLun() == lun) {
				for(long index=startOffset; index<endOffset; index += blockSize) {
					s.queue(index);
				}
			}
		}
		readUnlock();
	}

	public void startSnapshot(int lun, String filename, EncryptionParameters ep) throws IOException, VHException {
		writeLock();
		Snapshot ns = new Snapshot(filename + "-bitmap.dat", sm.getLunSize(lun), blockSize, storage, sm, filename, lun, ep);
		snapshots.add(ns);

		Thread snapshotThread = new Thread(ns, "Snapshot-thread " + filename + " (" + lun + ")");
		snapshotThread.start();
		threads.add(snapshotThread);
		writeUnlock();

		writeSnapshotJournal();
	}

	public void writeSnapshotJournal() throws IOException {
		writeLock();
		BufferedWriter out = new BufferedWriter(new FileWriter(snapshotJournal, false));

		for(Snapshot current : snapshots) {
			String file = current.getFilename();
			int lun = current.getLun();
			EncryptionParameters ep = current.getEncryptionParameters();
			String outLine = null;
			if (ep != null)
				outLine = "" + lun + " " + file + " " + ep.getPassword() + " " + ep.getCipher();
			else
				outLine = "" + lun + " " + file;
			out.write(outLine, 0, outLine.length());
			out.newLine();
		}

		out.close();
		writeUnlock();
	}

	protected void restartSnapshots() throws IOException, VHException {
		try {
			List<String[]> list = new ArrayList<String[]>();

			writeLock();
			BufferedReader in = new BufferedReader(new FileReader(snapshotJournal));

			for(;;) {
				String line = in.readLine();
				if (line == null)
					break;
				if (line.equals(""))
					continue;
				String [] elements = line.split(" ");
				list.add(elements);
			}
			in.close();

			for(String [] current : list) {
				if (current.length == 4)
					startSnapshot(Integer.valueOf(current[0]), current[1], new EncryptionParameters(current[2] /* password */, current[3] /* cipher */));
				else
					startSnapshot(Integer.valueOf(current[0]), current[1], null);
			}
		}
		catch(FileNotFoundException fnfe) {
			Log.log(LogLevel.LOG_INFO, "No snapshot journal, not restarting any snapshots");
		}
		finally {
			writeUnlock();
		}
	}

	public CopyOnWriteArrayList<Snapshot> getRunningSnapshots() {
		return snapshots;
	}

	public void run() {
		for(;;) {
			try {
				boolean updateJournal = false;
				writeLock();
				int index=0;
				while(index < snapshots.size()) {
					if (snapshots.get(index).isFinished()) {
						Snapshot cur = snapshots.get(index);
						snapshots.remove(index);
						updateJournal = true;
						Log.log(LogLevel.LOG_DEBUG, "Clean thread: removed finished snapshot from queue (lun " + cur.getLun() + ")");
					}
					else
						index++;
				}
				writeUnlock();

				if (updateJournal)
					writeSnapshotJournal();

				writeLock();
				for(Thread t : threads) {
					if (!t.isAlive())
						t.join();
				}
				writeUnlock();

				Thread.sleep(5000);
			}
			catch(IOException ioe) {
				Log.showException(ioe);
				Log.log(LogLevel.LOG_CRIT, "SnapshotManager: aborting");
				break;
			}
			catch(InterruptedException ie) {
				Log.showException(ie);
			}
		}
	}
}
