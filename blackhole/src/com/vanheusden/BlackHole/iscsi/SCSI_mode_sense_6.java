/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.util.Arrays;

public class SCSI_mode_sense_6 extends SCSI_response {
	public SCSI_mode_sense_6(long len, int blockSize, int pageCode) {
		if (pageCode == 8) {
			bytes = new byte[12 + 20];
			bytes[0] = 12 + 20 - 1;
		}
		else {
			bytes = new byte[12];
			bytes[0] = 12 - 1;
		}

		int lb = (int)(len / (long)blockSize);
		System.out.println("blocks: " + lb);

		bytes[3] = 8;

		byte [] capacity = Utils.intToByteArray(lb);
		Utils.arrayCopy(bytes, 4, capacity);
		bytes[4] = 0;

		byte [] bs = Utils.intToByteArray(blockSize);
		Utils.arrayCopy(bytes, 8, bs);
		bytes[8] = 0;

		if (pageCode == 8) {		// Caching mode page
			bytes[12 +  0] = 0x08;	// page code
			bytes[12 +  1] = 18;	// page length
			bytes[12 +  2] = (byte)128;	// IC=1
			bytes[12 + 13] = 1;	// number of cache segments
			bytes[12 + 14] = (byte)(blockSize >> 8);
			bytes[12 + 15] = (byte)(blockSize & 255);
		}
	}
}
