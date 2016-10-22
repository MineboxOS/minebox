/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.io.InputStream;
import java.io.IOException;

public class PDU_0x02__taskman__request extends PDU {
	public PDU_0x02__taskman__request() {
		bytes[0] = 0x02;
	}

	public PDU_0x02__taskman__request(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x02__taskman__request(InputStream is) throws IOException {
		is.read(bytes);

		int nData = getDataSegmentLength();
		if (nData > 0) {
			data = new byte[nData];
			is.read(data);

			int remaining = nData & 3;
			if (remaining != 0) {
				byte [] rem = new byte[4 - remaining];
				is.read(rem);
			}
		}
	}

	public boolean getI() {
		return getBit(0, 6);
	}

	public void setI(boolean to) {
		setBit(0, 6, to);
	}

	public int getFunction() {
		return bytes[1] & 0x7f;
	}

	public String getFunctionString() {
		int function = bytes[1] & 0x7f;

		switch(function) {
			case 1:
				return "abort task";
			case 2:
				return "abort task set";
			case 3:
				return "clear aca";
			case 4:
				return "clear task set";
			case 5:
				return "logical unit reset";
			case 6:
				return "target warm reset";
			case 7:
				return "target cold reset";
			case 8:
				return "task reassing";
			default:
				return "unknown: " + function;
		}
	}

	public void setFunction(int status) {
		bytes[1] = (byte)(status | 128);
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

	public int getReferencedTaskTag() {
		return getInt(20);
	}

	public void setReferencedTaskTag(int tag) {
		setInt(20, tag);
	}

	public int getCmdSN() {
		return getInt(24);
	}

	public void setCmdSN(int what) {
		setInt(24, what);
	}

	public int getExpStatSN() {
		return getInt(28);
	}

	public void setExpStatSN(int what) {
		setInt(28, what);
	}

	public int getRefCmdSN() {
		return getInt(32);
	}

	public void setRefCmdSN(int what) {
		setInt(32, what);
	}

	public int getExpDataSN() {
		return getInt(36);
	}

	public void setExpDataSN(int what) {
		setInt(36, what);
	}
}
