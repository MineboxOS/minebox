/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

public class CDB_inquiry_request extends CDB{
	byte [] bytes = new byte[16];

	public CDB_inquiry_request(int allocationLength) {
		bytes[0] = 0x12;
		bytes[3] = (byte)(allocationLength >> 8);
		bytes[4] = (byte)(allocationLength & 0xff);
	}

	public byte [] getAsByteArray() {
		return bytes;
	}
}
