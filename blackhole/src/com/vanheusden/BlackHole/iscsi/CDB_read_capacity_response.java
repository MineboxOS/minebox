/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.util.Arrays;

public class CDB_read_capacity_response extends CDB{
	byte [] bytes;
	byte [] data;

	public CDB_read_capacity_response(CDB in) {
		System.out.println("CDB: " + Arrays.toString(in.bytes));
		bytes = in.bytes;
		data = in.data;
	}

	public int getLBA() {
		byte [] lba = new byte[4];
		System.arraycopy(data, 0, lba, 0, 4);
		return Utils.byteArrayToInt(lba);
	}

	public int getBlockSize() {
		byte [] bs = new byte[4];
		System.arraycopy(data, 4, bs, 0, 4);
		return Utils.byteArrayToInt(bs);
	}
}
