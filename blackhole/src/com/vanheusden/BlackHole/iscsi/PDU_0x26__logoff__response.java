/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import java.io.InputStream;

public class PDU_0x26__logoff__response extends PDU {
	public PDU_0x26__logoff__response(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x26__logoff__response(int response, int time2wait, int time2retain) {
		setBit(1, 7, true);
		setTime2Wait(time2wait);
		setTime2Retain(time2retain);
	}

	public int getInitiatorTaskTag() {
		return getInt(16);
	}

	public void setInitiatorTaskTag(int tag) {
		setInt(16, tag);
	}

	public int getStatSN() {
		return getInt(24);
	}

	public void setStatSN(int what) {
		setInt(24, what);
	}

	public int getExpCmdSN() {
		return getInt(28);
	}

	public void setExpCmdSN(int what) {
		setInt(28, what);
	}

	public int getMaxCmdSN() {
		return getInt(32);
	}

	public void setMaxCmdSN(int what) {
		setInt(32, what);
	}

	public int getTime2Wait() {
		return ((bytes[40] & 0xff) << 8) + (bytes[41] & 0xff);
	}

	public void setTime2Wait(int time) {
		bytes[40] = (byte)(time >> 8);
		bytes[41] = (byte)(time & 0xff);
	}

	public int getTime2Retain() {
		return ((bytes[42] & 0xff) << 8) + (bytes[43] & 0xff);
	}

	public void setTime2Retain(int time) {
		bytes[42] = (byte)(time >> 8);
		bytes[43] = (byte)(time & 0xff);
	}
}
