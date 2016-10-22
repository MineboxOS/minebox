/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.io.InputStream;
import java.io.IOException;

public class PDU_0x31__ready_to_transfer__request extends PDU_0x00__nop__request {
	public PDU_0x31__ready_to_transfer__request(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x31__ready_to_transfer__request(InputStream is) throws IOException {
		is.read(bytes);
	}

	public PDU_0x31__ready_to_transfer__request(int offset, int nBytes) {
		setBit(1, 7, true);
		setInt(40, offset);
		setInt(44, nBytes);
	}

	public long getLUN() {
		return getLong(8);
	}

	public void setLUN(long lun) {
		setLong(8, lun);
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

	public void setTargetTransferTag(int value) {
		setInt(20, value);
	}

	public int getStatSN() {
		return getInt(24);
	}

	public void setStatSN(int cs) {
		setInt(24, cs);
	}

	public int getExpStatSN() {
		return getInt(28);
	}

	public void setExpStatSN(int ess) {
		setInt(28, ess);
	}

	public int getMaxCmdSN() {
		return getInt(32);
	}

	public void setMaxCmdSN(int mcs) {
		setInt(32, mcs);
	}

	public int getR2TSN() {
		return getInt(36);
	}

	public void setR2TSN(int mcs) {
		setInt(36, mcs);
	}

	public int getBufferOffset() {
		return getInt(40);
	}

	public void setBufferOffset(int mcs) {
		setInt(40, mcs);
	}

	public int getDesiredDataTransferLength() {
		return getInt(44);
	}

	public void setDesiredDataTransferLength(int mcs) {
		setInt(44, mcs);
	}
}
