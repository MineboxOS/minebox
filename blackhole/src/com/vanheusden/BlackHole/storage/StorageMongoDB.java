/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.cache.*;

import com.mongodb.Mongo;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

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

public class StorageMongoDB extends Storage {
	Mongo mongo = null;
	DB db = null;
	String mongoHost, mongoDb;
	DBCollection sectorMap, dataMap;
	WriteConcern wc = new WriteConcern(1);
	boolean debug = false;

	int hashSize = -1;
	String hashName = null;
	MessageDigest md;
	int blockLength = -1;
	Encryptor encryptorRead;
	Encryptor encryptorWrite;
	CompressionParameters compression;
	CRC32 crc32 = new CRC32();

	public void delete() throws VHException, IOException, SQLException {
		closeStorageBackend();

		mongo.dropDatabase(mongoDb);
	}

        public boolean fsck() throws BadPaddingException, IllegalBlockSizeException, DataFormatException {
		// FIXME

		return true;
	}

	protected Double getPercentageBlockUseDecrease() throws IOException, VHException, SQLException {
		return -1.0; // FIXME
	}

	public void closeStorageBackend() throws VHException, IOException, SQLException {
		super.closeStorageBackend();

		Log.log(LogLevel.LOG_INFO, "close");
// FIXME		if (!db.close())
			Log.log(LogLevel.LOG_WARN, "StorageMongoDB: failed closing database");

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

	public void testModeVerifyData(long sectorNr, byte [] data, int readFrom) throws IOException, VHException, SQLException {
		// FIXME
	}

	public StorageMongoDB(String mongoHost, String mongoDb, long fileSize, int blockSize, String dataPath, HashType hashType, ReadcacheParameters rcp, WritecacheParameters wcp, EncryptionParameters ep, boolean isFsck, CompressionParameters compression) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IOException, VHException, SQLException {
		super(dataPath, rcp, wcp, fileSize, blockSize, hashType);

		assert fileSize % blockSize == 0;

		this.mongoHost = mongoHost;
		this.mongoDb = mongoDb;

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

		mongo = new Mongo(mongoHost);
		if (!unsafe)
			mongo.setWriteConcern(WriteConcern.SAFE);
		db = mongo.getDB(mongoDb);
		sectorMap = db.getCollection("sectorMap");
		dataMap = db.getCollection("dataMap");

		sectorMap.createIndex(new BasicDBObject("sector", 1));
		sectorMap.createIndex(new BasicDBObject("hash", 1));
		dataMap.createIndex(new BasicDBObject("hash", 1));
	}

        public byte [] calcHash(byte [] data) {
                assert data.length == blockSize;

                byte [] hash = md.digest(data);
                assert hash.length == hashSize;

                return hash;
        }

	byte [] getHashForSector(long sectorNr) {
		assert sectorNr >= 0 && sectorNr < nBlocks;

                BasicDBObject query = new BasicDBObject();
                query.put("sector", sectorNr);

		DBCursor result = sectorMap.find(query);
		if (!result.hasNext())
			return null;

                return (byte [])result.next().get("hash");
	}

	long getHashUsageCount(byte [] hash) {
		assert hash.length == hashSize;

                BasicDBObject query = new BasicDBObject();
                query.put("hash", hash);

		DBCursor result = dataMap.find(query);
		if (!result.hasNext())
			return 0;

		BasicDBObject bdo = (BasicDBObject)result.next();
                return bdo.getLong("count");
	}

	void deleteHashDataPair(byte [] hash) throws VHException {
		assert hash.length == hashSize;

		BasicDBObject document = new BasicDBObject();
		document.put("hash", hash);

		WriteResult rc = dataMap.remove(document);
		if (rc.getN() != 1) {
			// FIXME error
			Log.log(LogLevel.LOG_WARN, "Failed removing data/hash pair: " + rc);
		}
	}

	void storeHashDataPair(byte [] hash, byte [] data) throws VHException {
                BasicDBObject document = new BasicDBObject();

                document.put("hash", hash);
		long count = 1;
                document.put("count", count);
                document.put("data", data);

		WriteResult rc = dataMap.save(document);
		// save is supposed to throw an exception if an error occured
		// getN() always returns 0?!
/*
		if (rc.getN() != 1) {
			// FIXME error
			Log.log(LogLevel.LOG_WARN, "Failed adding record: " + rc);

			try {
				if (getDataByHash(hash) == null)
					Log.log(LogLevel.LOG_WARN, "data was not stored");
			}
			catch(Exception e) {
				Log.log(LogLevel.LOG_WARN, "getDataByHash threw " + e);
			}
		}
*/
	}

	void mapSectorToHash(long sectorNr, byte [] hash) throws VHException {
		assert sectorNr >=0 && sectorNr < nBlocks;

                BasicDBObject search = new BasicDBObject();
                search.put("sector", sectorNr);

                BasicDBObject document = new BasicDBObject("$set", new BasicDBObject("hash", hash));

		WriteResult rc = sectorMap.update(search, document, true, false);
		if (rc.getN() == 0) {
			// FIXME error
			Log.log(LogLevel.LOG_WARN, "Expecting to automatically create a new sector mapping record but none was processed? sector: " + sectorNr + ", rc: " + rc);
		}
	}

	byte [] getDataByHash(byte [] hash) throws BadPaddingException, IllegalBlockSizeException, DataFormatException {
		assert hash.length == hashSize;

                BasicDBObject query = new BasicDBObject();
                query.put("hash", hash);

		DBCursor result = dataMap.find(query);
		if (!result.hasNext())
			return null;

                byte [] buffer = (byte [])result.next().get("data");

		if (encryptorRead != null)
			buffer = encryptorRead.decrypt(buffer);

		if (compression != null && compression.getType() != CompressionType.C_NONE)
			buffer = uncompress(compression.getType(), buffer, blockSize);

		assert buffer.length == blockSize;

		return buffer;
	}

	public void startTransaction() throws IOException, VHException, SQLException {
		// FIXME

		// it seems mongodb does not have transactions, this is the best
		// I could find
		db.requestStart();
	}

	public void commitTransaction() throws IOException, VHException, SQLException {
		// FIXME
		db.requestDone();
	}

	public void unmapSector(long sectorNr) throws VHException {
		byte [] hash = getHashForSector(sectorNr);

		// count how often the hash it uses is used
                BasicDBObject query = new BasicDBObject();
                query.put("hash", hash);

		long count = 0;
		DBCursor result = dataMap.find(query);
		if (result.hasNext()) {
			BasicDBObject bdo = (BasicDBObject)result.next();
                	count = bdo.getLong("count");
		}

		// delete mapping
		BasicDBObject document = new BasicDBObject();
		document.put("sector", sectorNr);

		sectorMap.remove(document);

		// delete data if usage count became 0
		// might already be 0: then the sector never was allocated to begin with
		if (count > 0) {
			long newCount = count - 1;
			updateCountAndDelete(hash, newCount);
		}
	}

	void updateCountAndDelete(byte [] hash, long count) {
		if (count == 0) {
			BasicDBObject query = new BasicDBObject();
			query.put("hash", hash);

			WriteResult rc = dataMap.remove(query);
			if (rc.getN() != 1) {
				// FIXME error
				Log.log(LogLevel.LOG_WARN, "Failed removing data/hash pair: " + rc);
			}
		}
		else {
			BasicDBObject search = new BasicDBObject();
			search.put("hash", hash);

			BasicDBObject document = new BasicDBObject("$set", new BasicDBObject("count", count));

			WriteResult rc = dataMap.update(search, document, false, false);
			if (rc.getN() == 0) {
				// FIXME failure
				Log.log(LogLevel.LOG_WARN, "Expecting to upgrade count in an existing record, none foud " + count + ": " + rc);
			}
		}
	}

	void putBlockLow(long sectorNr, byte [] data) throws VHException, BadPaddingException, IllegalBlockSizeException, DataFormatException, FileNotFoundException, IOException, SQLException {
		putBlockLow(sectorNr, data, calcHash(data));
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
			if (replacingData) {
				long prevHashCount = getHashUsageCount(prevHash);
				long newPrevHashCount = prevHashCount - 1;

				if (debug)
					System.out.println(" > replacing data, new count of old hash: " + newPrevHashCount);

				updateCountAndDelete(prevHash, newPrevHashCount);
			}

			long newHashCount = getHashUsageCount(newHash);
			long newNewHashCount = newHashCount + 1;

			if (debug)
				System.out.println(" > new count of new hash: " + newNewHashCount);

			// if the new count is 1, then the data was not already on disk
			// we're assuming here that it was previously completely written
			// to disk
			if (newNewHashCount == 1)
				storeHashDataPair(newHash, data);
			else
				updateCountAndDelete(newHash, newNewHashCount);
		}

		if (differentHash) {
			if (debug)
				System.out.println(" > mapping hash to sector");
			mapSectorToHash(sectorNr, newHash);
		}
	}

	protected byte [] readBlockLow(long sectorNr) throws BadPaddingException, IllegalBlockSizeException, DataFormatException {
		assert sectorNr >=0 && sectorNr < nBlocks;

		byte [] hash = getHashForSector(sectorNr);
		if (hash == null) // block not yet known
			return new byte[blockSize];

		return getDataByHash(hash);
	}

	static byte [] createBlock(String in) {
		byte [] out = new byte[4096];

		char [] in_char = in.toCharArray();
		for(int index=0; index<in_char.length; index++)
			out[index] = (byte)in_char[index];

		return out;
	}

	static void compareBlocks(byte [] b1, byte [] b2) {
		if (b1.length != b2.length)
			System.err.println("Blocks differ in size");

		int l = Math.min(b1.length, b2.length);

		int count = 0;
		for(int index=0; index<l; index++) {
			if (b1[index] != b2[index]) {
				if (++count >= 10) {
					System.err.println("...");
					break;
				}

				System.err.println("" + index + "] " + b1[index] + " / " + b2[index]);
			}
		}
	}

	public static void main(String [] args) throws Exception {
		StorageMongoDB s = new StorageMongoDB("localhost", "test", 16l * 1024 * 1024 * 1024, 4096, null, HashType.MD5, null, null, null, false, null);
		s.debug = true;

		boolean fail = false;

		System.out.println(" * put & get 1 block in empty storage at sector nr 0"); // ***
		byte [] block = createBlock("1");
		s.putBlockLow(0, block);

		byte [] block_get = s.readBlockLow(0);
		if (block_get == null) {
			System.err.println(" - Block for sector 0 not found!");
			fail = true;
		}
		else if (Utils.compareValues(block, block_get) != 0) {
			System.err.println(" - Read back block after simple put: contents differ");
			compareBlocks(block, block_get);
			fail = true;
		}

		System.out.println(" * verify that the data block can be found via its hash"); // ***
		byte [] calc_hash = s.calcHash(block);
		block_get = s.getDataByHash(calc_hash);
		if (block_get == null) {
			System.err.println(" - Block for hash not found!");
			fail = true;
		}
		else if (Utils.compareValues(block, block_get) != 0) {
			System.err.println(" - Read back block via hash: contents differ");
			compareBlocks(block, block_get);
			fail = true;
		}

		System.out.println(" * verify that the usage count of the hash is 1"); // ***
		long count = s.getHashUsageCount(calc_hash);
		if (count != 1) {
			System.err.println(" - usage count is: " + count);
			fail = true;
		}

		System.out.println(" * write another instance of that block on sector 1, will trigger de-dup"); // ***
		s.putBlockLow(1, block);

		System.out.println(" * verify that the usage count of the hash now is 2"); // ***
		count = s.getHashUsageCount(calc_hash);
		if (count != 2) {
			System.err.println(" - usage count is: " + count);
			fail = true;
		}

		System.out.println(" * verify that sector 1 can be read and contains valid data"); // ***
		block_get = s.readBlockLow(1);
		if (block_get == null) {
			System.err.println(" - Block for sector 1 not found!");
			fail = true;
		}
		else if (Utils.compareValues(block, block_get) != 0) {
			System.err.println(" - Read back block after dedup: contents differ");
			compareBlocks(block, block_get);
			fail = true;
		}

		System.out.println(" * see if a hash is stored at all"); // ***
		byte [] fake_hash = new byte [] { 1, 2, 3, 4 };
		s.mapSectorToHash(2, fake_hash);
		byte [] sm_hash = s.getHashForSector(2);
		if (sm_hash == null) {
			System.err.println(" - Hash for sector 2 not found!");
			fail = true;
		}
		else if (Utils.compareValues(fake_hash, sm_hash) != 0) {
			System.err.println(" - Hash in sector map differs (1)");
			compareBlocks(fake_hash, sm_hash);
			fail = true;
		}

		System.out.println(" * verify that the hash in the sectormap is the same as the one inserted"); // ***
		calc_hash = s.calcHash(block);
		sm_hash = s.getHashForSector(0);
		if (sm_hash == null) {
			System.err.println(" - Hash for sector 0 not found!");
			fail = true;
		}
		else if (Utils.compareValues(calc_hash, sm_hash) != 0) {
			System.err.println(" - Hash in sector map differs (2)");
			compareBlocks(calc_hash, sm_hash);
			fail = true;
		}

		if (!fail)
			System.out.println(" *** ALL FINE *** ");

		System.exit(0);
	}
}
