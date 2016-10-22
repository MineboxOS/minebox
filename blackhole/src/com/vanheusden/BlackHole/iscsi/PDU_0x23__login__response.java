/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import java.io.InputStream;
import java.io.IOException;

public class PDU_0x23__login__response extends PDU_0x03__login__request {
	public PDU_0x23__login__response() {
		bytes[0] = 0x23;
	}

	public PDU_0x23__login__response(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x23__login__response(InputStream is) throws IOException {
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

	public PDU_0x23__login__response(long isid, int tsih, int itt, int cid, int expstatsn) {
		setBits(0, 0, 0x23, 6);		// opcode
		setBit(1, 7, true);		// T=1
		setBit(1, 6, false);		// C=0
		bytes[2] = 0;			// version max
		bytes[3] = 0;			// version min
		bytes[4] = 0;			// no AHS
		setCSG(1);
		setNSG(3);
		setISID(isid);
		setTSIH(tsih);
		setInitiatorTaskTag(itt);
		setCID(cid);
		setStatSN(expstatsn);
		setExpCmdSN(0);
		setMaxCmdSN(expstatsn + 1);
		setStatusClass(0);
		setStatusDetail(0);

		String [] keyValuePairs = new String[] {
				"TargetPortalGroupTag=1",
				"MaxConnections=1",		// FvH
				"InitialR2T=Yes",		// FvH
				"ImmediateData=Yes",		// FvH
//				"MaxRecvDataSegmentLength=262144", // FvH
				"MaxOutstandingR2T=1",		// FvH
				"HeaderDigest=None",
				"DataDigest=None",
				"DefaultTime2Wait=1",
				"DefaultTime2Retain=0",
				"IFMarker=No",
				"OFMarker=No",
				"ErrorRecoveryLevel=0"
		};
		byte [] dataSegment = combineStrings(keyValuePairs);
		setDataSegment(dataSegment);
	}

	public int getCSG() {
		return getBits(1, 2, 2);
	}

	public void setCSG(int value) {
		assert value >= 0 && value <= 3;
		setBits(1, 2, value, 2);
	}

	public int getNSG() {
		return getBits(1, 0, 2);
	}

	public void setNSG(int value) {
		assert value >= 0 && value <= 3;
		setBits(1, 0, value, 2);
	}

	public long getISID() {
		return (((long)getInt(8)) << 16) + ((long)(bytes[12] & 0xff) << 8) + (long)(bytes[13] & 0xff);
	}

	public void setISID(long value) {
		for(int index=5; index>=0; index--) {
			bytes[8 + index] = (byte)(value & 0xff);
			value >>= 8;
		}
	}

	public int getTSIH() {
		return ((bytes[14] & 0xff) << 8) + (bytes[15] & 0xff);
	}

	public void setTSIH(int value) {
		bytes[14] = (byte)((value >> 8) & 0xff);
		bytes[15] = (byte)(value & 0xff);
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

	public void setMaxCmdSN(int what) {
		setInt(32, what);
	}

	public int getStatusClass() {
		return bytes[36] & 0xff;
	}

	public void setStatusClass(int c) {
		bytes[36] = (byte)c;
	}

	public int getStatusDetail() {
		return bytes[37] & 0xff;
	}

	public void setStatusDetail(int detail) {
		bytes[37] = (byte)detail;
	}
}
