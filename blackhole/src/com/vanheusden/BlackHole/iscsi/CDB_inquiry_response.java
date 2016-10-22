/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.util.Arrays;

public class CDB_inquiry_response extends CDB{
	byte [] bytes;
	byte [] data;

	public CDB_inquiry_response(CDB in) {
		bytes = in.bytes;
		data = in.data;
	}

	public String getVendorID() {
		if (data.length >= 16) {
			byte [] vi = new byte[8];
			System.arraycopy(data, 8, vi, 0, 8);
			return new String(vi);
		}

		return "vendor: ???";
	}

	public String getProductID() {
		if (data.length >= 32) {
			byte [] pi = new byte[16];
			System.arraycopy(data, 16, pi, 0, 16);
			return new String(pi);
		}

		return "product: ???";
	}
}
