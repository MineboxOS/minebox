/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.io.InputStream;
import java.io.IOException;

public class PDU_0x21_scsi_response extends PDU {
	public PDU_0x21_scsi_response() {
		bytes[0] = 0x21;
		setBit(1, 7, true);
	}

	public PDU_0x21_scsi_response(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x21_scsi_response(InputStream is) throws IOException {
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

	public boolean geto() {
		return getBit(1, 4);
	}

	public void seto(boolean what) {
		setBit(1, 4, what);
	}

	public boolean getu() {
		return getBit(1, 3);
	}

	public void setu(boolean what) {
		setBit(1, 3, what);
	}

	public boolean getO() {
		return getBit(1, 2);
	}

	public void setO(boolean what) {
		setBit(1, 2, what);
	}

	public boolean getU() {
		return getBit(1, 1);
	}

	public void setU(boolean what) {
		setBit(1, 1, what);
	}

	public int getResponse() {
		return bytes[2] & 0xff;
	}

	public void setResponse(int response) {
		bytes[2] = (byte)response;
	}

	public int getStatus() {
		return bytes[3];
	}

	public void setStatus(int status) {
		bytes[3] = (byte)status;
	}

	public int getInitiatorTaskTag() {
		return getInt(16);
	}

	public void setInitiatorTaskTag(int tag) {
		setInt(16, tag);
	}

	public int getSNACKTag() {
		return getInt(20);
	}

	public void setSNACKTag(int what) {
		setInt(20, what);
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

	public void setMaxCmdSN(int what) {
		setInt(32, what);
	}

	public int getExpDataSN() {
		return getInt(36);
	}

	public void setExpDataSN(int what) {
		setInt(36, what);
	}

	public int getBidirectionalReadResidualCount() {
		return getInt(40);
	}

	public void setBidirectionalReadResidualCount(int what) {
		setInt(40, what);
	}

	public int getResidualCount() {
		return getInt(44);
	}

	public void setResidualCount(int what) {
		setInt(44, what);
	}
}
