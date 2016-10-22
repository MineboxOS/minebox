/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.io.InputStream;
import java.io.IOException;

public class PDU_0x25_scsi_response_data extends PDU {
	public PDU_0x25_scsi_response_data() {
		bytes[0] = 0x25;
	}

	public PDU_0x25_scsi_response_data(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x25_scsi_response_data(InputStream is) throws IOException {
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

	public boolean getF() {
		return getBit(1, 7);
	}

	public void setF(boolean what) {
		setBit(1, 7, what);
	}

	public boolean getA() {
		return getBit(1, 6);
	}

	public void setA(boolean what) {
		setBit(1, 6, what);
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

	public boolean getS() {
		return getBit(1, 0);
	}

	public void setS(boolean what) {
		setBit(1, 0, what);
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

	public int getTargetTransferTag() {
		return getInt(20);
	}

	public void setTargetTransferTag(int what) {
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

	public int getDataSN() {
		return getInt(36);
	}

	public void setDataSN(int what) {
		setInt(36, what);
	}

	public int getBufferOffset() {
		return getInt(40);
	}

	public void setBufferOffset(int what) {
		setInt(40, what);
	}

	public int getResidualCount() {
		return getInt(44);
	}

	public void setResidualCount(int what) {
		setInt(44, what);
	}
}
