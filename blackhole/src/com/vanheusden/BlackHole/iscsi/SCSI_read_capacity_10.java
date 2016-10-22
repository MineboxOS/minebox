/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.util.Arrays;

public class SCSI_read_capacity_10 extends SCSI_response {
	public SCSI_read_capacity_10(long len, int blockSize) {
		bytes = new byte[8];

		int lb = (int)(len / (long)blockSize);
System.out.println("blocks: " + lb);
		byte [] capacity = Utils.intToByteArray(lb);
		byte [] bs = Utils.intToByteArray(blockSize);

		Utils.arrayCopy(bytes, 0, capacity);
		Utils.arrayCopy(bytes, 4, bs);
	}
}
