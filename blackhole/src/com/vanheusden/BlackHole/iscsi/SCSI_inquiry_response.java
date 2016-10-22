/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.util.Arrays;

public class SCSI_inquiry_response extends SCSI_response {
	public SCSI_inquiry_response(String strVendor, String strProduct, String strVersion) {
		bytes = new byte[36];

		bytes[2] = 4;
		bytes[3] = 2;
		bytes[4] = 36 - 5;

		byte [] vendor = Utils.strToBytes(strVendor);
		byte [] product = Utils.strToBytes(strProduct);
		byte [] version = Utils.strToBytes(strVersion);

		Utils.arrayCopy(bytes, 8, vendor);
		Utils.arrayCopy(bytes, 16, product);
		Utils.arrayCopy(bytes, 32, version);
	}
}
