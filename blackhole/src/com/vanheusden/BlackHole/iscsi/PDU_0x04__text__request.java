/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import java.io.InputStream;

public class PDU_0x04__text__request extends PDU {
	public PDU_0x04__text__request(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x04__text__request() {
	}

	public PDU_0x04__text__request(int sn, int esn, String [] keyValuePairs) {
		setBit(0, 6, false);		// I=0
		setBits(0, 0, 0x04, 6);		// byte 0, bit 0-5, opcode 0x04, 6 wide
		setBit(1, 7, true);		// F=1 final pdu in sequence
		setBit(1, 6, false);		// C=0 text is complete
		bytes[4] = 0;			// no AHS
		bytes[16] = bytes[17] =
		bytes[18] = 0;
		bytes[19] = 1;			// initiator task tag
		bytes[20] = (byte)0xff;
		bytes[21] = (byte)0xff;
		bytes[22] = (byte)0xff;
		bytes[23] = (byte)0xff;		// target transfer tag
                bytes[24] = (byte)((sn >> 24) & 255);
                bytes[25] = (byte)((sn >> 16) & 255);
                bytes[26] = (byte)((sn >>  8) & 255);
                bytes[27] = (byte)(sn & 255);// CmdSN
                bytes[28] = (byte)((esn >> 24) & 255);
                bytes[29] = (byte)((esn >> 16) & 255);
                bytes[30] = (byte)((esn >>  8) & 255);
                bytes[31] = (byte)(esn & 255);// expatsn

		byte [] dataSegment = combineStrings(keyValuePairs);
		setDataSegment(dataSegment);
	}

	public boolean getI() {
		return getBit(0, 6);
	}

	public void setI(boolean to) {
		setBit(0, 6, to);
	}

	public boolean getF() {
		return getBit(1, 7);
	}

	public void setF(boolean to) {
		setBit(1, 7, to);
	}

	public boolean getC() {
		return getBit(1, 6);
	}

	public void setC(boolean to) {
		setBit(1, 6, to);
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

	public void setTargetTransferTag(int tag) {
		setInt(20, tag);
	}

	public int getCmdSN() {
		return getInt(24);
	}

	public void setCmdSN(int cs) {
		setInt(24, cs);
	}

	public int getExpStatSN() {
		return getInt(28);
	}

	public void setExpStatSN(int ess) {
		setInt(28, ess);
	}

	public String [] getKeyValuePairs() {
		for(int index=0; index<data.length; index++) {
			if (data[index] == 0x00)
				data[index] = '\n';
		}

		String str = new String(data);

		return str.split("\n");
	}

	public void setKeyValuePairs(String [] keyValuePairs) {
		byte [] dataSegment = combineStrings(keyValuePairs);
		setDataSegment(dataSegment);
	}
}
