/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

public class CDB_write10 extends CDB{
	byte [] bytes = new byte[16];

	public CDB_write10(long blockNr, int targetBlockSize, int writeBlockSize) {
		bytes[0] = 0x2a;
		bytes[2] = (byte)((blockNr >> 24) & 255);
		bytes[3] = (byte)((blockNr >> 16) & 255);
		bytes[4] = (byte)((blockNr >>  8) & 255);
		bytes[5] = (byte)(blockNr & 255);
		int blocks = writeBlockSize / targetBlockSize;
		bytes[7] = (byte)((blocks >> 8) & 255);
		bytes[8] = (byte)(blocks & 255);
	}

	public byte [] getAsByteArray() {
		return bytes;
	}
}
