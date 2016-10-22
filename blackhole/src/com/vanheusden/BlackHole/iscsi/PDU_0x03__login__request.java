/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import java.io.InputStream;

public class PDU_0x03__login__request extends PDU {
	public PDU_0x03__login__request(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x03__login__request() {
	}

	// mode = 1: discover
	// mode = 2: login to lun
	// mode = 3: no data
	public PDU_0x03__login__request(int esn, String targetLun, int mode, String myIQN) {
		setBit(0, 6, true);		// 1
		setBits(0, 0, 0x03, 6);		// byte 0, bit 0-5, opcode 0x03, 6 wide
		setBit(1, 7, true);		// T=1
		setBit(1, 6, false);		// C=0
		setBits(1, 2, 1, 2);		// byte 1, bit 2-3, value 1, 2 wide: LoginOperationalNegotiation (CSG: 01)
		setBits(1, 0, 3, 2);		// next FullFeaturePhase (NSG: 11)
		bytes[2] = 0;			// version max
		bytes[3] = 0;			// version min
		bytes[4] = 0;			// no AHS
		bytes[8] = 0;			// A  + ISID
		bytes[9] = 0x02;		// B  |
		bytes[10] = 0x3d;		// B  |
		bytes[11] = 0;			// C  |
		bytes[12] = bytes[13] = 0;	// D  +
		bytes[14] = bytes[15] = 0;	// TSIH
		bytes[16] = bytes[17] =
		bytes[18] = bytes[19] = 0;	// initiator task tag
		bytes[20] = 0;
		bytes[21] = 0;			// CID
		bytes[24] = bytes[25] =
		bytes[26] = 0;
		bytes[27] = 1;			// CmdSN
                bytes[28] = (byte)((esn >> 24) & 255);
                bytes[29] = (byte)((esn >> 16) & 255);
                bytes[30] = (byte)((esn >>  8) & 255);
                bytes[31] = (byte)(esn & 255);// expatsn

		String [] keyValuePairs = null;
		if (mode == 1) {
			keyValuePairs =  new String[] {
				"InitiatorName=" + myIQN,
				"SessionType=Discovery",
				"HeaderDigest=None",
				"DataDigest=None",
				"DefaultTime2Wait=0",
				"DefaultTime2Retain=0",
				"IFMarker=No",
				"OFMarker=No",
				"ErrorRecoveryLevel=0",
				"MaxRecvDataSegmentLength=32768"
			};
		}
		else if (mode == 2){
			String kvTargetLun = "TargetName=" + targetLun;
			keyValuePairs =  new String[] {
				"InitiatorName=" + myIQN,
//				"InitiatorAlias=belle",
				kvTargetLun,
				"SessionType=Normal",
				"HeaderDigest=None",
				"DataDigest=None",
				"DefaultTime2Wait=2",
				"DefaultTime2Retain=0",
				"IFMarker=No",
				"OFMarker=No",
				"ErrorRecoveryLevel=0",
				"InitialR2T=Yes",
				"ImmediateData=Yes",
				"MaxBurstLength=4096",
				"FirstBurstLength=4096",
				"MaxOutstandingR2T=1",
				"MaxConnections=1",
				"DataPDUInOrder=Yes",
				"DataSequenceInOrder=Yes",
				"MaxRecvDataSegmentLength=4096"
			};
		}
		if (mode == 1 || mode == 2) {
			byte [] dataSegment = combineStrings(keyValuePairs);
			setDataSegment(dataSegment);
		}
	}

	public boolean getT() {
		return getBit(1, 7);
	}

	public void setT(boolean what) {
		setBit(1, 7, what);
	}

	public boolean getC() {
		return getBit(1, 6);
	}

	public void setC(boolean what) {
		setBit(1, 6, what);
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

	public int getVersionMax() {
		return bytes[2];
	}

	public void setVersionMax(int version) {
		bytes[2] = (byte)version;
	}

	public int getVersionMin() {
		return bytes[3];
	}

	public void setVersionMin(int version) {
		bytes[3] = (byte)version;
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

	public int getCID() {
		return (bytes[20] & 0xff) << 8 + (bytes[21] & 0xff);
	}

	public void setCID(int value) {
		bytes[20] = (byte)(value >> 8);
		bytes[21] = (byte)(value & 0xff);
	}

	public int getCmdSN() {
		return getInt(24);
	}

	public void setCmdSN(int what) {
		setInt(24, what);
	}

	public int getExpStatSN() {
		return getInt(28);
	}

	public void setExpStatSN(int what) {
		setInt(28, what);
	}
}
