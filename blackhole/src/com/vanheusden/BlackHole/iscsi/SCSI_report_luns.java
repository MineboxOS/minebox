/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.util.Arrays;

public class SCSI_report_luns extends SCSI_response {
	public SCSI_report_luns() {
		bytes = new byte[16];

		byte [] nLUNs = Utils.intToByteArray(1);

		Utils.arrayCopy(bytes, 0, nLUNs);
	}
}
