/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

public class CDB_read_capacity extends CDB{
	byte [] bytes = new byte[16];

	public CDB_read_capacity() {
		bytes[0] = 0x25;
	}

	public byte [] getAsByteArray() {
		return bytes;
	}
}
