/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

import com.vanheusden.BlackHole.Utils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;

public class SCSI_write_10 extends SCSI_response {
	long offset = -1;
	int nBlocks = -1;
	int bufferOffset = -1;
	public SCSI_write_10(FileChannel fc, CDB cdb, int blockSize, byte [] data) throws IOException {
		byte [] bIn = cdb.bytes;
		long lba = Utils.byteArrayToInt(bIn, 2) & 0xffffffff;
		int nBlocks = ((int)(bIn[7] & 0xff) << 8) + (bIn[8] & 0xff);

		System.out.println("   cdb # blocks: " + nBlocks + ", data # blocks: " + (data.length / blockSize));
		if (nBlocks != data.length / blockSize)
			System.err.println("   cdb # blocks: " + nBlocks + ", data # blocks: " + (data.length / blockSize));
//		assert nBlocks == data.length / blockSize;

		ByteBuffer bb = ByteBuffer.wrap(data);
		offset = lba * blockSize;
		fc.write(bb, offset);

		this.nBlocks = nBlocks - (data.length / blockSize);
		this.bufferOffset = data.length;
	}

	public long getOffset() {
		return offset;
	}

	public int getNBlocks() {
		return nBlocks;
	}

	public int getBufferOffset() {
		return bufferOffset;
	}
}
