/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import java.io.InputStream;

public class PDU_0x3f_reject extends PDU {
	public PDU_0x3f_reject(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x3f_reject() {
		setBit(1, 7, true);
	}

	public int getStatSN() {
		return getInt(24);
	}

	public void setStatSN(int ess) {
		setInt(24, ess);
	}

	public int getExpCmdSN() {
		return getInt(28);
	}

	public void setExpCmdSN(int ess) {
		setInt(28, ess);
	}

	public int getDataSN() {
		return getInt(36);
	}

	public void setDataSN(int ess) {
		setInt(36, ess);
	}
}
