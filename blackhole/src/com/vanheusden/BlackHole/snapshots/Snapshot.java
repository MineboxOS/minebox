/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.snapshots;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.storage.*;
import com.vanheusden.BlackHole.storage.files.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.SQLException;
import java.util.concurrent.Semaphore;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import java.security.NoSuchAlgorithmException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.InvalidAlgorithmParameterException;

public class Snapshot implements Runnable {
	static String version = "$Id: Snapshot.java 606 2013-07-06 22:07:22Z folkert $";
        private final Semaphore sem = new Semaphore(1, true);
	//
	Bitmap mmjb;
	String mmjbFilename;
	int blockSize = -1;
	long totalNBlocks = -1;
	long lunSize = -1;
	//
	RandomAccessFile fileHandle;
	String snapshotFile;
	long snapshotPointer = 0;
	boolean finished = false;
	//
	Storage sp;
	int lun;
	SectorMapper sm;
	Encryptor encryptorWrite;
	EncryptionParameters ep;

        public void lock() {
                sem.acquireUninterruptibly();
        }

        public void unlock() {
                sem.release();
        }

	public Snapshot(String mmJournalBitmap, long lunSize, int blockSize, Storage storageCallBack, SectorMapper sm, String snapshotFile, int lun, EncryptionParameters ep) throws IOException, VHException {
		this.lunSize = lunSize;
		sp = storageCallBack;
		this.sm = sm;
		this.lun = lun;

                if (ep != null) {
			this.ep = ep;
                        try {   
                                encryptorWrite = new Encryptor(ep);
                        }
                        catch(NoSuchAlgorithmException nsae) { throw new VHException("Crypto exception: " + nsae.toString()); }
                        catch(NoSuchPaddingException nspe) { throw new VHException("Crypto exception: " + nspe.toString()); }
                        catch(InvalidKeyException ike) { throw new VHException("Crypto exception: " + ike.toString()); }
                        catch(InvalidAlgorithmParameterException iape) { throw new VHException("Crypto exception: " + iape.toString()); }
                }

		totalNBlocks = lunSize / blockSize;
		mmjb = new Bitmap(mmJournalBitmap, totalNBlocks);
		mmjbFilename = mmJournalBitmap;
		this.blockSize = blockSize;

		fileHandle = new RandomAccessFile(snapshotFile, "rw");
		fileHandle.setLength(lunSize);
		this.snapshotFile = snapshotFile;
		Log.log(LogLevel.LOG_INFO, "Snapshot " + snapshotFile + " setup");
	}

	public EncryptionParameters getEncryptionParameters() {
		return ep;
	}

	public String getFilename() {
		return snapshotFile;
	}

	public int getLun() {
		return lun;
	}

	public long getBytesLeft() {
		lock();
		long bytesLeft = lunSize - snapshotPointer;
		unlock();
		return bytesLeft;
	}

	public boolean isFinished() {
		lock();
		boolean copy = finished;
		unlock();
		return copy;
	}

	public static boolean all0x00(byte [] data) {
		for(int index=0; index<data.length; index++) {
			if (data[index] != 0)
				return false;
		}

		return true;
	}

	public void queue(long offset) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException {
		lock();

		long blockNr = offset / blockSize;

		if (offset < snapshotPointer || mmjb.getBit(blockNr) == true) {
			// already snapshotted
		}
		else {
			byte [] blData = sp.readBlock(sm.getMapOffset(lun, offset) / blockSize);
			if (!all0x00(blData)) {
				fileHandle.seek(offset);
				fileHandle.write(blData);
			}

			mmjb.setBit(blockNr, true);
		}

		unlock();
	}

	public void run() {
		Log.log(LogLevel.LOG_INFO, "Snapshot " + snapshotFile + " started");

		long progressThreshold = lunSize / 10;
		long lastProgress = -1;

		long startedAt = System.currentTimeMillis();

		for(;;) {
			try {
				lock();
				if (snapshotPointer == lunSize) {
					fileHandle.close();
					mmjb.close();
					new File(mmjbFilename).delete();
					finished = true;
					unlock();
					Log.log(LogLevel.LOG_INFO, "Snapshot " + snapshotFile + " finished");
					break;
				}

				long curBlockNr = snapshotPointer / blockSize;
				if (mmjb.getBit(curBlockNr) == false) {
					byte [] blData = sp.readBlock(sm.getMapOffset(lun, snapshotPointer) / blockSize);

					fileHandle.seek(snapshotPointer);

					if (encryptorWrite != null) {
						try {   
							fileHandle.write(encryptorWrite.encrypt(blData));
						}
						catch(BadPaddingException pbe) { throw new VHException("Crypto exception: " + pbe.toString()); }
						catch(IllegalBlockSizeException ibse) { throw new VHException("Crypto exception: " + ibse.toString()); }
					}
					else {
						if (!all0x00(blData)) {
							fileHandle.write(blData);
						}
					}

					mmjb.setBit(curBlockNr, true);
				}

				long progress = snapshotPointer / progressThreshold;
				if (progress != lastProgress) {
					long bytesLeft = lunSize - snapshotPointer;
					double runTime = (System.currentTimeMillis() - startedAt) / 1000.0;
					long MBps = (long)((snapshotPointer / (1024.0 * 1024.0)) / Math.max(1.0, runTime));
					int eta = (int)Math.ceil((bytesLeft / (1024.0 * 1024.0)) / (double)MBps);
					Log.log(LogLevel.LOG_INFO, "Snapshot " + snapshotFile + " progress: " + (bytesLeft / blockSize) + " blocks left (" + (bytesLeft / (1024 * 1024)) + "MB), " + MBps + "MB/s, run time: " + runTime + "s, eta: " + eta + "s");
					lastProgress = progress;
				}

				snapshotPointer += blockSize;
				unlock();
			}
			catch(Exception e) { // OK
				Log.showException(e);
				Log.log(LogLevel.LOG_CRIT, "Aborting snapshot! " + snapshotFile);
				break;
			}
		}
	}
}
