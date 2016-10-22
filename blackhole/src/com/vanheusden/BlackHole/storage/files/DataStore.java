/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage.files;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.storage.*;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class DataStore {
	static String version = "$Id: DataStore.java 606 2013-07-06 22:07:22Z folkert $";
	TableOnDisk dataBlocks;
	TreeOnDisk hashTree;
	long dataStoreSize, nBlocks;;
	int blockSize;
	long lastSearched = 0;
	int hashSize = -1;
	String hashName = null;
	MessageDigest md;
	boolean isFsck;

	public void delete() {
		dataBlocks.delete();
		hashTree.delete();
	}

	public void growSize(long newSize) throws IOException {
		dataBlocks.growSize(newSize / blockSize);
		hashTree.growSize(newSize / blockSize);
	}

	public DataStore(long dataStoreSize, int blockSize, String dataFilename, String hashTableFilename, String hashBitmapFilename, String topNodeFilename, HashType hashType, EncryptionParameters ep, boolean isFsck) throws IOException, VHException, NoSuchAlgorithmException {
		this.isFsck = isFsck;

		hashName = hashType.getName();
                md = MessageDigest.getInstance(hashName);
		hashSize = md.getDigestLength();

		nBlocks = dataStoreSize/blockSize;
		dataBlocks = new TableOnDisk(dataFilename, blockSize, nBlocks, ep);
		hashTree = new TreeOnDisk(hashTableFilename, hashBitmapFilename, topNodeFilename, nBlocks, hashSize, isFsck);

		this.dataStoreSize = dataStoreSize;
		this.blockSize = blockSize;
	}

	public boolean fsck() throws IOException, VHException {
		boolean allFine = true;
		TreeSet<Long> check = new TreeSet<Long>();
//boolean valid = false;
		System.out.print("FSCK, hash-tree (1): ");
		System.out.flush();
		boolean valid = !hashTree.checkTreeForError(-1, check);
		if (!valid)
			allFine = false;
		System.out.println(" " + valid);
		System.out.print("FSCK, hash-tree (2): ");
		System.out.flush();
		valid = hashTree.checkTreeForError2(check);
		if (!valid)
			allFine = false;
		System.out.println(" " + valid);

		System.out.print("FSCK, hashes: ");
		System.out.flush();
		valid = true;
		for(long element=0; element<nBlocks; element++) {
			if (hashTree.hasElement(element) == false)
				continue;

			byte [] valueTree = hashTree.retrieveElement(element);
			byte [] valueData = calcHash(dataBlocks.retrieveElement(element, true, -1));
			if (Utils.compareValues(valueTree, valueData) != 0) {
				valid = false;
				System.out.print("" + element + " ");
				System.out.flush();
			}
		}
		System.out.println(" " + valid);
		if (!valid)
			allFine = false;

		return allFine;
	}

	public boolean hasElement(long blocknr) throws IOException, VHException {
		return hashTree.hasElement(blocknr);
	}

	public int getHashSize() {
		return hashSize;
	}

	public void dumpTree(String fileName) throws IOException, VHException {
		hashTree.dump(fileName);
	}

	public void dumpTreeNode(long node) throws IOException, VHException {
		hashTree.dumpNode(node);
	}

	public void flush() throws IOException, VHException {
		dataBlocks.flush();
		hashTree.flush();
	}

	public void close() throws IOException, VHException {
		Log.log(LogLevel.LOG_INFO, "Datastore: close data blocks store");
		dataBlocks.close();
		Log.log(LogLevel.LOG_INFO, "Datastore: close hash tree");
		hashTree.close();
	}

        public byte [] calcHash(byte [] data) {
                assert data.length == blockSize;

                byte [] hash = md.digest(data);
                assert hash.length == hashSize;

                return hash;
        }

	public long searchByHash(byte [] hash) throws IOException, VHException {
		assert hash.length == hashSize;

		Long [] rc = hashTree.findNode(hash);

		return rc[0];
	}

	public Long [] searchByHashWithStoreInfo(byte [] hash) throws IOException, VHException {
		assert hash.length == hashSize;

		return hashTree.findNode(hash);
	}

	public byte [] getHash(long blockNr) throws IOException, VHException {
		byte [] out = hashTree.retrieveElement(blockNr);
		return out;
	}

	public byte [] getData(long blockNr) throws IOException, VHException {
		byte [] out = dataBlocks.retrieveElement(blockNr, false, 7);
		return out;
	}

	public long putData(byte [] data) throws IOException, VHException {
		assert data.length == blockSize;

		long blockNr = hashTree.storeNode(calcHash(data));
		dataBlocks.storeElement(blockNr, data, 4);

		return blockNr;
	}

	public long putData(byte [] hash, byte [] data) throws IOException, VHException {
		assert data.length == blockSize;

		long blockNr = hashTree.storeNode(hash);
		dataBlocks.storeElement(blockNr, data, 4);

		return blockNr;
	}

	public long putData(byte [] hash, byte [] data, long toBlockId) throws IOException, VHException {
		long blockNr = hashTree.storeNode(hash, toBlockId);
		assert blockNr == toBlockId;

		dataBlocks.storeElement(blockNr, data, 4);

		return blockNr;
	}

	public long putData(long parent, byte [] hash, byte [] data) throws IOException, VHException {
		assert data.length == blockSize;
		assert parent != -1;

		long blockNr = hashTree.storeNode(parent, hash);
		dataBlocks.storeElement(blockNr, data, 4);

		return blockNr;
	}

	public long putData(long parent, byte [] hash, byte [] data, long toBlockId) throws IOException, VHException {
		long blockNr = hashTree.storeNode(parent, hash, toBlockId);
		assert blockNr == toBlockId;
		assert parent != -1;

		dataBlocks.storeElement(blockNr, data, 4);

		return blockNr;
	}

	public long updateData(long blockNr, byte [] data) throws IOException, VHException {
		return updateData(blockNr, calcHash(data), data);
	}

	public long updateData(long blockNr, byte [] hash, byte [] data) throws IOException, VHException {
		Long [] rc = hashTree.findNode(hash);
		long newBlockId = rc[0];
		if (newBlockId == -1) {
			hashTree.deleteNode(blockNr);
			newBlockId = hashTree.storeNode(hash);
			dataBlocks.storeElement(newBlockId, data, 5);
		}
		else if (newBlockId != blockNr) {
			hashTree.deleteNode(blockNr);
		}

		return newBlockId;
	}

	public long addData(long parent, byte [] hash, byte [] data) throws IOException, VHException {
		assert data.length == blockSize;
		long newBlockNr = -1;

		if (parent == -1)
			newBlockNr = putData(hash, data);
		else
			newBlockNr = putData(parent, hash, data);

		return newBlockNr;
	}

	public long addData(byte [] hash, byte [] data) throws IOException, VHException {
		assert data.length == blockSize;

		long newBlockNr = putData(hash, data);

		return newBlockNr;
	}

	public long addData(byte [] data) throws IOException, VHException {
		assert data.length == blockSize;

		byte [] hash = calcHash(data);

		long newBlockNr = putData(hash, data);

		return newBlockNr;
	}

	public void deleteBlock(long blockNr) throws IOException, VHException {
		hashTree.deleteNode(blockNr);

	}

	public void deleteBlock(byte [] hash) throws IOException, VHException {
                assert hash.length == hashSize;

		long node = hashTree.findNode(hash)[0];
		assert node != -1;
		hashTree.deleteNode(node);
	}
}
