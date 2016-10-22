/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

public class CDB_request_sense extends CDB{
	byte [] bytes = new byte[16];

	public CDB_request_sense() {
		bytes[0] = 0x03;
	}

	public byte [] getAsByteArray() {
		return bytes;
	}
}
