/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage.files;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.storage.*;

import javax.crypto.BadPaddingException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.MappedByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class TableOnDisk {
	static String version = "$Id: TableOnDisk.java 606 2013-07-06 22:07:22Z folkert $";
	String fileName;
	int elementSize;
	long nBlocks;
	RandomAccessFile fileHandle;
	FileChannel fileChannel;
	ByteBuffer readBuffer;
	long ss = 1L * 1024 * 1024 * 1024;
	int nMBB;
	MappedByteBuffer [] mbb;
	//
	Encryptor encryptorRead;
	Encryptor encryptorWrite;
	Random random = new Random();

	public void delete() {
		new File(fileName).delete();
		unmap();
	}

	public TableOnDisk(String fileName, int elementSize, long nBlocks, EncryptionParameters ep) throws IOException, VHException {
		this.fileName = fileName;
		this.elementSize = elementSize;
		this.nBlocks = nBlocks;
		long size = elementSize * nBlocks;

		if (ep != null) {
			try {
				encryptorRead = new Encryptor(ep);
				encryptorWrite = new Encryptor(ep);
			}
			catch(NoSuchAlgorithmException nsae) { throw new VHException("Crypto exception: " + nsae.toString()); }
			catch(NoSuchPaddingException nspe) { throw new VHException("Crypto exception: " + nspe.toString()); }
			catch(InvalidKeyException ike) { throw new VHException("Crypto exception: " + ike.toString()); }
			catch(InvalidAlgorithmParameterException iape) { throw new VHException("Crypto exception: " + iape.toString()); }
		}

		fileHandle = new RandomAccessFile(fileName, "rw");
		fileHandle.setLength(size);
		fileChannel = fileHandle.getChannel();
		readBuffer = ByteBuffer.allocateDirect(elementSize);

		map();
	}

	public void growSize(long newNBlocks) throws IOException {
		assert newNBlocks >= nBlocks;
		nBlocks = newNBlocks;
		unmap();
		map();
		fileHandle.setLength(newNBlocks * elementSize);
	}

	void unmap() {
		for(int index=0; index<nMBB; index++)
			mbb[index] = null;
		mbb = null;
		nMBB = 0;
	}

	void map() {
		long size = nBlocks * elementSize;
		nMBB = (int)((size  + ss - 1) / ss);
		try {
			mbb = new MappedByteBuffer[nMBB];
			for(int index=0; index<nMBB; index++) {
				long offset = index * ss;
				Log.log(LogLevel.LOG_DEBUG, "TOD: creating map " + index + " out of " + nMBB + " for " + fileName);
				mbb[index] = fileChannel.map(FileChannel.MapMode.READ_WRITE, offset, Math.min(size - offset, ss));
			}
// throw new IOException();
		}
		catch(IOException ie) {
			Log.log(LogLevel.LOG_WARN, "TOD: failed creating (m)map on " + fileName + ", falling back to regular i/o");

			unmap();
		}
	}

	public void flush() throws IOException {
		fileChannel.force(false);
		for(int index=0; index<nMBB; index++)
			mbb[index].force();
	}

	public void close() throws IOException {
		flush();
		fileHandle.close();
	}

	protected void putLow(long blockIndex, byte [] elementIn) throws IOException, VHException {
		byte [] element;

		if (encryptorWrite != null) {
			try {
				element = encryptorWrite.encrypt(elementIn);
			}
			catch(BadPaddingException pbe) { throw new VHException("Crypto exception: " + pbe.toString()); }
			catch(IllegalBlockSizeException ibse) { throw new VHException("Crypto exception: " + ibse.toString()); }
		}
		else
			element = elementIn;
		assert element.length == elementIn.length;

		long fileOffset = blockIndex * elementSize;
		if (nMBB > 0) {
			int mbbNr = (int)(fileOffset / ss);
			assert mbbNr < nMBB;
			int mbbOffset = (int)(fileOffset % ss);

			int curLen = (int)Math.min(ss - mbbOffset, element.length);
			mbb[mbbNr].position(mbbOffset);
			mbb[mbbNr].put(element, 0, curLen);
			if (curLen < elementSize) {
				System.out.println("" + (elementSize - curLen));
				mbbNr++;
				assert mbbNr < nMBB;
				mbb[mbbNr].position(0);
				mbb[mbbNr].put(element, curLen, elementSize - curLen);
			}
		}
		else {
			ByteBuffer b = ByteBuffer.wrap(element);
			fileChannel.write(b, fileOffset);
		}
	}

	public void storeElement(long elementNr, byte [] element, int nr) throws IOException, VHException {
		assert element.length == elementSize;

		putLow(elementNr, element);
	}

	public byte [] retrieveElement(long elementNr, boolean completeOnly, int nr) throws IOException, VHException {
		byte [] buffer = null;
		int rc = -1;
		long reshuffleNr = -1;

		byte [] bufferIn;

		if (nMBB > 0) {
			bufferIn = new byte[elementSize];
			long index = elementNr * elementSize;
			int mbbNr = (int)(index / ss);
			assert mbbNr < nMBB;
			int offset = (int)(index % ss);

			mbb[mbbNr].position(offset);
			int curLen = (int)Math.min(ss - offset, elementSize);
			mbb[mbbNr].get(bufferIn, 0, curLen);
			if (curLen < elementSize) {
				mbbNr++;
				assert mbbNr < nMBB;
				mbb[mbbNr].position(0);
				mbb[mbbNr].get(bufferIn, curLen, elementSize - curLen);
			}
		}
		else {
			rc = fileChannel.read(readBuffer, elementNr * elementSize);
			bufferIn = readBuffer.array();
		}
		if (encryptorRead != null) {
			try {
				buffer = encryptorRead.decrypt(bufferIn);
			}
			catch(BadPaddingException pbe) { throw new VHException("Crypto exception: " + pbe.toString()); }
			catch(IllegalBlockSizeException ibse) { throw new VHException("Crypto exception: " + ibse.toString()); }
		}
		else
			buffer = bufferIn;
		rc = elementSize;

		assert buffer.length == elementSize;

		if (rc == elementSize) {	// all fine
			return buffer;
		}

		// Log.log(LogLevel.LOG_ERR, "read failure: " + rc + " " + buffer.length + " " + buffer[0]);
		if (completeOnly)
			return null;
		if (rc == -1)		// reading beyond file-end, can happen
			return buffer;

		throw new VHException("TableOnDisk::retrieveElement: short-read while reading element. expecting " + elementSize + ", got " + rc);
	}

	public long getNElements() throws IOException {
		long fileSize = fileChannel.size();

		return fileSize / elementSize;
	}
}
