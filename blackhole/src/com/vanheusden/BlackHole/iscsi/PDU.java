/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.io.InputStream;
import java.io.IOException;

public class PDU {
	private byte [] bitMasks = { 1, 3, 7, 15, 31, 63, 127, (byte)255 };
	protected byte [] bytes = new byte[48];
	protected byte [] data = null;

	public byte getByte(int nr) {
		return bytes[nr];
	}

	public byte getDataByte(int nr) {
		return data[nr];
	}

	public byte [] getData() {
		return data;
	}

	public void setBit(int byteNr, int bit, boolean value) {
		int bitMask = 1 << bit;

		bytes[byteNr] &= (byte)(~bitMask);
		bytes[byteNr] |= (value ? bitMask : 0);
	}

	public boolean getBit(int byteNr, int bit) {
		int bitMask = 1 << bit;

		return ((bytes[byteNr] & bitMask) != 0) ? true : false;
	}

	public void setBits(int byteNr, int startBit, int value, int nBits) {
		int bitMask = bitMasks[nBits - 1] << startBit;

		bytes[byteNr] &= (byte)(~bitMask);
		bytes[byteNr] |= value << startBit;
	}

	public int getBits(int byteNr, int startBit, int nBits) {
		return ((bytes[byteNr] & 0xff) >> startBit) & bitMasks[nBits - 1];
	}

	public byte [] getAsByteArray() {
		if (data != null)
			return  addArrays(bytes, data);

		return bytes;
	}

	public CDB getCDB() {
		CDB out = new CDB();
		System.arraycopy(bytes, 32, out.bytes, 0, 16);
		out.data = data;
		return out;
	}

	public byte [] addArrays(byte [] in1, byte [] in2) {
		int len1 = in1.length;
		int len2 = in2.length;
		byte [] out = new byte[len1 + len2];
		Utils.arrayCopy(out, 0, in1);
		Utils.arrayCopy(out, len1, in2);
		return out;
	}

	public byte [] combineStrings(String [] strings) {
		int totalLen = 0;
		for(int index=0; index<strings.length; index++)
			totalLen += strings[index].length() + 1;

		byte [] result = new byte[totalLen];

		int putIndex = 0;
		for(int index=0; index<strings.length; index++) {
			Utils.arrayCopy(result, putIndex, Utils.strToBytes(strings[index]));
			putIndex += strings[index].length() + 1;
		}

		return result;
	}

	public static PDU recvPdu(InputStream is) throws IOException {
		PDU out = new PDU();
		int n = is.read(out.bytes);
		if (n == -1) // EOF
			return null;
		int dataLen = ((out.bytes[5] & 0xff) << 16) + ((out.bytes[6] & 0xff) << 8) + (out.bytes[7] & 0xff);
		System.out.printf("recv pdu opcode: %02x, %d, datalen: %d\n", (out.bytes[0] & 63), n, dataLen);
		if (dataLen > 0) {
			out.data = new byte[dataLen];
			is.read(out.data);
		}

                int remaining = dataLen & 3;
                if (remaining != 0) {
			int padding = 4 - remaining;
			byte [] buffer = new byte[padding];
			is.read(buffer);
		}

		return getResponsePDU(out);
	}

	public static PDU getResponsePDU(PDU in) {
		int opcode = in.getBits(0, 0, 6);
		if (opcode == 0x00)
			return new PDU_0x00__nop__request(in.bytes, in.data);

		if (opcode == 0x01)
			return new PDU_0x01_scsi_request(in.bytes, in.data);

		if (opcode == 0x02)
			return new PDU_0x02__taskman__request(in.bytes, in.data);

		if (opcode == 0x03)
			return new PDU_0x03__login__request(in.bytes, in.data);

		if (opcode == 0x04)
			return new PDU_0x04__text__request(in.bytes, in.data);

		if (opcode == 0x05)
			return new PDU_0x05__data_inout__request(in.bytes, in.data);

		if (opcode == 0x06)
			return new PDU_0x06__logoff__request(in.bytes, in.data);

		if (opcode == 0x20)
			return new PDU_0x20__nop__response(in.bytes, in.data);

		if (opcode == 0x21)
			return new PDU_0x21_scsi_response(in.bytes, in.data);

//		if (opcode == 0x22)
//			return new PDU_0x22__taskman__response(in.bytes, in.data);

		if (opcode == 0x23)
			return new PDU_0x23__login__response(in.bytes, in.data);

		if (opcode == 0x24)
			return new PDU_0x24__text__response(in.bytes, in.data);

		if (opcode == 0x26)
			return new PDU_0x26__logoff__response(in.bytes, in.data);

		if (opcode == 0x31)
			return new PDU_0x31__ready_to_transfer__request(in.bytes, in.data);

		if (opcode == 0x3f)
			return new PDU_0x3f_reject(in.bytes, in.data);

		return in;
	}

	public long getLong(int offset) {
		long out = 0;

		for(int index=0; index<8; index++) {
			out <<= 8;
			out += bytes[offset + index] & 0xff;
		}

		return out;
	}

	public void setLong(int offset, long value) {
		for(int index=7; index>=0; index--) {
			bytes[offset + index] = (byte)(value & 0xff);
			value >>= 8;
		}
	}

	public int getInt(int offset) {
		int out = 0;

		for(int index=0; index<4; index++) {
			out <<= 8;
			out += bytes[offset + index] & 0xff;
		}

		return out;
	}

	public void setInt(int offset, int value) {
		for(int index=3; index>=0; index--) {
			bytes[offset + index] = (byte)(value & 0xff);
			value >>= 8;
		}
	}

	public int getStatSN() {
		return getInt(24);
	}

	public byte [] getDataSegment() {
		return data;
	}

	public int getDataSegmentLength() {
		return ((bytes[5] & 0xff) << 16) + ((bytes[6] & 0xff) << 8) + (bytes[7] & 0xff);
	}

	public void setDataSegment(byte [] data) {
                int len = data.length;
		int remaining = len & 3;

		if (remaining != 0)
			this.data = addArrays(data, new byte[4 - remaining]);
		else
			this.data = data;

                bytes[5] = (byte)((len >> 16) & 255);
                bytes[6] = (byte)((len >> 8) & 255);
                bytes[7] = (byte)(len & 255);                   // DataSegmentLength: with the text parameters
	}

	public PDU(byte [] b, byte [] d) {
		bytes = b;
		data = d;
	}

	public PDU() {
	}

	public static void main(String [] args) {
		PDU p = new PDU();
		//
		p.setBit(0, 1, true);
		System.out.printf("%02x\n", p.bytes[0]);
		p.bytes[0] = (byte)255;
		p.setBit(0, 1, false);
		System.out.printf("%02x\n", p.bytes[0]);
		//
		p.bytes[0] = (byte)255;
		p.setBits(0, 1, 0, 3);
		System.out.printf("%02x\n", p.bytes[0]);
		p.bytes[0] = (byte)255;
		p.setBits(0, 1, 2, 3);
		System.out.printf("%02x\n", p.bytes[0]);
		//
		p.bytes[0] = 0;
		p.setBit(0, 7, true);
		System.out.printf("%02x\n", p.bytes[0]);
		p.bytes[0] = (byte)255;
		p.setBit(0, 7, false);
		System.out.printf("%02x\n", p.bytes[0]);
	}
}
