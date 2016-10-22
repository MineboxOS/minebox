/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.cache.*;
import com.vanheusden.BlackHole.storage.files.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class StorageFiles extends Storage {
	BlockMap bm;
	DataStore ds;
	TableOnDisk buc;
	long nBlocks;

	public void delete() throws IOException, VHException, SQLException {
		closeStorageBackend();
		bm.delete();
		ds.delete();
		buc.delete();
	}

	protected void relocateBlock(long fromBlockId, long toBlockId) {
		assert false;
	}

	public void swapBlock(String dataPath, long blockId1, long blockId2) throws IOException {
		assert false;
	}

        boolean relocatePending(String dataPath) {
		assert false;
		return false;
	}

	void runRelocateIntentJournal(String dataPath) {
		assert false;
	}

	void writeRelocateIntentToJournal(String dataPath, long fromBlockId, long toBlockId) throws IOException {
		assert false;
	}

	public void growDatastore(long newSize) throws IOException {
		writeLock();
		long newNBlocks = newSize / blockSize;
		assert newNBlocks >= nBlocks;
		nBlocks = newNBlocks;
		fileSize = newSize;

		bm.growSize(nBlocks);
		ds.growSize(newSize);
		buc.growSize(nBlocks);
		writeUnlock();
	}

	public Double getPercentageBlockUseDecrease() throws IOException, VHException {
		long nBlocksAlloc = 0;
		long nBlocksReal = 0;
		long nBlocks = fileSize / blockSize;

		for(long index=0; index<nBlocks; index++) {
			readLock();
			byte [] bytes = buc.retrieveElement(index, true, 23);
			readUnlock();

			long value = Utils.byteArrayToLong(bytes);
			assert value >= 0;
			if (value > 0) {
				nBlocksReal += value;
				nBlocksAlloc++;
			}

		}

		return (((double)nBlocksAlloc - (double)nBlocksReal) / (double)nBlocksReal) * 100.0;
	}

	public byte [] getHash(long sectorNr) throws IOException, VHException {
		long blockId = getBlockId(sectorNr);
		if (blockId == -1)
			return null;

                return ds.getHash(blockId);
        }

	public void storeSectorBlockIdMapping(long sectorNr, long blockId) throws IOException, VHException {
		Log.log(LogLevel.LOG_DEBUG, "storeSectorBlockIdMapping: mapping block " + blockId + " to sector " + sectorNr);
		bm.setBlockId(sectorNr, blockId);
		//setBlockUsageCnt(blockId, 1);
		incBlockUsageCnt(blockId);
	}

	public void unmapSector(long sectorNr) throws IOException, VHException {
		long blockId = getBlockId(sectorNr);

		if (blockId != -1) {
			bm.forgetSector(sectorNr);

			if (decBlockUsageCnt(blockId) == 0)
				ds.deleteBlock(blockId);
		}
	}

        public long findBlockForThisHash(byte [] hash) throws IOException, VHException {
		long blockId = ds.searchByHash(hash);
		Log.log(LogLevel.LOG_DEBUG, "findBlockForThisHash: found copy of this block at blockid " + blockId);
		return blockId;
        }

        public Long [] findBlockForThisHashWithStoreInfo(byte [] hash) throws IOException, VHException {
		return ds.searchByHashWithStoreInfo(hash);
        }

        public byte [] calcHash(byte [] data) {
                return ds.calcHash(data);
        }

        public long storeNewBlock(long parent, byte [] hash, byte [] data) throws IOException, VHException {
		assert data.length == blockSize;

		long blockId = ds.addData(parent, hash, data);
		Log.log(LogLevel.LOG_DEBUG, "storeNewBlock: stored new block at blockid " + blockId);
		return blockId;
        }

        public long storeNewBlock(byte [] hash, byte [] data) throws IOException, VHException {
		assert data.length == blockSize;

		long blockId = ds.addData(hash, data);
		Log.log(LogLevel.LOG_DEBUG, "storeNewBlock: stored new block at blockid " + blockId);
		return blockId;
        }

	public long storeNewBlock(byte [] data) throws IOException, VHException {
		assert data.length == blockSize;

		long blockId = ds.addData(data);
		Log.log(LogLevel.LOG_DEBUG, "storeNewBlock: stored new block at blockid " + blockId);
		return blockId;
	}

	public long updateDataBlock(long blockId, byte [] hash, byte [] data) throws IOException, VHException {
		assert data.length == blockSize;
		Log.log(LogLevel.LOG_DEBUG, "updateDataBlock(" + blockId + ")");

		long newBlockId = ds.updateData(blockId, hash, data);

		return newBlockId;
	}

	public long updateDataBlock(long blockId, byte [] data) throws IOException, VHException {
		assert data.length == blockSize;
		Log.log(LogLevel.LOG_DEBUG, "updateDataBlock(" + blockId + ")");

		long newBlockId = ds.putData(data);
		return newBlockId;
	}

	public void updateMapPointer(long sectorNr, long oldBlockId, long newBlockId) throws IOException, VHException {
		Log.log(LogLevel.LOG_DEBUG, "updateMapPointer: working on sector " + sectorNr + ", replacing with " + newBlockId + " (was: " + oldBlockId + ")");
		bm.setBlockId(sectorNr, newBlockId);
		decBlockUsageCnt(oldBlockId);
		incBlockUsageCnt(newBlockId);
	}

	public void deleteDataBlock(long blockId) throws IOException, VHException {
		Log.log(LogLevel.LOG_DEBUG, "deleteDataBlock: deleting block " + blockId);
		ds.deleteBlock(blockId);
	}

	public void setBlockUsageCnt(long blockId, long cnt) throws IOException, VHException {
		buc.storeElement(blockId, Utils.longToByteArray(cnt), 0);
	}

	public long decBlockUsageCnt(long blockId) throws IOException, VHException {
		byte [] bytes = buc.retrieveElement(blockId, false, 0);
		long value = Utils.byteArrayToLong(bytes);
		assert value <= nBlocks && value > 0;
		value--;
		assert value >= 0;
		buc.storeElement(blockId, Utils.longToByteArray(value), 1);
		return value;
	}

	public void incBlockUsageCnt(long blockId) throws IOException, VHException {
		byte [] bytes = buc.retrieveElement(blockId, false, 1);
		long value = Utils.byteArrayToLong(bytes);
		assert value >= 0;
		assert value <= nBlocks;
		value++;
		assert value <= nBlocks;
		buc.storeElement(blockId, Utils.longToByteArray(value), 2);
	}

	public long findNumberOfSectorsUsingBlock(long blockId) throws IOException, VHException {
		assert blockId >= 0;
		byte [] bytes = buc.retrieveElement(blockId, false, 3);
		long out = Utils.byteArrayToLong(bytes);
		assert out <= nBlocks;
		return out;
	}

	public long getBlockId(long sectorNr) throws IOException, VHException {
		long blockId = bm.getBlockId(sectorNr);
		Log.log(LogLevel.LOG_DEBUG, "getBlockId: sector " + sectorNr + " is located at block " + blockId);
		return blockId;
	}

	protected byte [] readBlockLow(long sectorNr) throws IOException, VHException {
		byte [] data;

		long blockId = bm.getBlockId(sectorNr);
		if (blockId == -1)
			data = new byte[blockSize];
		else
			data = ds.getData(blockId);

		return data;
	}

	public StorageFiles(long fileSize, int blockSize, String dataPath, HashType hashType, ReadcacheParameters rcp, WritecacheParameters wcp, EncryptionParameters ep, boolean isFsck) throws IOException, VHException, NoSuchAlgorithmException, NoSuchAlgorithmException, SQLException {
		super(dataPath, rcp, wcp, fileSize, blockSize, hashType);

		assert fileSize % blockSize == 0;

		concurrentRead = false;

		nBlocks = fileSize / blockSize;
		Log.log(LogLevel.LOG_INFO, "Size: " + nBlocks + " blocks");

		bm = new BlockMap(dataPath + "/blockmap-bitmap.dat", dataPath + "/blockmap-pointers.dat", nBlocks);
		ds = new DataStore(fileSize, blockSize, dataPath + "/data-data.dat", dataPath + "/data-hashtable.dat", dataPath + "/data-hashbitmap.dat", dataPath + "/topnode.dat", hashType, ep, isFsck);
		buc = new TableOnDisk(dataPath + "/block-cnt.dat", 8, nBlocks, null);

		this.dataPath = dataPath;

		// bm.initializeForDefrag();
	}

	public boolean fsck() throws IOException, VHException {
		boolean ok = ds.fsck();

		// FIXME buc versus bm check
		Log.log(LogLevel.LOG_INFO, "FSCK, block counts: ");
		Log.log(LogLevel.LOG_INFO, "(counting) ");
		boolean fine = true;
		int checkN = (int)nBlocks;
		if (nBlocks >= 2147483648L) {
			checkN = 2147483647;
			Log.log(LogLevel.LOG_INFO, "(check truncated) ");
		}
		long [] blockUsageCount = new long[checkN];
		for(long element = 0; element < nBlocks; element++) {
			long current = bm.getBlockId(element);
			if (current == -1) {
				if (bm.hasElement(element) == true) {
					Log.log(LogLevel.LOG_INFO, "" + element + " ");
					fine = false;
				}
			}
			else {
				if (current < 2147483648L)
					blockUsageCount[(int)current]++;

				if (ds.hasElement(current) == false) {
					Log.log(LogLevel.LOG_INFO, "" + element + " ");
					fine = false;
				}
			}
		}
		Log.log(LogLevel.LOG_INFO, "(verifying " + checkN + ") ");
		for(int element = 0; element < checkN; element++) {
			if (blockUsageCount[element] != findNumberOfSectorsUsingBlock(element)) {
				Log.log(LogLevel.LOG_INFO, "" + element + " ");
				fine = false;
			}
		}
		Log.log(LogLevel.LOG_INFO, "" + fine);
		if (!fine)
			ok = false;

		return ok;
	}

	public void startTransaction() throws IOException, VHException, SQLException {
		// FIXME
	}

	public void commitTransaction() throws IOException, VHException, SQLException {
		// FIXME
		bm.flush();
		ds.flush();
		buc.flush();
	}

	public void closeStorageBackend() throws IOException, VHException, SQLException {
		super.closeStorageBackend();

		Log.log(LogLevel.LOG_INFO, "close datastore");
		ds.close();
		Log.log(LogLevel.LOG_INFO, "close blockmap");
		bm.close();
		Log.log(LogLevel.LOG_INFO, "close buc");
		buc.close();

		Log.log(LogLevel.LOG_INFO, "finished closeStorageBackend");
	}

	void putBlockLow(long sectorNr, byte [] data) throws IOException, VHException, SQLException, BadPaddingException, IllegalBlockSizeException, DataFormatException {
		putBlockLow(sectorNr, data, calcHash(data));
	}

	void putBlockLow(long sectorNr, byte [] data, byte [] hash) throws IOException, VHException, SQLException, BadPaddingException, IllegalBlockSizeException, DataFormatException {
		assert checkWriteLocked();

		lastSectorWritten = sectorNr;
		lastWriteThread = Thread.currentThread();

		assert sectorNr >= 0;
		long blockId = getBlockId(sectorNr);
		assert blockId >= -1;

		if (testMode) {
			byte [] storedHash = testModeSectorHash.retrieveElement(sectorNr, true, -1);
			if (storedHash != null) {
				if (!Arrays.equals(hash, storedHash)) {
					Log.log(LogLevel.LOG_EMERG, "putBlockLow " + sectorNr + " stored hash doesn't match with new hash");
					byte [] action = testModeAction.retrieveElement(sectorNr, true, -1);
					Log.log(LogLevel.LOG_EMERG, "action on this sector: " + action[0]);
					throw new VHException("corrupt data");
				}
			}
		}

		if (blockId == -1)	// no record for this datablock in the database yet
		{
			//Log.log(LogLevel.LOG_DEBUG, "This is a new block: " + sectorNr);
			// see if we have a block that has the same data
			Long [] rc = findBlockForThisHashWithStoreInfo(hash);
			blockId = rc[0];
			if (blockId == -1) { // no, add new block
				// Log.log(LogLevel.LOG_DEBUG, "storeNewBlock");
				blockId = storeNewBlock(rc[1], hash, data);
				lastWriteAction = 1;
			}
			else {
				// Log.log(LogLevel.LOG_DEBUG, "Found data in block " + blockId + " so re-using that");
				lastWriteAction = 6;
			}
			storeSectorBlockIdMapping(sectorNr, blockId);

			// Log.log(LogLevel.LOG_DEBUG, "Storing block as " + blockId + " with hash " + Utils.byteArrayToHexString(hash));
		}
		else
		{
			long n = findNumberOfSectorsUsingBlock(blockId);
			if (n <= 0 || n >= nBlocks)
			{
				Log.log(LogLevel.LOG_EMERG, "Datablock count for sector " + sectorNr + " is strange: " + n);
				assert false;
				System.exit(127);
			}

			if (n > 1)
			{
				// Log.log(LogLevel.LOG_DEBUG, "Block used by multiple sectors, creating new with hash " + Utils.byteArrayToHexString(hash));
				// see if we have a block that has the same data
				Long [] rc = findBlockForThisHashWithStoreInfo(hash);
				long newBlockId = rc[0];
				if (newBlockId == -1) { // no, add new block
					newBlockId = storeNewBlock(rc[1], hash, data);
					lastWriteAction = 2;
				}
				else {
					// Log.log(LogLevel.LOG_DEBUG, "Block already existing, re-using " + newBlockId + " (was: " + blockId + ")");
					lastWriteAction = 3;
				}
				updateMapPointer(sectorNr, blockId, newBlockId);
			}
			else if (n == 1)
			{
				// Log.log(LogLevel.LOG_DEBUG, "Block only used once, update (" + blockId + ") with hash " + Utils.byteArrayToHexString(hash));
				long newBlockId = updateDataBlock(blockId, hash, data);
				if (blockId != newBlockId)
				{
					// Log.log(LogLevel.LOG_DEBUG, "The new data is also at " + newBlockId + " so updating there...");
					updateMapPointer(sectorNr, blockId, newBlockId);
					// Log.log(LogLevel.LOG_DEBUG, "...and deleting the old " + blockId);
					// updateDataBlock already does this deleteDataBlock(blockId);
					lastWriteAction = 4;
				}
				else
				{
					// Log.log(LogLevel.LOG_DEBUG, "Replacing block with itself");
					lastWriteAction = 5;
				}
			}
			else // cannot happen
				assert false;
		}

		if (testMode) {
			byte [] action = new byte [] { (byte)lastWriteAction };
			testModeAction.storeElement(sectorNr, action, -1);
		}

		// Log.log(LogLevel.LOG_DEBUG, "--- END PUT BLOCK ---");
	}

	public void testModeVerifyData(long sectorNr, byte [] data, int readFrom) throws IOException, VHException, SQLException {
		if (testMode && data != null) {
			if (testModeInUse.getBit(sectorNr)) {
				byte [] curHash = calcHash(data);
				byte [] storedHash = testModeSectorHash.retrieveElement(sectorNr, true, -1);
				if (!Arrays.equals(curHash, storedHash)) {
					long blockId = getBlockId(sectorNr);
					Log.log(LogLevel.LOG_EMERG, "testMode: Data corrupt! " + Utils.byteArrayToHexString(curHash) + " versus " + Utils.byteArrayToHexString(storedHash) + " n read: " + nrRead + ", sector: " + sectorNr + ", blockid: " + blockId);
					Log.log(LogLevel.LOG_EMERG, "last sector written: " + lastSectorWritten + ", last write action: " + lastWriteAction + ", last writing thread: " + (lastWriteThread != null ? lastWriteThread.getName() : "?"));
					Log.log(LogLevel.LOG_EMERG, "sector read from: " + readFrom);
					byte [] action = testModeAction.retrieveElement(sectorNr, true, -1);
					Log.log(LogLevel.LOG_EMERG, "action on this sector: " + action[0]);
					// Log.log(LogLevel.LOG_EMERG, "current: " + Utils.byteArrayToHexString(data));
					throw new VHException("corrupt data");
					// System.exit(1);
				}
			}
		}
	}
}
