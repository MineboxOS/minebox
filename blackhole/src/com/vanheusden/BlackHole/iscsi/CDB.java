/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

public class CDB {
	byte [] bytes = new byte[16];
	byte [] data;

	public CDB() {
	}

	public int getOpcode() {
		return bytes[0] & 0xff;
	}

	public int getByte(int nr) {
		return bytes[nr] & 0xff;
	}

	public int getDataByte(int nr) {
		return data[nr] & 0xff;
	}

	public byte [] getAsByteArray() {
		return bytes;
	}

	public byte [] getDataAsByteArray() {
		return bytes;
	}
}
