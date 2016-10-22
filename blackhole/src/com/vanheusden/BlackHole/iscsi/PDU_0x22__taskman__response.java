/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.io.InputStream;
import java.io.IOException;

public class PDU_0x22__taskman__response extends PDU {
	public PDU_0x22__taskman__response() {
		bytes[0] = 0x22;
	}

	public PDU_0x22__taskman__response(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x22__taskman__response(InputStream is) throws IOException {
		is.read(bytes);

		int nData = getDataSegmentLength();
		if (nData > 0) {
			data = new byte[nData];
			is.read(data);

			int remaining = nData & 3;
			if (remaining != 0) {
				byte [] rem = new byte[4 - remaining];
				is.read(rem);
			}
		}
	}

	public PDU_0x22__taskman__response(int response) {
		bytes[0] = 0x22;
		bytes[1] = (byte)0x80;
		setResponse(response);
	}

	public int getResponse() {
		return bytes[2];
	}

	public void setResponse(int status) {
		bytes[2] = (byte)status;
	}

	public int getInitiatorTaskTag() {
		return getInt(16);
	}

	public void setInitiatorTaskTag(int tag) {
		setInt(16, tag);
	}

	public int getStatSN() {
		return getInt(24);
	}

	public void setStatSN(int what) {
		setInt(24, what);
	}

	public int getExpCmdSN() {
		return getInt(28);
	}

	public void setExpCmdSN(int what) {
		setInt(28, what);
	}

	public int getMaxCmdSN() {
		return getInt(32);
	}

	public void getMaxCmdSN(int what) {
		setInt(32, what);
	}
}
