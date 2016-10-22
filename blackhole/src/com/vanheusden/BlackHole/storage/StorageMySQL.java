/* Released under GPL 2.0
 * (C) 2010-2013 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.cache.*;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class StorageMySQL extends Storage {
        Connection con;

	PreparedStatement psSectorHashMapping_check;
	PreparedStatement psSectorHashMapping_insert;
	PreparedStatement psSectorHashMapping_update;

	PreparedStatement psHashDataCountMap_insert;

	PreparedStatement psGetDataByHash;
	PreparedStatement psGetDataBySectorNr;

	PreparedStatement psGetUsageCountByHash;

	PreparedStatement psUpdateUsageCount;

	PreparedStatement psDeleteData;

	Encryptor encryptorRead;
	Encryptor encryptorWrite;
	//
	CompressionParameters compression;

        String hashName = null;
        MessageDigest md = null;
	int hashSize;

	public void delete() throws SQLException {
		// FIXME
	}

        private void emitDBWarnings(Statement stmt) throws SQLException {
                SQLWarning warning = stmt.getWarnings();

                if (warning != null) {
                        Log.log(LogLevel.LOG_WARN, "\n---Warning---\n");

                        while (warning != null) {
                                Log.log(LogLevel.LOG_WARN, "Message: " + warning.getMessage());
                                Log.log(LogLevel.LOG_WARN, "SQLState: " + warning.getSQLState());
                                Log.log(LogLevel.LOG_WARN, "Vendor error code: " + warning.getErrorCode());
                                warning = warning.getNextWarning();
                        }
                }
        }

        protected byte [] calcHash(byte [] data) {
                assert data.length == blockSize;

                byte [] hash = md.digest(data);
                assert hash.length == hashSize;

                return hash;
        }

	protected byte [] readBlockLow(long sectorNr) throws VHException, SQLException, DataFormatException {
/* FIXME
		assert sectorNr != -1;

		psGetDataBySectorNr.setLong(1, sectorNr);
                ResultSet getDataRS = psReadBlockLow.executeQuery();

		emitDBWarnings(psGetDataBySectorNr);

                if (getDataRS.next()) {
			byte [] dataIn = getDataRS.getBytes("data");

			getDataRS.close();

			if (encryptorRead != null) {
				try {
					dataIn = encryptorRead.decrypt(dataIn);
				}
				catch(BadPaddingException pbe) { throw new VHException("Crypto exception: " + pbe.toString()); }
				catch(IllegalBlockSizeException ibse) { throw new VHException("Crypto exception: " + ibse.toString()); }
			}

			if (compression != null && compression.getType() != CompressionType.C_NONE)
				dataIn = uncompress(compression.getType(), dataIn, blockSize);

			assert dataIn.length == blockSize;

			return dataIn;
		}

		getDataRS.close();
*/
		return new byte[blockSize];
	}

	byte [] getHashForSector(long sectorNr) throws SQLException {
		psSectorHashMapping_check.setLong(1, sectorNr);
                ResultSet getHashRS = psSectorHashMapping_check.executeQuery();

		emitDBWarnings(psSectorHashMapping_check);

                if (getHashRS.next())
			return getHashRS.getBytes("hash");

		return null;
	}

	protected Double getPercentageBlockUseDecrease() throws IOException, VHException, SQLException {
		return -1.0; // FIXME
	}

	public void testModeVerifyData(long sectorNr, byte [] data, int readFrom) throws IOException, VHException, SQLException {
		// FIXME
	}

	public void unmapSector(long sector) throws IOException, VHException, SQLException {
		// FIXME
		// SELECT count, hash FROM sm, dm WHERE sm.hash=dm.hash AND sectornr=?
		// if (hash != null) {
			// DELETE FROM sm WHERE sector=?

			// decrease hash usage counter and if it becomes zero,
			// then delete the data block as well
			// count--;
			// if (count == 0)
				// DELETE from dm WHERE hash=?
		// }
	}

	void putBlockLow(long sectorNr, byte [] data, byte [] newHash) throws VHException, BadPaddingException, IllegalBlockSizeException, DataFormatException, IOException, SQLException {
		assert sectorNr >=0 && sectorNr < nBlocks;
		assert data.length == blockSize;
		assert newHash.length == hashSize;

		// table sm:
		//	sector_nr bigint(16) not null,
		//	hash binary(128) not null,
		//	primary key(sector_nr),
		//	index(hash)

		// table dm:
		//	hash binary(128) not null,
		//	count bigint(16) not null,
		//	data blob not null,
		//	primary key(hash)

		// function put_block(long sectornr_in, byte [] data_in, byte [] hash_in)
		// {
		// 	SELECT count AS prev_count, hash AS previous_hash FROM sm, dm WHERE sm.hash=dm.hash AND sectornr=sectornr_in FOR UPDATE (*1)

		// 	if (previous_hash == null || previous_hash != hash_in) {
		// 		if prev_count == 1
		//			DELETE from dm WHERE hash=previous_hash					(*2)
		//
		//		SELECT new_count FROM dm WHERE hash=hash_in FOR UPDATE;				(*3)
		//		if (new_count == null)
		//			INSERT INTO dm(hash, count, data) VALUES(hash_in, 1, data_in)		(*4)
		//		else
		//			UPDATE dm SET count=count+1 WHERE hash=hash_in;				(*5)
		// 	}

		// 	if previous_count == null
		//		INSERT INTO sm(sectornr, hash) VALUES(sectornr_in, hash_in);			(*6)
		// 	else
		//		UPDATE sm SET hash=hash_in WHERE sectornr=sectornr_in				(*7)
		// }


		/* best case: 4
		   	1, 3, 4/5, 6/7
		   worst case: 5
			1, 2, 3, 4/5, 6/7
		 */
	}

	public void growDatastore(long newSize) {
		writeLock();
		long newNBlocks = newSize / blockSize;
		assert newNBlocks >= nBlocks;
		nBlocks = newNBlocks;
		fileSize = newSize;
		writeUnlock();
	}

	public StorageMySQL(long fileSize, int blockSize, String dataPath, String dbUrl, String dbUser, String dbPass, HashType hashType, WritecacheParameters wcp, ReadcacheParameters rcp, EncryptionParameters ep, boolean isFsck, CompressionParameters compression) throws VHException, IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, SQLException, InvalidAlgorithmParameterException, NoSuchAlgorithmException {
		super(dataPath, rcp, wcp, fileSize, blockSize, hashType);

		if (ep != null) {
			encryptorRead = new Encryptor(ep);
			encryptorWrite = new Encryptor(ep);
		}

		this.compression = compression;

                hashName = hashType.getName();
                md = MessageDigest.getInstance(hashName);
                hashSize = md.getDigestLength();

		concurrentRead = true;

		assert fileSize % blockSize == 0;

		con = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                con.setAutoCommit(false);

		psSectorHashMapping_check = con.prepareStatement("SELECT hash FROM sm WHERE sectornr=? LIMIT 1");
		psSectorHashMapping_insert = con.prepareStatement("INSERT INTO sm(sectornr, hash) VALUES(?, ?)");
		psSectorHashMapping_update = con.prepareStatement("UPDATE sm SET hash=? WHERE sectornr=? LIMIT 1");

		psHashDataCountMap_insert = con.prepareStatement("INSERT INTO dm(hash, usage_count, data) VALUES(?, 1, ?)");

		psGetDataByHash = con.prepareStatement("SELECT data FROM dm WHERE hash=? LIMIT 1");
		psGetDataBySectorNr = con.prepareStatement("SELECT data FROM dm, sm WHERE dm.hash=sm.hash AND sm=? LIMIT 1");

		psGetUsageCountByHash = con.prepareStatement("SELECT count FROM dm WHERE hash=? LIMIT 1");

		psUpdateUsageCount = con.prepareStatement("UPDATE dm SET count=? WHERE hash=? LIMIT 1");

		psDeleteData = con.prepareStatement("DELETE FROM dm WHER hash=? LIMIT 1");

		this.dataPath = dataPath;
	}

	public boolean fsck() throws SQLException {
		boolean ok = true;

		return ok;
	}

	public void closeStorageBackend() throws VHException, IOException, SQLException {
		super.closeStorageBackend();
                con.setAutoCommit(true);
                con.close();
		Log.log(LogLevel.LOG_INFO, "finished closeStorageBackend");
        }

        public void startTransaction() throws IOException, VHException, SQLException {
        }

        public void commitTransaction() throws IOException, VHException, SQLException {
		con.commit();
        }
}
