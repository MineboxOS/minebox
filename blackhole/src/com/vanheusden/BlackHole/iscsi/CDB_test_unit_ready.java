/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

public class CDB_test_unit_ready extends CDB{
	byte [] bytes = new byte[16];

	public CDB_test_unit_ready(int lun) {
		bytes[0] = 0x00;
		assert lun >= 0 && lun <= 7;
		bytes[1] = (byte)lun;
	}

	public byte [] getAsByteArray() {
		return bytes;
	}
}
