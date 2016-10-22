/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import java.io.InputStream;

public class PDU_0x06__logoff__request extends PDU {
	public PDU_0x06__logoff__request(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x06__logoff__request(int reason) {
		setBit(1, 7, true);
	}

	public boolean getI() {
		return getBit(0, 6);
	}

	public void setI(boolean what) {
		setBit(0, 6, what);
	}

	public int getInitiatorTaskTag() {
		return getInt(16);
	}

	public void setInitiatorTaskTag(int tag) {
		setInt(16, tag);
	}

	public int getCID() {
		return (bytes[20] & 0xff) << 8 + (bytes[21] & 0xff);
	}

	public void setCID(int value) {
		bytes[20] = (byte)(value >> 8);
		bytes[21] = (byte)(value & 0xff);
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
}
