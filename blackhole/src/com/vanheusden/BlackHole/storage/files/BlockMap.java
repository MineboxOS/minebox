/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage.files;

import com.vanheusden.BlackHole.Utils;
import com.vanheusden.BlackHole.VHException;
import com.vanheusden.BlackHole.Log;
import com.vanheusden.BlackHole.LogLevel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockMap {
	static String version = "$Id: BlockMap.java 606 2013-07-06 22:07:22Z folkert $";
	Bitmap inUse;
	TableOnDisk pointers;
	long nBlocks;
	Map<Long, List<Long>> blockIdToSectors = null;

	public void delete() {
		inUse.delete();
		pointers.delete();
	}

	public void growSize(long newNBlocks) throws IOException {
		inUse.growSize(newNBlocks);
		pointers.growSize(newNBlocks);
	}

	public BlockMap(String bitmapFilename, String pointersFilename, long nBlocks) throws IOException, VHException {
		inUse = new Bitmap(bitmapFilename, nBlocks);
		pointers = new TableOnDisk(pointersFilename, 8, nBlocks, null);
		this.nBlocks = nBlocks;
	}

	public void flush() throws IOException, VHException {
		inUse.flush();
		pointers.flush();
	}

	public void close() throws IOException, VHException {
		inUse.close();
		pointers.close();
	}
/*
	public void initializeForDefrag() {
		blockIdToSectors = new HashMap<Long, List<Long>>();

		for(long sectorNr=0; sectorNr<nBlocks; sectorNr++) {
			if (inUse.getBit(sectorNr)) {
				long curBlockId = Utils.byteArrayToLong(pointers.retrieveElement(sectorNr, false, 4));

				boolean add = false;
				List<Long> sectors = blockIdToSectors.get(curBlockId);
				if (sectors == null) {
					sectors = new ArrayList<Long>();
					add = true;
				}

				boolean foundSector = false;
				for(Long curSector : sectors) {
					if (curSector == sectorNr) {
						foundSector = true;
						break;
					}
				}

				if (!foundSector)
						sectors.add(sectorNr);

				if (add)
					blockIdToSectors.put(Long.valueOf(curBlockId), sectors);
			}
		}
	}

	public long replaceBlockId(long oldBlockId, long newBlockId) {
		// return pointers.searchAndReplace(inUse, oldBlockId, newBlockId);
		assert false;
		return -1;
	}
*/
	public long getBlockId(long sectorNr) throws IOException, VHException {
		long tableIndex = sectorNr;
		Log.log(LogLevel.LOG_DEBUG, "getBlockId: retrieving blockid for sector " + tableIndex + ", in use: " + inUse.getBit(tableIndex));
		if (inUse.getBit(tableIndex)) {
			long blockId = Utils.byteArrayToLong(pointers.retrieveElement(tableIndex, false, 4));

			Log.log(LogLevel.LOG_DEBUG, "getBlockId: returning " + blockId);
			return blockId;
		}

		Log.log(LogLevel.LOG_DEBUG, "getBlockId: returning -1");
		return -1;
	}

	public boolean hasElement(long sectorNr) throws IOException, VHException {
		return inUse.getBit(sectorNr);
	}

	public void setBlockId(long sectorNr, long blockId) throws IOException, VHException {
		long tableIndex = sectorNr;

		byte [] out = Utils.longToByteArray(blockId);
		pointers.storeElement(tableIndex, out, 3);

		inUse.setBit(tableIndex, true);
	}

	public void forgetSector(long sectorNr) throws IOException, VHException {
		inUse.setBit(sectorNr, false);
	}
/*
	public long [] getSectorsUsingBlock(long blockId) {
		List<Long> list = new ArrayList<Long>();

		for(long sectorNr=0; sectorNr<nBlocks; sectorNr++) {
			long curBlockId = getBlockId(sectorNr);
			if (curBlockId == blockId)
				list.add(sectorNr);
		}

		int n = list.size();
		if (n == 0)
			return null;

		long [] out = new long[n];
		int index = 0;
		for(Long cur : list) {
			out[index++] = cur;
		}

		return out;
	}
*/
}
