/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.cache.*;

import kyotocabinet.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class StorageKC extends Storage {
	DB db = new DB();
	int hashSize = -1;
	String hashName = null;
	MessageDigest md;
	int blockLength = -1;
	String path = null;
	Encryptor encryptorRead;
	Encryptor encryptorWrite;
	CompressionParameters compression;
	CRC32 crc32 = new CRC32();
	char PREFIX_COUNT = 'c';
	char PREFIX_DATA  = 'd';
	String dbFile = "db.kch";

	public void delete() throws VHException, IOException, SQLException {
		closeStorageBackend();

		new File(path + "/" + dbFile).delete();
	}

        public boolean fsck() throws BadPaddingException, IllegalBlockSizeException, DataFormatException {
		long nBlocks = fileSize / blockSize;
		Log.log(LogLevel.LOG_INFO, "StorageKC: checking " + nBlocks + " blocks");

		long nSectorsMapped = 0, nErrors = 0;
		for(long sector=0; sector<nBlocks; sector++) {
			byte [] hash = getHashForSector(sector);
			if (hash == null)
				continue;

			nSectorsMapped++;

			if (getHashUsageCount(hash) <= 0) {
				Log.log(LogLevel.LOG_WARN, "StorageKC: sector " + sector + " maps to a hash with a usage count of 0!");
				nErrors++;
			}

			if (getDataByHash(hash) == null) {
				Log.log(LogLevel.LOG_WARN, "StorageKC: sector " + sector + " maps to a hash to which no data block is linked!");
				nErrors++;
			}
		}

		Log.log(LogLevel.LOG_INFO, "StorageKC: total of " + nSectorsMapped + " sectors mapped");
		Log.log(nErrors == 0 ? LogLevel.LOG_INFO : LogLevel.LOG_WARN, "StorageKC: total number of errors: " + nErrors);

		return nErrors == 0;
	}

	public void closeStorageBackend() throws VHException, IOException, SQLException {
		super.closeStorageBackend();

		Log.log(LogLevel.LOG_INFO, "close");
		if (!db.close())
			Log.log(LogLevel.LOG_WARN, "StorageKC: failed closing database");

		Log.log(LogLevel.LOG_INFO, "finished closeStorageBackend");
	}

	public void growDatastore(long newSize) {
		writeLock();
		assert (nBlocks * blockSize) == fileSize;
		long newNBlocks = newSize / blockSize;
		assert newNBlocks >= nBlocks;
		nBlocks = newNBlocks;
		assert fileSize <= newSize;
		fileSize = newSize;

		writeUnlock();
	}

	public StorageKC(long fileSize, int blockSize, String dataPath, HashType hashType, ReadcacheParameters rcp, WritecacheParameters wcp, EncryptionParameters ep, boolean isFsck, CompressionParameters compression) throws VHException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IOException, NoSuchAlgorithmException, SQLException {
		super(dataPath, rcp, wcp, fileSize, blockSize, hashType);

		assert fileSize % blockSize == 0;

		if (ep != null) {
			encryptorRead = new Encryptor(ep);
			encryptorWrite = new Encryptor(ep);
		}

		concurrentRead = true;

		this.compression = compression;

		hashName = hashType.getName();
                md = MessageDigest.getInstance(hashName);
		hashSize = md.getDigestLength();

		nBlocks = fileSize / blockSize;
		Log.log(LogLevel.LOG_INFO, "Size: " + nBlocks + " blocks");

		this.path = dataPath;

		// FIXME tree faster?
		boolean rc = db.open(path + "/" + dbFile, DB.OWRITER | DB.OREADER | DB.OCREATE);
		if (!rc)
			throw new VHException("Failed opening database: " + db.error().toString());
	}

        public byte [] calcHash(byte [] data) {
                assert data.length == blockSize;

                byte [] hash = md.digest(data);
                assert hash.length == hashSize;

                return hash;
        }

	byte [] getHashForSector(long sectorNr) {
		assert sectorNr >= 0 && sectorNr < nBlocks;

                byte [] sectorBytes = Utils.longToByteArray(sectorNr);
                return db.get(sectorBytes);
	}

	byte [] prefixHash(char prefix, byte [] in) {
		assert in.length == hashSize;
		byte [] out = new byte[in.length + 1];

		Utils.arrayCopy(out, 1, in);
		out[0] = (byte)prefix;

		return out;
	}

	long getHashUsageCount(byte [] hash) {
		assert hash.length == hashSize;
		byte [] result = db.get(prefixHash(PREFIX_COUNT, hash));
		if (result == null)
			return 0;

		return Utils.byteArrayToLong(result);
	}

	long [] getHashesUsageCounts(byte [][] hashes) throws VHException {
		int n = hashes.length;
		byte [][] phashes = new byte[n][];

		for(int index=0; index<n; index++) {
			assert hashes[index].length == hashSize;
			phashes[index] = prefixHash(PREFIX_COUNT, hashes[index]);
		}

		byte [][] result = db.get_bulk(phashes, false);
		if (result == null)
			throw new VHException("getHashesUsageCounts: no data returned" + db.error().toString());

		long [] out = new long[hashes.length];
		out[0] = out[1] = 0;
		for(int h_index=0; h_index<hashes.length; h_index++) {
			for(int r_index=0; r_index<result.length; r_index+=2) {
				if (Utils.compareValues(result[r_index], phashes[h_index]) == 0) {
					out[h_index] = Utils.byteArrayToLong(result[r_index + 1]);
					break;
				}
			}
		}

		return out;
	}

	void deleteHashDataPair(byte [] hash) throws VHException {
		assert hash.length == hashSize;
		boolean rc = db.remove(prefixHash(PREFIX_DATA, hash));
		if (!rc)
			throw new VHException("deleteHashDataPair: " + db.error().toString());
	}

	void deleteHashUsageCounter(byte [] hash) throws VHException {
		assert hash.length == hashSize;
		boolean rc = db.remove(prefixHash(PREFIX_COUNT, hash));
		if (!rc)
			throw new VHException("deleteHashUsageCounter: " + db.error().toString());
	}

	long decreaseHashUsageCount(byte [] hash) throws VHException {
		assert hash.length == hashSize;
		byte [] result = db.get(prefixHash(PREFIX_COUNT, hash));
		long count = Utils.byteArrayToLong(result);
		count --;
		assert count > 0;
		boolean rc = db.set(prefixHash(PREFIX_COUNT, hash), Utils.longToByteArray(count));
		if (!rc)
			throw new VHException("decreaseHashUsageCount: " + db.error().toString());
		return count;
	}

	void storeHashDataPairCountMapSector(long sectorNr, byte [] hash, byte [] elementIn) throws VHException {
		assert hash.length == hashSize;
		assert elementIn.length == blockSize;
		byte [] element = null;

		if (compression != null && compression.getType() != CompressionType.C_NONE)
			elementIn = compress(compression.getType(), compression.getLevel(), elementIn, blockSize);

		if (encryptorWrite != null) {
			try {
				elementIn = encryptorWrite.encrypt(elementIn);
			}
			catch(BadPaddingException pbe) { throw new VHException("Crypto exception: " + pbe.toString()); }
			catch(IllegalBlockSizeException ibse) { throw new VHException("Crypto exception: " + ibse.toString()); }
		}

		element = elementIn;

		byte [][] data = new byte[6][];
		data[0] = prefixHash(PREFIX_COUNT, hash);
		data[1] = Utils.longToByteArray(1);
		data[2] = prefixHash(PREFIX_DATA, hash);
		data[3] = element;
                data[4] = Utils.longToByteArray(sectorNr);
		data[5] = hash;

		long rc = db.set_bulk(data, false); // not atomic: is in a transaction
		if (rc != 3)
			throw new VHException("storeHashDataPair: expected store of 2, stored only " + rc + ", " + db.error().toString());
	}

	void setHashUsageCount(byte [] hash, long count) throws VHException {
		assert hash.length == hashSize;
		assert count >=0 && count < nBlocks;
		boolean rc = db.set(prefixHash(PREFIX_COUNT, hash), Utils.longToByteArray(count));
		if (!rc)
			throw new VHException("setHashUsageCount: " + db.error().toString());
	}

	void setHashUsageCountMapSector(long sectorNr, byte [] hash, long count) throws VHException {
		assert hash.length == hashSize;
		assert count >=0 && count < nBlocks;

		byte [][] data = new byte[4][];
		data[0] = prefixHash(PREFIX_COUNT, hash);
		data[1] = Utils.longToByteArray(count);
                data[2] = Utils.longToByteArray(sectorNr);
		data[3] = hash;

		long rc = db.set_bulk(data, false); // not atomic: is in a transaction
		if (rc != 2)
			throw new VHException("storeHashDataPair: expected store of 2, stored only " + rc + ", " + db.error().toString());
	}

	void increaseHashUsageCount(byte [] hash) throws VHException {
		assert hash.length == hashSize;
		byte [] result = db.get(prefixHash(PREFIX_COUNT, hash));
		long count = Utils.byteArrayToLong(result);
		count++;
		assert count <= nBlocks;
		boolean rc = db.set(prefixHash(PREFIX_COUNT, hash), Utils.longToByteArray(count));
		if (!rc)
			throw new VHException("increaseHashUsageCount: " + db.error().toString());
	}

	void mapSectorToHash(long sectorNr, byte [] hash) throws VHException {
		assert sectorNr >=0 && sectorNr < nBlocks;

                byte [] sectorBytes = Utils.longToByteArray(sectorNr);
		boolean rc = db.set(sectorBytes, hash);
		if (!rc)
			throw new VHException("increaseHashUsageCount: " + db.error().toString());
	}

	byte [] getDataByHash(byte [] hash) throws BadPaddingException, IllegalBlockSizeException, DataFormatException {
		assert hash.length == hashSize;

		byte [] buffer = db.get(prefixHash(PREFIX_DATA, hash));
		if (buffer == null)
			return null;

		if (encryptorRead != null)
			buffer = encryptorRead.decrypt(buffer);

		if (compression != null && compression.getType() != CompressionType.C_NONE)
			buffer = uncompress(compression.getType(), buffer, blockSize);

		assert buffer.length == blockSize;

		return buffer;
	}

	public void unmapSector(long sectorNr) throws VHException {
		// remove sector/hash mapping
                byte [] sectorBytes = Utils.longToByteArray(sectorNr);
                byte [] thisSectorHash = db.get(sectorBytes);

		if (thisSectorHash != null) {
			db.remove(sectorBytes);

			// decrease hash usage counter and if it becomes zero,
			// then delete the data block as well
			if (decreaseHashUsageCount(thisSectorHash) == 0)
				delete_HashDataPair_HashUsageCounter(thisSectorHash);
		}
	}

	public void delete_HashDataPair_HashUsageCounter(byte [] hash) throws VHException {
		byte [][] keys = new byte[2][];

		keys[0] = prefixHash(PREFIX_DATA, hash);
		keys[1] = prefixHash(PREFIX_COUNT, hash);

		// atomic set to false: we're in a transaction
		long rc = db.remove_bulk(keys, false);
		if (rc != 2)
			throw new VHException("delete_HashDataPair_HashUsageCounter: while deleting only " + rc + " out of 2, " + db.error().toString());
	}

	public void testModeVerifyData(long sectorNr, byte [] data, int readFrom) throws IOException, VHException, SQLException {
		// FIXME
	}

	protected Double getPercentageBlockUseDecrease() throws IOException, VHException, SQLException {
		return -1.0; // FIXME
	}

	void putBlockLow(long sectorNr, byte [] data, byte [] newHash) throws VHException, BadPaddingException, IllegalBlockSizeException, DataFormatException, FileNotFoundException, IOException, SQLException {
		assert sectorNr >=0 && sectorNr < nBlocks;
		assert data.length == blockSize;
		assert newHash.length == hashSize;

		byte [] prevHash = getHashForSector(sectorNr);
		boolean newSector = prevHash == null;
		boolean differentHash = newSector || Utils.compareValues(prevHash, newHash) != 0;
		boolean replacingData = newSector ? false : differentHash;

		// if prev_hash != new_hash || new_sector
		//	if !new_sector
		// 		check if hash usage count of 'previous hash' is journal-1
		// 	check if hash usage count of 'new hash' is journal+1
		// check if sector is mapped to hash
		// check if hash is mapped to data
		// delete journal

		if (newSector || replacingData) {
			long [] usageCounts = null;
			long prevHashCount = -1, newHashCount = -1;

			if (replacingData) {
				usageCounts = getHashesUsageCounts(new byte[][] { prevHash, newHash });
				prevHashCount = usageCounts[0];
				newHashCount  = usageCounts[1];

				long newPrevHashCount = prevHashCount - 1;

				if (newPrevHashCount == 0)
					delete_HashDataPair_HashUsageCounter(prevHash);
				else
					setHashUsageCount(prevHash, newPrevHashCount);
			}
			else {
				newHashCount = getHashUsageCount(newHash);
			}

			long newNewHashCount = newHashCount + 1;
			// if the new count is 1, then the data was not already on disk
			// we're assuming here that it was previously completely written
			// to disk
			if (newNewHashCount == 1)
				storeHashDataPairCountMapSector(sectorNr, newHash, data);
			else
				setHashUsageCountMapSector(sectorNr, newHash, newNewHashCount);
		}
	}

	public void startTransaction() throws IOException, VHException, SQLException {
		if (!unsafe)
			db.begin_transaction(true);
	}

	public void commitTransaction() throws IOException, VHException, SQLException {
		if (!unsafe)
			db.end_transaction(true);
	}

	protected byte [] readBlockLow(long sectorNr) throws BadPaddingException, IllegalBlockSizeException, DataFormatException, DataFormatException {
		assert sectorNr >=0 && sectorNr < nBlocks;

		byte [] hash = getHashForSector(sectorNr);
		if (hash == null) // block not yet known
			return new byte[blockSize];

		assert getHashUsageCount(hash) > 0;
		return getDataByHash(hash);
	}
}
