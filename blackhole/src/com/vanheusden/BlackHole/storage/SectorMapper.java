/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.VHException;
import com.vanheusden.BlackHole.Log;
import com.vanheusden.BlackHole.LogLevel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;

public class SectorMapper {
	static String version = "$Id: SectorMapper.java 606 2013-07-06 22:07:22Z folkert $";
	static final long GB = 1024L * 1024L * 1024L;
	long datastoreSize = -1;
	List<Lun> luns = new ArrayList<Lun>();
	List<Integer> partitionToLun = new ArrayList<Integer>();
        ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
        Lock rl = readWriteLock.readLock();;
        Lock wl = readWriteLock.writeLock();;

	void readLock() {
		rl.lock();
	}

	void readUnlock() {
		rl.unlock();
	}

	void writeLock() {
		wl.lock();
	}

	void writeUnlock() {
		wl.unlock();
	}

        int longToInt(long value) {
                assert value <= 2147483647;
                return (int)value;
        }

	public SectorMapper(long size) {
		datastoreSize = size;

		int nLuns = longToInt(size / GB);
		assert (nLuns * GB) == size;
		for(int index=0; index<nLuns; index++)
			partitionToLun.add(-1);
	}

	String readWithExpect(BufferedReader fh, int line, String expected) throws IOException {
		String lineStr = fh.readLine();
		if (lineStr == null) {
			System.err.println("sectormap.dat: line " + line + " missing line");
			System.exit(1);
		}

		String [] parts = lineStr.split(" ");
		if (parts.length != 2) {
			System.err.println("sectormap.dat: line " + line + " consists of > 2 elements, remove double spaces if any");
			System.exit(1);
		}

		if (expected.equalsIgnoreCase(parts[0]) == false) {
			System.err.println("sectormap.dat: line " + line + " expecteding element " + expected);
			System.exit(1);
		}

		String out = parts[1].trim();
		if (out.length() == 0) {
			System.err.println("sectormap.dat: line " + line + " missing parameter");
			System.exit(1);
		}

		return parts[1];
	}

	public SectorMapper(long size, String mapFile) throws IOException, FileNotFoundException {
		assert ((size / GB) * GB) == size;
		datastoreSize = size;
		int line = 1;
		BufferedReader fh = new BufferedReader(new FileReader(mapFile));

		int nPartitions = Integer.valueOf(readWithExpect(fh, line++, "nPartitions"));
		for(int index=0; index<nPartitions; index++)
			partitionToLun.add(Integer.valueOf(readWithExpect(fh, line++, "partitionToLun")));

		int nLuns = Integer.valueOf(readWithExpect(fh, line++, "nLuns"));
		for(int index=0; index<nLuns; index++) {
			int thisNP = Integer.valueOf(readWithExpect(fh, line++, "nPartitionsForThisLun"));
			long lunSize = Long.valueOf(readWithExpect(fh, line++, "lunSize"));
			if (lunSize == 0 && thisNP == 0) {
				luns.add(null);
			}
			else {
				List<Integer> ps = new ArrayList<Integer>();
				for(int p=0; p<thisNP; p++)
					ps.add(Integer.valueOf(readWithExpect(fh, line++, "partitionNr")));
				luns.add(new Lun(lunSize, ps));
			}
		}
	}

	void writeLine(BufferedWriter fh, String what) throws IOException {
		fh.write(what, 0, what.length());
		fh.newLine();
	}

	void storeMappings(String mapFile) throws IOException {
		BufferedWriter fh = new BufferedWriter(new FileWriter(mapFile));

		readLock();
		writeLine(fh, "nPartitions " + partitionToLun.size());
		for(int index=0; index<partitionToLun.size(); index++)
			writeLine(fh, "partitionToLun " + partitionToLun.get(index));

		writeLine(fh, "nLuns " + luns.size());
		for(int index=0; index<luns.size(); index++) {
			int thisNP = 0;
			long lunSize = 0;
			if (luns.get(index) != null) {
				thisNP = luns.get(index).getPartitions().size();
				lunSize = luns.get(index).getSize();
			}
			writeLine(fh, "nPartitionsForThisLun " + thisNP);
			writeLine(fh, "lunSize " + lunSize);
			for(int p=0; p<thisNP; p++)
				writeLine(fh, "partitionNr " + luns.get(index).getPartitions().get(p));
		}
		readUnlock();

		fh.close();
	}

	public int addLun(long size) throws VHException {
		writeLock();
		int nr = -1;
		for(int index=0; index<luns.size(); index++) {
			if (luns.get(index) == null) {
				nr = index;
				break;
			}
		}
		if (nr == -1) {
			nr = luns.size();
			luns.add(new Lun());
		}
		else {
			luns.set(nr, new Lun());
		}
		writeUnlock();

		if (size > 0)
			growLun(nr, size);

		return nr;
	}

	public void growDatastore(long newSize) {
		assert ((newSize / GB) * GB) == newSize;
		writeLock();
		assert newSize > datastoreSize;
		long oldSize = datastoreSize;
		int oldNPartitions = (int)((oldSize + GB - 1)/GB);
		int newNPartitions = (int)((newSize + GB - 1)/GB);
		int n = newNPartitions - oldNPartitions;
		for(int index=0; index<n; index++)
			partitionToLun.add(-1);
		datastoreSize = newSize;
		writeUnlock();
	}

	public long getLunSize(int lun) {
		readLock();
		long lunSize = luns.get(lun).getSize();
		readUnlock();
		return lunSize;
	}

	public long getMapOffset(int lun, long offset) {
		readLock();
		Lun l = luns.get(lun);
		long result = l.getMapOffset(offset);
		readUnlock();
		return result;
	}

	public void growLun(int lun, long newSize) throws VHException {
		if (luns.get(lun) == null)
			throw new VHException("That LUN does not exist");
		writeLock();
		long oldSize = luns.get(lun).getSize();
		int oldNPartitions = (int)((oldSize + GB - 1)/GB);
		int newNPartitions = (int)((newSize + GB - 1)/GB);
		int n = newNPartitions - oldNPartitions;
		if (n > 0) {
			List<Integer> pList = new ArrayList<Integer>();
			for(int nr=0; nr<n; nr++) {
				int partition = allocatePartition(lun);
				assert partition != -1;
				pList.add(partition);
			}
			luns.get(lun).grow(newSize, pList);
		}
		else {
			luns.get(lun).grow(newSize, null);
		}
		writeUnlock();
	}

	public boolean verifySpaceAvailable(long size) {
		readLock();
		for (Integer aPartitionToLun : partitionToLun) {
			if (aPartitionToLun == -1) {
				size -= GB;
				if (size <= 0)
					break;
			}
		}
		readUnlock();

		if (size <= 0)
			return true;

		return false;
	}

	public long getFree() {
		long free = 0;

		readLock();
		for(int index=0; index<partitionToLun.size(); index++) {
			int p = partitionToLun.get(index);
			if (p == -1)
				free += GB;
		}
		readUnlock();

		return free;
	}

	public int allocatePartition(int lun) {
		for(int index=0; index<partitionToLun.size(); index++) {
			int p = partitionToLun.get(index);
			if (p == -1) {
				partitionToLun.set(index, lun);
				return index;
			}
		}

		return -1;
	}

	public void deleteLun(int lun) {
		if (luns.get(lun) != null) {
			luns.set(lun, null);
			for(int index=0; index<partitionToLun.size(); index++) {
				if (partitionToLun.get(index) == lun)
					partitionToLun.set(index, -1);
			}
		}
	}
}
