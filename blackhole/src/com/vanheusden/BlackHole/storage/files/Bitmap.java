/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage.files;

import com.vanheusden.BlackHole.*;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class Bitmap {
	static String version = "$Id: Bitmap.java 606 2013-07-06 22:07:22Z folkert $";
	String fileName;
	RandomAccessFile fileHandle;
	int pageSize = 131072;
	byte [] buffer = new byte[pageSize];
	long blockInBuffer = -1;
	long nextSearchStart0 = 0;
	long nextSearchStart1 = 0;
	long nBitsNeeded, size;

	public void delete() {
		new File(fileName).delete();
	}

	public void growSize(long newNBitsNeeded) throws IOException {
		assert newNBitsNeeded >= nBitsNeeded;
		size = newNBitsNeeded / 8;
		fileHandle.setLength(size);
		nBitsNeeded = newNBitsNeeded;
	}

	public Bitmap(String fileName, long nBitsNeeded) throws IOException {
		this.fileName = fileName;

		assert (nBitsNeeded & 7) == 0;
		size = nBitsNeeded / 8;

		fileHandle = new RandomAccessFile(fileName, "rw");
		fileHandle.setLength(size);

		this.nBitsNeeded = nBitsNeeded;
	}

	public void flush() throws IOException {
	}

	public void close() throws IOException {
		fileHandle.close();
	}

	private long getByteNr(long bitNr) {
		return bitNr / 8;
	}

	private int getBitNr(long bitNr) {
		return (int)(bitNr & 7);
	}

	private int getByte(long byteNr) throws IOException {
		int b = -1;

		assert byteNr < size;
		long pageNr = byteNr / pageSize;
		loadPage(pageNr);

		int dummy = (int)(byteNr & (pageSize - 1));
		b = buffer[dummy] & 0xff;

		return b;
	}

	private void loadPage(long pageNr) throws IOException {
		assert pageNr >= 0;
		try {
			if (pageNr != blockInBuffer)
			{
				fileHandle.seek(pageNr * pageSize);
				fileHandle.read(buffer);

				blockInBuffer = pageNr;
			}
		}
		catch(EOFException ee) { // we're reading where we haven't yet stored a bit pattern
			assert false;
		}
	}

	public long findBit0(long maxBitNr) throws IOException {
		return findBit0(false, maxBitNr);
	}

	public long findBit0(boolean startAtStart, long maxBitNr) throws IOException {
		long node = -1;

		long startAt = startAtStart ? 0 : (nextSearchStart0 & (~((pageSize * 8) - 1)));
		int pageStart = (int)(nextSearchStart0 & ((pageSize * 8) - 1)) / 8;
	finished2:
		for(long bitNr=startAt; bitNr<maxBitNr; bitNr += (pageSize * 8)) {
			loadPage(bitNr / (pageSize * 8));

			for(int index=pageStart; index<pageSize; index++) {
				if (buffer[index] != (byte)0xff) {
					long currentBitNr = bitNr + (index * 8);
					for(int bit=0; bit<8; bit++) {
						if ((buffer[index] & (1 << bit)) == 0) {
							node = currentBitNr + bit;
							nextSearchStart0 = node + 1;
							break finished2;
						}
					}
				}
			}
			pageStart=0;
		}

		if (node >= maxBitNr)
			return -1;

		return node;
	}

	public long findBit1(long maxBitNr) throws IOException {
		return findBit1(false, maxBitNr);
	}

	public long findBit1(boolean startAtStart, long maxBitNr) throws IOException {
		long node = -1;

		long startAt = startAtStart ? 0 : (nextSearchStart1 & (~((pageSize * 8) - 1)));
		int pageStart = (int)(nextSearchStart1 & ((pageSize * 8) - 1)) / 8;
	finished2:
		for(long bitNr=startAt; bitNr<maxBitNr; bitNr += (pageSize * 8)) {
			loadPage(bitNr / (pageSize * 8));

			for(int index=pageStart; index<pageSize; index++) {
				if (buffer[index] != 0x00) {
					long currentBitNr = bitNr + (index * 8);
					for(int bit=0; bit<8; bit++) {
						if ((buffer[index] & (1 << bit)) != 0) {
							node = currentBitNr + bit;
							nextSearchStart1 = node + 1;
							break finished2;
						}
					}
				}
			}
			pageStart=0;
		}

		if (node >= maxBitNr)
			return -1;

		return node;
	}

	public long getNBits1() throws IOException {
		int [] lookup = new int[256];
		for(int index=0; index<256; index++) {
			for(int cnt=0; cnt<8; cnt++) {
				if ((index & (1 << cnt)) != 0)
					lookup[index]++;
			}
		}

		long count = 0;
		for(long index=0; index<size; index++) {
			count += lookup[getByte(index) & 0xff];
		}

		return count;
	}

	private void putByte(long byteNr, int b) throws IOException {
		long pageNr = byteNr / pageSize;
		if (pageNr != blockInBuffer) {
			loadPage(pageNr);
		}

		int dummy = (int)(byteNr & (pageSize - 1));
		buffer[dummy] = (byte)b;

		fileHandle.seek(byteNr);
		fileHandle.write(b);
	}

	public boolean getBit(long bitNr) throws IOException {
		assert bitNr < nBitsNeeded;

		int currentBitNr = getBitNr(bitNr);

		long byteNr = getByteNr(bitNr);

		int currentByte = getByte(byteNr);

		if ((currentByte & (1 << currentBitNr)) != 0)
			return true;

		return false;
	}

	public void setBit(long bitNr, boolean value) throws IOException {
		assert bitNr < nBitsNeeded;

		int currentBitNr = getBitNr(bitNr);

		long byteNr = getByteNr(bitNr);

		int currentByte = getByte(byteNr);

		if (value) {
			currentByte |= 1 << currentBitNr;
			nextSearchStart1 = Math.min(nextSearchStart1, bitNr);
		}
		else {
			currentByte &= ~(1 << currentBitNr);
			nextSearchStart0 = Math.min(nextSearchStart0, bitNr);
		}

		putByte(byteNr, currentByte);
	}
}
