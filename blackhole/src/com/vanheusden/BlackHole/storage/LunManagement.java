/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.mirrors.*;
import com.vanheusden.BlackHole.protocol.*;
import com.vanheusden.BlackHole.snapshots.*;
import com.vanheusden.BlackHole.stats.*;
import com.vanheusden.BlackHole.zoning.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class LunManagement implements Runnable {
	static String version = "$Id: LunManagement.java 606 2013-07-06 22:07:22Z folkert $";
	final String confName = "lun.dat";
	List<ThreadLun> tl = new ArrayList<ThreadLun>();
	List<ProtocolListener> registeredLuns = new ArrayList<ProtocolListener>();
	Semaphore threadLunLock = new Semaphore(1, true);
	SectorMapper sm;
	String smName, path;
	Storage storage;
	MirrorManager mirrorMan;
	SnapshotManager snapMan;
	ZoneManager zm;

	public LunManagement(String path, Storage storage, MirrorManager mirrorMan, SnapshotManager snapMan, SectorMapper sm, String smName, ZoneManager zm) throws IOException, VHException {
		this.storage = storage;
		this.sm = sm;
		this.path = path;
		this.smName = smName;
		this.mirrorMan = mirrorMan;
		this.snapMan = snapMan;
		this.zm = zm;

		loadConfig(path + File.separator + confName);
	}

	private void loadConfig(String fileName) throws IOException, VHException {
		try {
			BufferedReader fh = new BufferedReader(new FileReader(fileName));

			int lineNr = 0;
			for(;;) {
				lineNr++;
				String line = fh.readLine();
				if (line == null)
					break;
				line = line.trim();
				if (line.length() == 0)
					continue;
				if (line.substring(0, 1).equals("#"))
					continue;

				String [] elements = line.split("\\s+");

				if (elements[1].equalsIgnoreCase("NBD")) {
					if (elements.length != 6)
						throw new VHException("NBD lun configuration file: parameter missing (" + lineNr + ")");

					int lunNr = Integer.valueOf(elements[0]);
					registerLun(lunNr, ProtocolType.PROTOCOL_NBD, new String [] { elements[2], elements[3], elements[5] }, elements[4]);
					startLun(lunNr);
				}
				else if (elements[1].equalsIgnoreCase("IMDisk")) {
					if (elements.length != 5)
						throw new VHException("IMDisk lun configuration file: parameter missing (" + lineNr + ")");

					int lunNr = Integer.valueOf(elements[0]);
					registerLun(lunNr, ProtocolType.PROTOCOL_IMDISK, new String [] { elements[2], elements[3], elements[5] }, elements[4]);
					startLun(lunNr);
				}
				else {
					throw new VHException("Protocol " + elements[1] + " not (yet) supported.");
				}
			}

			fh.close();
		}
		catch(FileNotFoundException fnfe) {
			Log.log(LogLevel.LOG_WARN, "LUN manager: " + fileName + " not found, first run?");
		}
	}

	void writeLine(BufferedWriter fh, String what) throws IOException {
		fh.write(what, 0, what.length());
		fh.newLine();
	}

	void saveConfig(String fileName) throws IOException {
		BufferedWriter fh = new BufferedWriter(new FileWriter(fileName));

		for(ProtocolListener pnl : registeredLuns)
			writeLine(fh, pnl.getConfigLine());

		fh.close();
	}

	public String [] getServices() {
		threadLunLock.acquireUninterruptibly();

		int n = registeredLuns.size();
		String [] out = new String[n];

		for(int index=0; index<n; index++) {
			out[index] = registeredLuns.get(index).getConfigLine();
		}

		threadLunLock.release();

		return out;
	}

	public void close() {
		Log.log(LogLevel.LOG_INFO, "LUN manager terminating");
	}

	public void growDatastore(long newSize) throws IOException {
		threadLunLock.acquireUninterruptibly();

		sm.growDatastore(newSize);
		saveConfig(path + File.separator + confName);
		sm.storeMappings(smName);

		threadLunLock.release();
	}

	public List<ThreadLun> getThreadList() {
		threadLunLock.acquireUninterruptibly();

		List<ThreadLun> copy = new ArrayList<ThreadLun>(tl.size());
		for(ThreadLun cur : tl)
			copy.add(cur);

		threadLunLock.release();

		return copy;
	}

	public SectorMapper getSectorMapper() {
		return sm;
	}

	public long getFree() {
		return sm.getFree();
	}

	public long getLunSize(int lun) {
		return sm.getLunSize(lun);
	}

	public int copyLun(int sourceLun, boolean blocking, ProtocolType tp, String [] parameters, String name) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException, InterruptedException {
		threadLunLock.acquireUninterruptibly();

		long nBlocks = getLunSize(sourceLun) / storage.getBlockSize();
		int newLunNr = addLun(tp, parameters, getLunSize(sourceLun), name);

		byte [] empty = new byte[storage.getBlockSize()];

		storage.startTransaction();

		for(long sector=0; sector<nBlocks; sector++) {
			byte [] data = storage.readBlock(sm.getMapOffset(sourceLun, sector));

			if (data != null)
				storage.putBlock(sm.getMapOffset(newLunNr, sector), data, false);
			else
				storage.putBlock(sm.getMapOffset(newLunNr, sector), empty, false);

			if ((sector & 1023) == 0) {
				storage.commitTransaction();
				storage.startTransaction();
			}
		}

		storage.commitTransaction();

		saveConfig(path + File.separator + confName);
		sm.storeMappings(smName);

		threadLunLock.release();

		return newLunNr;
	}

	public int addLun(ProtocolType tp, String [] parameters, long size, String name) throws IOException, VHException {
		threadLunLock.acquireUninterruptibly();

		int lunNr = sm.addLun(size);
		ProtocolListener protListener = null;
		if (tp == ProtocolType.PROTOCOL_NBD) {
			protListener = new ProtocolNBDListener(storage, mirrorMan, snapMan, this, lunNr, parameters[0], Integer.valueOf(parameters[1]), name, zm, parameters[2].equals("true"));
		}
		else if (tp == ProtocolType.PROTOCOL_IMDISK) {
			protListener = new ProtocolIMDiskListener(storage, mirrorMan, snapMan, this, lunNr, parameters[0], Integer.valueOf(parameters[1]), name, zm, size, parameters[2].equals("true"));
		}

		if (protListener == null) {
			threadLunLock.release();
			return -1;
		}

		registeredLuns.add(protListener);

		saveConfig(path + File.separator + confName);
		sm.storeMappings(smName);

		threadLunLock.release();

		return lunNr;
	}

	public boolean stopLun(int lunNr) {
		threadLunLock.acquireUninterruptibly();

		for(int index=0; index<tl.size(); index++) {
			ThreadLun curTl = tl.get(index);

			if (curTl.getLun() == lunNr && curTl.getEndPoint() == null) {
				Thread t = curTl.getThread();
				try {
					curTl.getProtocolListener().stop();
				}
				catch(IOException ioe) {
					Log.showException(ioe);
				}
				Utils.intJoinIgnoreEx(t);
				tl.remove(index);
				threadLunLock.release();
				return true;
			}
		}

		threadLunLock.release();

		return false;
	}

	public void registerLun(int lun, ProtocolType tp, String [] parameters, String name) throws VHException, IOException {
		threadLunLock.acquireUninterruptibly();

		Thread t = null;
		ProtocolListener protListener = null;
		if (tp == ProtocolType.PROTOCOL_NBD) {
			registeredLuns.add(new ProtocolNBDListener(storage, mirrorMan, snapMan, this, lun, parameters[0], Integer.valueOf(parameters[1]), name, zm, parameters[2] == "true"));
		}
		else if (tp == ProtocolType.PROTOCOL_IMDISK) {
			registeredLuns.add(new ProtocolIMDiskListener(storage, mirrorMan, snapMan, this, lun, parameters[0], Integer.valueOf(parameters[1]), name, zm, sm.getLunSize(lun), parameters[2] == "true"));
		}
		else {
			threadLunLock.release();
			throw new VHException("Protocol type " + tp + " not known, LUN not registered");
		}

		threadLunLock.release();
	}

	public void changeListenAdapter(int lun, String adapter) throws VHException, IOException {
		ProtocolListener pnl = findLun(lun);
		if (pnl == null)
			throw new VHException("Lun " + lun + " not known");
		pnl.setAdapter(adapter);
		saveConfig(path + File.separator + confName);
	}

	public void changeListenPort(int lun, int port) throws VHException, IOException {
		ProtocolListener pnl = findLun(lun);
		if (pnl == null)
			throw new VHException("Lun " + lun + " not known");
		pnl.setPort(port);
		saveConfig(path + File.separator + confName);
	}

	public ProtocolListener findLun(int lun) {
		ProtocolListener pnl = null;

		threadLunLock.acquireUninterruptibly();
		for(ProtocolListener curPnl : registeredLuns) {
			if (curPnl.getLun() == lun) {
				pnl = curPnl;
				break;
			}
		}
		threadLunLock.release();

		return pnl;
	}

	public void startLun(int lun) throws VHException {
		ProtocolListener pnl = findLun(lun);

		if (pnl == null)
			throw new VHException("Lun " + lun + " not known");

		Log.log(LogLevel.LOG_INFO, "Start LUN " + pnl.getName() + " on " + pnl.getNetworkAddress());

		Thread t = new Thread(pnl);
		t.setName(pnl.getProtocol() + " listener for LUN " + pnl.getName());
		t.start();

		ThreadLun curTl = new ThreadLun(pnl, t, lun, pnl.getName(), null, null);
		tl.add(curTl);
		ThreadLunReaper.getInstance().add(curTl);
	}

	public void deleteLun(int lun, boolean purge) throws IOException, VHException, SQLException {
		Log.log(LogLevel.LOG_INFO, "Delete lun " + lun);

		if (stopLun(lun))
			Log.log(LogLevel.LOG_INFO, " LUN stopped");

		threadLunLock.acquireUninterruptibly();
Log.log(LogLevel.LOG_INFO, "" + sm.getMapOffset(lun, 2147479552L + 4096L));

		boolean found = false;
		for(int index=0; index<registeredLuns.size(); index++) {
			if (registeredLuns.get(index).getLun() == lun) {
				registeredLuns.remove(index);
				found = true;
				break;
			}
		}
		if (found)
			Log.log(LogLevel.LOG_INFO, " LUN removed from list");

		if (!found) {
			threadLunLock.release();
			throw new VHException("No lun with number " + lun + " found");
		}

		if (purge) {
			Log.log(LogLevel.LOG_INFO, "Delete lun " + lun + ": removing data from datastore");
			storage.unmapLun(sm, lun);
		}

		Log.log(LogLevel.LOG_INFO, "Delete lun " + lun + ": updating tables");
		sm.deleteLun(lun);

		Log.log(LogLevel.LOG_INFO, "Delete lun " + lun + ": updating configuration files");
		saveConfig(path + File.separator + confName);
		sm.storeMappings(smName);

		threadLunLock.release();
	}

	public boolean verifySpaceAvailable(long size) {
		threadLunLock.acquireUninterruptibly();
		boolean space = sm.verifySpaceAvailable(size);
		threadLunLock.release();
		return space;
	}

	public void run() {
		try {
			for(;;) {
				Thread.sleep(2500);

				int index = 0;
				while(index < tl.size()) {
					Thread currentThread = tl.get(index).getThread();
					if (currentThread.isAlive() == false) {
						tl.remove(index);
					}
					else {
						index++;
					}
				}
			}
		}
		catch(InterruptedException ie) {
			Log.log(LogLevel.LOG_DEBUG, "Asked to stop");
		}
	}
}
