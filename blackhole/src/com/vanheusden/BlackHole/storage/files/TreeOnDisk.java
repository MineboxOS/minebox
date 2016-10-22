/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage.files;

import com.vanheusden.BlackHole.*;

import javax.crypto.BadPaddingException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class TreeOnDisk {
	static String version = "$Id: TreeOnDisk.java 606 2013-07-06 22:07:22Z folkert $";
	final static long maxReshuffleSleep = 30000, minReshuffleSleep = 1000;
	final double log2 = Math.log(2.0);
	//
	AtomicBoolean exit = new AtomicBoolean();
	TableOnDisk tod;
	Bitmap b;
	long maxNBlocks, nBlocksIn;
	long topNode = -1;
	RandomAccessFile tnHandle = null;
	int hashSize;
	String topNodeFilename;

	public void delete() {
		tod.delete();
		b.delete();
		new File(topNodeFilename).delete();
	}

	public void growSize(long newNBlocks) throws IOException {
		tod.growSize(newNBlocks);
		b.growSize(newNBlocks);
	}

	public boolean hasElement(long element) throws IOException {
		return b.getBit(element);
	}

	public TreeOnDisk(String tableFilename, String bitmapFilename, String topNodeFilename, long maxNBlocks, int hashSize, boolean isFsck) throws IOException, VHException {
		tod = new TableOnDisk(tableFilename, hashSize /* hash */ + 8 /* left */ + 8 /* right */, maxNBlocks, null);
		b = new Bitmap(bitmapFilename, maxNBlocks);
		this.maxNBlocks = maxNBlocks;
		this.hashSize = hashSize;

		this.topNodeFilename = topNodeFilename;
		tnHandle = new RandomAccessFile(topNodeFilename, "rw");
		byte [] buffer = new byte[8];
		if (tnHandle.read(buffer) != 8)
			topNode = -1;
		else {
			topNode = Utils.byteArrayToLong(buffer);
			Log.log(LogLevel.LOG_DEBUG, "TreeOnDisk: top node: " + topNode);
		}
	}

	public int findDepth(long start, boolean left) throws IOException, VHException {
		long current = start;
		int length = 0;

		long stopSearch = Math.max(16, maxNBlocks);
		while(current != -1) {
			byte [] nodeBytes = tod.retrieveElement(current, true, 10);
			if (nodeBytes == null)
				break;
			TreeOnDiskNode todnIn = new TreeOnDiskNode(nodeBytes, hashSize);

			if (++length > stopSearch) {
				Log.log(LogLevel.LOG_DEBUG, "node: " + current);
				Log.log(LogLevel.LOG_DEBUG, "left: " + todnIn.getLeft());
				Log.log(LogLevel.LOG_DEBUG, "right: " + todnIn.getRight());
				TreeSet<Long> check = new TreeSet<Long>();
				checkTreeForError(topNode, check);
				throw new VHException("search took too long");
			}

			if (left)
				current = todnIn.getLeft();
			else
				current = todnIn.getRight();
		}

		return length;
	}

	public void storeTopNode() throws IOException {
		tnHandle.seek(0);
		byte [] buffer = Utils.longToByteArray(topNode);
		tnHandle.write(buffer);
		Log.log(LogLevel.LOG_DEBUG, "TreeOnDisk: use node " + topNode + " as top node");
	}

	public void flush() throws IOException {
		tod.flush();
		b.flush();
	}

	public void close() throws IOException {
		Log.log(LogLevel.LOG_INFO, "TreeOnDisk close");
		exit.set(true);
		Log.log(LogLevel.LOG_INFO, "TreeOnDisk close hash table");
		tod.close();
		Log.log(LogLevel.LOG_INFO, "TreeOnDisk close usage bitmap");
		b.close();
		Log.log(LogLevel.LOG_INFO, "TreeOnDisk close topnode");
		tnHandle.close();
		Log.log(LogLevel.LOG_INFO, "TreeOnDisk close finished");
	}

	private long findEmptyNode() throws IOException {
		long node = b.findBit0(maxNBlocks);
		assert node != -1;
		assert b.getBit(node) == false;
		return node;
	}

	public void dump(String fileName) throws IOException, VHException {
		BufferedWriter out = new BufferedWriter(new FileWriter(fileName, true));
		dumpLine(out, "digraph {");
		dumpNode(out, 0);
		dumpLine(out, "}");
		out.close();
	}

	public void dumpLine(BufferedWriter out, String line) throws IOException {
		out.write(line, 0, line.length());
		out.newLine();
	}

	public String dumpValue(byte [] value) {
		String out = "";

		for(int index=0; index<value.length; index++) {
			out += String.format("%02x", value[index]);
		}

		return out;
	}

	public void dumpNode(BufferedWriter out, long p) throws IOException, VHException {
		if (p == -1)
			return;

		TreeOnDiskNode todnIn = new TreeOnDiskNode(tod.retrieveElement(p, false, 8), hashSize);
		long left = todnIn.getLeft();
		long right = todnIn.getRight();

		dumpLine(out, "\t" + p + " [label=\"" + todnIn.getValue()[0] + "\"]");

		if (left != -1) {
			dumpLine(out, "\t" + p + " -> " + left);
			dumpNode(out, left);
		}

		if (right != -1) {
			dumpLine(out, "\t" + p + " -> " + right);
			dumpNode(out, right);
		}
	}

	public void dumpNode(long p) throws IOException, VHException {
		TreeOnDiskNode todnIn = new TreeOnDiskNode(tod.retrieveElement(p, false, 8), hashSize);
		byte [] value = todnIn.getValue();
		long left = todnIn.getLeft();
		long right = todnIn.getRight();

		System.out.println("value: " + Arrays.toString(value) + ", left: " + left + ", right: " + right);
	}

	public long storeNode(long parent, byte [] hash) throws IOException, VHException {
		return storeNode(parent, hash, -1);
	}

	public long storeNode(long parent, byte [] hash, long locationInTod) throws IOException, VHException {
		// find free location in table
		long newIndex = locationInTod;
		if (newIndex == -1)
			newIndex = findEmptyNode();
		assert newIndex != -1;

		TreeOnDiskNode todnIn = new TreeOnDiskNode(tod.retrieveElement(parent, true, 9), hashSize);

		if (Utils.compareValues(hash, todnIn.getValue()) < 0) {
			assert todnIn.getLeft() == -1;

			// let parent node point to the new object
			todnIn.setLeft(newIndex);
			tod.storeElement(parent, todnIn.getAsByteArray(), 7);
		}
		else {
			assert todnIn.getRight() == -1;

			// let parent node point to the new object
			todnIn.setRight(newIndex);
			tod.storeElement(parent, todnIn.getAsByteArray(), 9);
		}

		// create new object
		TreeOnDiskNode todnOut = new TreeOnDiskNode(hash, -1, -1, hashSize);
		// and store it in the free location
		tod.storeElement(newIndex, todnOut.getAsByteArray(), 10);
		b.setBit(newIndex, true);

		return newIndex;
	}

	public long storeNode(byte [] hash) throws IOException, VHException {
		return storeNode(hash, -1);
	}

	public long storeNode(byte [] hash, long locationInTod) throws IOException, VHException {
		nBlocksIn++;

		assert hash.length == hashSize;
		long current = topNode;

		if (topNode == -1) {
			if (locationInTod != -1)
				topNode = locationInTod;
			else
				topNode = findEmptyNode();

			// create new object
			TreeOnDiskNode todnOut = new TreeOnDiskNode(hash, -1, -1, hashSize);
			// and store it in the free location
			tod.storeElement(topNode, todnOut.getAsByteArray(), 6);
			b.setBit(topNode, true);

			storeTopNode();

			return topNode;
		}

		while(current != -1) {
			TreeOnDiskNode todnIn = new TreeOnDiskNode(tod.retrieveElement(current, true, 9), hashSize);
			// System.out.println("" + current + ": " + todnIn.toString());

			if (Utils.compareValues(hash, todnIn.getValue()) < 0) {
				if (todnIn.getLeft() == -1) {
					// find free location in table
					long newIndex = locationInTod;
					if (newIndex == -1)
						newIndex = findEmptyNode();
					assert newIndex != -1;
					// let current node point to the new object
					todnIn.setLeft(newIndex);
					tod.storeElement(current, todnIn.getAsByteArray(), 7);
					// create new object
					TreeOnDiskNode todnOut = new TreeOnDiskNode(hash, -1, -1, hashSize);
					// and store it in the free location
					tod.storeElement(newIndex, todnOut.getAsByteArray(), 8);
					b.setBit(newIndex, true);
					return newIndex;
				}
				else {
					current = todnIn.getLeft();
				}
			}
			else {
				if (todnIn.getRight() == -1) {
					// find free location in table
					long newIndex = locationInTod;
					if (newIndex == -1)
						newIndex = findEmptyNode();
					assert newIndex != -1;
					// let current node point to the new object
					todnIn.setRight(newIndex);
					tod.storeElement(current, todnIn.getAsByteArray(), 9);
					// create new object
					TreeOnDiskNode todnOut = new TreeOnDiskNode(hash, -1, -1, hashSize);
					// and store it in the free location
					tod.storeElement(newIndex, todnOut.getAsByteArray(), 10);
					b.setBit(newIndex, true);
					return newIndex;
				}
				else {
					current = todnIn.getRight();
				}
			}
		}

		assert false;
		return -1;
	}

	public Long [] findNode(byte [] hash) throws IOException, VHException {
		assert hash.length == hashSize;
		long current = topNode;
		long found = -1, parent = -1;
		int length = 0;

		while(current != -1) {
			byte [] nodeBytes = tod.retrieveElement(current, true, 10);
			if (nodeBytes == null)
				break;
			TreeOnDiskNode todnIn = new TreeOnDiskNode(nodeBytes, hashSize);

			if (++length > maxNBlocks) {
				Log.log(LogLevel.LOG_DEBUG, "node: " + current);
				Log.log(LogLevel.LOG_DEBUG, "left: " + todnIn.getLeft());
				Log.log(LogLevel.LOG_DEBUG, "right: " + todnIn.getRight());
				TreeSet<Long> check = new TreeSet<Long>();
				checkTreeForError(topNode, check);
				throw new VHException("search took too long");
			}

			int diff = Utils.compareValues(hash, todnIn.getValue());
			if (diff == 0) {
				found = current;
				break;
			}
			parent = current;
			if (diff < 0)
				current = todnIn.getLeft();
			else
				current = todnIn.getRight();
		}

		return new Long [] { Long.valueOf(found), Long.valueOf(parent) };
	}

	public byte [] retrieveElement(long nr) throws IOException, VHException {
		TreeOnDiskNode todn = new TreeOnDiskNode(tod.retrieveElement(nr, true, 11), hashSize);
		return todn.getValue();
	}

	public boolean checkTreeForError(long node, TreeSet<Long> check) throws IOException, VHException {
		boolean error = false;

		if (topNode == -1) { // EMPTY datastore
			System.out.println("empty datastore(1) ");
			return false; // FIXME; check that the topnodefile also does not exist
		}

		if (node == -1)
			node = topNode;

		if (node == topNode && tod.retrieveElement(topNode, true, 17) == null) {
			System.out.println("empty datastore(2) ");
			return false;
		}

		if (b.getBit(node) == false) {
			System.out.println("node not in allocation bitmap");
			return false;
		}

		TreeOnDiskNode todn = new TreeOnDiskNode(tod.retrieveElement(node, true, 18), hashSize);
		TreeOnDiskNode tl = null, tr = null;
		if (todn.getLeft() != -1)
		{
			tl = new TreeOnDiskNode(tod.retrieveElement(todn.getLeft(), true, 19), hashSize);
			if (check.contains(todn.getLeft()) != false) {
				Log.log(LogLevel.LOG_EMERG, "left circular");
				error = true;
			}
		}
		if (todn.getRight() != -1) {
			tr = new TreeOnDiskNode(tod.retrieveElement(todn.getRight(), true, 20), hashSize);
			if (check.contains(todn.getRight()) != false) {
				Log.log(LogLevel.LOG_EMERG, "right circular");
				error = true;
			}
		}

		if (tl != null && Utils.compareValues(tl.getValue(), todn.getValue()) > 0) {
			Log.log(LogLevel.LOG_EMERG, "left value bigger");
			error = true;
		}
		if (tr != null && Utils.compareValues(tr.getValue(), todn.getValue()) < 0) {
			Log.log(LogLevel.LOG_EMERG, "right value smaller");
			error = true;
		}

		if (todn.getLeft() == node) {
			Log.log(LogLevel.LOG_EMERG, "left circular");
			error = true;
		}
		if (todn.getRight() == node) {
			Log.log(LogLevel.LOG_EMERG, "right circular");
			error = true;
		}

		check.add(node);

		if (todn.getLeft() != -1)
			error |= checkTreeForError(todn.getLeft(), check);
		if (todn.getRight() != -1)
			error |= checkTreeForError(todn.getRight(), check);

		return error;
	}

	public boolean checkTreeForError2(TreeSet<Long> check) throws IOException {
		for(long index=0; index<maxNBlocks; index++) {
			if (b.getBit(index) && check.contains(index) == false) {
				System.out.println("orphan node");
				return false;
			}
		}

		return true;
	}

	public long findParent(long child) throws IOException, VHException {
		long current = topNode;
		long found = -1;
		long length = 0;
		TreeOnDiskNode todnChild = new TreeOnDiskNode(tod.retrieveElement(child, true, 13), hashSize);

		while(current != -1) {
			length++;
			TreeOnDiskNode todn = new TreeOnDiskNode(tod.retrieveElement(current, true, 14), hashSize);
			if (todn.getLeft() == child || todn.getRight() == child) {
				found = current;
				break;
			}

			int diff = Utils.compareValues(todnChild.getValue(), todn.getValue());
			if (diff < 0)
				current = todn.getLeft();
			else
				current = todn.getRight();
		}

		return found;
	}

	public void updateLeftPointer(long node, long p) throws IOException, VHException {
		TreeOnDiskNode todn = new TreeOnDiskNode(tod.retrieveElement(node, true, 15), hashSize);
		todn.setLeft(p);
		tod.storeElement(node, todn.getAsByteArray(), 11);
	}

	public void updateRightPointer(long node, long p) throws IOException, VHException {
		TreeOnDiskNode todn = new TreeOnDiskNode(tod.retrieveElement(node, true, 16), hashSize);
		todn.setRight(p);
		tod.storeElement(node, todn.getAsByteArray(), 12);
	}

	public long findMin(long start) throws IOException, VHException {
		long current = start;
		long length = 0;

		for(;;) {
			length++;
			TreeOnDiskNode todn = new TreeOnDiskNode(tod.retrieveElement(current, true, 12), hashSize);
			if (todn.getLeft() == -1)
				break;

			current = todn.getLeft();
		}

		return current;
	}

	long getSuccessor(long delNode) throws IOException, VHException {
		long successorParent = delNode;
		long successor = delNode;
		TreeOnDiskNode todnDelNode = new TreeOnDiskNode(tod.retrieveElement(delNode, true, 12), hashSize);
		long current = todnDelNode.getRight();		// go to right child
		while(current != -1)			// until no more
		{					// left children,
			successorParent = successor;
			successor = current;
			TreeOnDiskNode todn = new TreeOnDiskNode(tod.retrieveElement(current, true, 12), hashSize);
			current = todn.getLeft();	// go to left child
		}
		// if successor not
		TreeOnDiskNode todn = new TreeOnDiskNode(tod.retrieveElement(successor, true, 12), hashSize);
		if (successor != todnDelNode.getRight()) {	// right child,
			updateLeftPointer(successorParent, todn.getRight()); // successorParent.leftChild = successor.rightChild;

			updateRightPointer(successor, todnDelNode.getRight());	// successor.rightChild = delNode.rightChild;
		}

		return successor;
	}

	public boolean deleteNode(long node) throws IOException, VHException {
		nBlocksIn--;

		long parent = findParent(node);
		if (parent == -1) {
			System.out.println("parent not found");
			return false;
		}

		TreeOnDiskNode todnParent = new TreeOnDiskNode(tod.retrieveElement(parent, true, 21), hashSize);
		boolean isLeftChild = todnParent.getLeft() == node;

		TreeOnDiskNode todnNode = new TreeOnDiskNode(tod.retrieveElement(node, true, 22), hashSize);
		long todnNodeLeft = todnNode.getLeft();
		long todnNodeRight = todnNode.getRight();

		// if no children, simply delete it
		if (todnNodeLeft == -1 && todnNodeRight == -1) {
			// Log.log("TreeOnDisk: if no children, simply delete it");
			if (todnParent.getLeft() == node)
				todnParent.setLeft(-1);
			else
				todnParent.setRight(-1);
		}
		// if no right child, replace with left subtree
		else if (todnNodeRight == -1) {
			// Log.log("TreeOnDisk: if no right child, replace with left subtree");
			if (isLeftChild)
				todnParent.setLeft(todnNodeLeft);
			else
				todnParent.setRight(todnNodeLeft);
		}
		// if no left child, replace with right subtree
		else if (todnNodeLeft == -1) {
			// Log.log("TreeOnDisk: if no left child, replace with right subtree");
			if (isLeftChild)
				todnParent.setLeft(todnNodeRight);
			else
				todnParent.setRight(todnNodeRight);
		}
		// two children, so replace with inorder successor
		else {
			// Log.log("TreeOnDisk: two children, so replace with inorder successor");
			/*
			   long minNode = findMin(todnNodeRight);

			   if (todnParent.getLeft() == node)
			   todnParent.setLeft(minNode);
			   else
			   todnParent.setRight(minNode);

			   updateLeftPointer(minNode, todnNodeLeft);
			 */

			long successor = getSuccessor(node);
			// no longer as root moves assert node != 0;		// should be: root = successor

			if (isLeftChild)
				todnParent.setLeft(successor);
			else
				todnParent.setRight(successor);

			// connect successor to current's left child
			updateLeftPointer(successor, todnNodeLeft);	// successor.leftChild = current.leftChild;
		}

		tod.storeElement(parent, todnParent.getAsByteArray(), 13);
		assert b.getBit(node) == true;
		b.setBit(node, false);
		assert b.getBit(node) == false;
		// assert b.findBit0(maxNBlocks) != -1; due to setting new search start, first bit will not be found

		return true;
	}

	// http://code.google.com/p/visualgorithm/source/browse/trunk/src/algorithm/tree/?r=83
	public void rotateRight(long node) throws IOException, VHException {
		TreeOnDiskNode todnNode = new TreeOnDiskNode(tod.retrieveElement(node, true, 22), hashSize);

		long nodeLeft = todnNode.getLeft();
		TreeOnDiskNode todnLeft = new TreeOnDiskNode(tod.retrieveElement(nodeLeft, true, 22), hashSize);

		long nodeLeftRight = todnLeft.getRight();
		todnNode.setLeft(nodeLeftRight);

		long nodeParent = findParent(node);
		TreeOnDiskNode todnNodeParent = null;
		if (nodeParent != -1)
			todnNodeParent = new TreeOnDiskNode(tod.retrieveElement(nodeParent, true, 22), hashSize);

		if (nodeParent == -1) {
			topNode = nodeLeft;
			storeTopNode();
		}
		else {
			if (node == todnNodeParent.getRight())
				todnNodeParent.setRight(nodeLeft);
			else
				todnNodeParent.setLeft(nodeLeft);
		}

		todnLeft.setRight(node);

		tod.storeElement(node, todnNode.getAsByteArray(), 13);
		if (nodeParent != -1)
			tod.storeElement(nodeParent, todnNodeParent.getAsByteArray(), 13);
		tod.storeElement(nodeLeft, todnLeft.getAsByteArray(), 13);
	}

	public void rotateLeft(long node) throws IOException, VHException {
		TreeOnDiskNode todnNode = new TreeOnDiskNode(tod.retrieveElement(node, true, 22), hashSize);

		long nodeRight = todnNode.getRight();
		TreeOnDiskNode todnRight = new TreeOnDiskNode(tod.retrieveElement(nodeRight, true, 22), hashSize);

		long nodeRightLeft = todnRight.getLeft();
		todnNode.setRight(nodeRightLeft);

		long nodeParent = findParent(node);
		TreeOnDiskNode todnNodeParent = null;
		if (nodeParent != -1)
			todnNodeParent = new TreeOnDiskNode(tod.retrieveElement(nodeParent, true, 22), hashSize);

		if (nodeParent == -1) {
			topNode = nodeRight;
			storeTopNode();
		}
		else {
			if (node == todnNodeParent.getLeft())
				todnNodeParent.setLeft(nodeRight);
			else
				todnNodeParent.setRight(nodeRight);
		}

		todnRight.setLeft(node);

		tod.storeElement(node, todnNode.getAsByteArray(), 13);
		if (nodeParent != -1)
			tod.storeElement(nodeParent, todnNodeParent.getAsByteArray(), 13);
		tod.storeElement(nodeRight, todnRight.getAsByteArray(), 13);
	}
}
