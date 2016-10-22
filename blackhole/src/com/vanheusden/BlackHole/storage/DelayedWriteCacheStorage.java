/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.cache.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.zip.DataFormatException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class DelayedWriteCacheStorage extends DelayedWriteCache {
	static String version = "$Id$";
	Storage s = null;

	public DelayedWriteCacheStorage(String what, Storage s, WritecacheParameters wcp) {
		this.s = s;
		this.what = what;
		this.wcp = wcp;

		init();
	}

	protected void putLow(long sectorNr, byte [] data, byte [] hash) throws IOException, VHException, SQLException, BadPaddingException, IllegalBlockSizeException, DataFormatException {
		assert checkWriteLocked();
		s.putBlockLow(sectorNr, data, hash);
	}

	protected boolean checkReadLocked() {
		return s.checkReadLocked();
	}

        protected boolean checkWriteLocked() {
		return s.checkWriteLocked();
        }

	protected void writeLock() {
		s.writeLock();
	}

	protected void writeUnlock() {
		s.writeUnlock();
	}
}
