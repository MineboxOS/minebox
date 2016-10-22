/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import java.io.InputStream;
import java.io.IOException;

public class PDU_0x24__text__response extends PDU_0x04__text__request {
        public PDU_0x24__text__response() {
                bytes[0] = 0x24;
        }

        public PDU_0x24__text__response(byte [] b, byte [] d) {
                bytes = b;
                data = d;
        }

        public PDU_0x24__text__response(InputStream is) throws IOException {
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

        public PDU_0x24__text__response(int itt, int sn, String [] kvp) {
                bytes[0] = 0x24;
		setF(true);
		setC(false);
		setInitiatorTaskTag(itt);
		setTargetTransferTag(0xffffffff);
		setStatSN(sn);
		setKeyValuePairs(kvp);
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

	public int getStatSN() {
		return getInt(24);
	}

	public void setStatSN(int cs) {
		setInt(24, cs);
	}

	public int getExpCmdSn() {
		return getInt(28);
	}

	public void setExpCmdSn(int ess) {
		setInt(28, ess);
	}

	public int getMaxCmdSN() {
		return getInt(32);
	}

	public void setMaxCmdSN(int ess) {
		setInt(32, ess);
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
