/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.io.InputStream;

public class PDU_0x01_scsi__write__request extends PDU_0x01_scsi_request {
	public PDU_0x01_scsi__write__request(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU_0x01_scsi__write__request(int sn, int esn, long blockNr, int targetBlockSize, byte [] data) {
                setBit(0, 6, true);             // 1
                setBits(0, 0, 0x01, 6);         // byte 0, bit 0-5, opcode 0x01, 6 wide
                setBit(1, 7, true);             // F=1
                setBit(1, 6, false);            // R=0
                setBit(1, 5, true);             // W=1
                setBits(1, 0, 1, 3);            // ATTR=1
                bytes[16] = 0x0a;               // initiator task tag 0x0a
		bytes[20] = (byte)((data.length >> 24) & 255);
		bytes[21] = (byte)((data.length >> 16) & 255);
		bytes[22] = (byte)((data.length >>  8) & 255);
		bytes[23] = (byte)(data.length & 255);// expected data transfer length
                bytes[24] = (byte)((sn >> 24) & 255);
                bytes[25] = (byte)((sn >> 16) & 255);
                bytes[26] = (byte)((sn >>  8) & 255);
                bytes[27] = (byte)(sn & 255);// CmdSN
                bytes[28] = (byte)((esn >> 24) & 255);
                bytes[29] = (byte)((esn >> 16) & 255);
                bytes[30] = (byte)((esn >>  8) & 255);
                bytes[31] = (byte)(esn & 255);// expatsn
		CDB cdb = new CDB_write10(blockNr, targetBlockSize, data.length);
		Utils.arrayCopy(bytes, 32, cdb.getAsByteArray());
		setDataSegment(data);
	}
}
