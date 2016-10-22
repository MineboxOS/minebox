/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.mirrors;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.storage.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.concurrent.Semaphore;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public abstract class Mirror implements Runnable {
	static String version = "$Id: Mirror.java 606 2013-07-06 22:07:22Z folkert $";
	Storage sp;
	String host;
	int port;
	int lun;
	SectorMapper sm;
	String filenameBase;
	final String syncOffsetFile = "-sync_offset";
	byte [] empty = null;
	//
	boolean resync = false;
	long resyncOffset = 0;
        final Semaphore resyncSem = new Semaphore(1, true);
	long resyncProgressThreshold = -1;
	long resyncLastProgress = -1;
	long resyncStartedAt = 0;

        public void setCallBack(Storage s) {
		sp = s;

		triggerStart();
        }

	public int getLun() {
		return lun;
	}

	abstract public long getPendingBlockCount() throws IOException;

	abstract protected void triggerStart();

	abstract public boolean isSynchronous();

	abstract public void resyncAllData() ;

	abstract public long getResyncOffset();

	public void loadSyncState() throws IOException {
		try {
			resyncSem.acquireUninterruptibly();

			BufferedReader input =  new BufferedReader(new FileReader(filenameBase + syncOffsetFile));

			resync = true;
			resyncOffset = Long.valueOf(input.readLine());

			input.close();
		}
		catch(FileNotFoundException fnfe) {
			Log.log(LogLevel.LOG_WARN, "Mirror: were not syncing");
		}
		finally {
			resyncSem.release();
		}
	}

	public void storeSyncOffset() {
		Log.log(LogLevel.LOG_DEBUG, "Mirror store sync offset to " + filenameBase + syncOffsetFile);

		try {
			Writer out = new OutputStreamWriter(new FileOutputStream(filenameBase + syncOffsetFile));
			out.write("" + resyncOffset);
			out.close();
		}
		catch(Exception e) {
			Log.log(LogLevel.LOG_ERR, "Mirror: storeSyncOffset failed because " + e);
		}
	}

	public void deleteSyncOffset() {
		new File(filenameBase + syncOffsetFile).delete();
	}

	abstract public boolean isSyncing();

	abstract public void flushBacklog() throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException;

	abstract public void queue(long offset, byte [] data) throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException;

	abstract public boolean transmitBlock(long offset, byte [] data) throws IOException, VHException;

	abstract public void close() throws IOException, VHException, SQLException, DataFormatException, BadPaddingException, IllegalBlockSizeException;

	abstract public void run();

	public String toString() {
		return host + "-" + port;
	}

	public boolean isAll0x00(byte [] data) {
		assert data.length == empty.length;

		return Arrays.equals(data, empty);
	}
}
