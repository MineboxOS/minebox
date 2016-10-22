/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import java.io.InputStream;

public class PDU_0x05__data_inout__request extends PDU {
	public PDU_0x05__data_inout__request(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x05__data_inout__request() {
	}

	public boolean getF() {
		return getBit(1, 7);
	}

	public void setF(boolean to) {
		setBit(1, 7, to);
	}

	public long getLUN() {
		return getLong(8);
	}

	public void setLUN(long lun) {
		setLong(8, lun);
	}

	public int getInitiatorTaskTag() {
		return getInt(16);
	}

	public void setInitiatorTaskTag(int tag) {
		setInt(16, tag);
	}

	public int getTargetTransferTag() {
		return getInt(20);
	}

	public void setTargetTransferTag(int tag) {
		setInt(20, tag);
	}

	public int getExpStatSN() {
		return getInt(28);
	}

	public void setExpStatSN(int ess) {
		setInt(28, ess);
	}

	public int getDataSN() {
		return getInt(36);
	}

	public void setDataSN(int ess) {
		setInt(36, ess);
	}

	public long getBufferOffset() {
		return getInt(40);
	}

	public void setBufferOffset(int ess) {
		setInt(40, ess);
	}
}
