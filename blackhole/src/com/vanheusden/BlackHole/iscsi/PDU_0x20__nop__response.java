/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.io.InputStream;
import java.io.IOException;

public class PDU_0x20__nop__response extends PDU_0x00__nop__request {
	public PDU_0x20__nop__response(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x20__nop__response(int itt, int ss) {
		setBits(0, 0, 0x20, 6);		// byte 0, bit 0-5, opcode 0x20, 6 wide
		setBit(1, 7, true);
		setInitiatorTaskTag(itt);
		setTargetTransferTag(0xffffffff);
		setStatSN(ss);
	}

	public PDU_0x20__nop__response(InputStream is) throws IOException {
		is.read(bytes);
	}

	public PDU_0x20__nop__response() {
		setBits(0, 0, 0x20, 6);		// byte 0, bit 0-5, opcode 0x20, 6 wide
		setBit(1, 7, true);
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
}
