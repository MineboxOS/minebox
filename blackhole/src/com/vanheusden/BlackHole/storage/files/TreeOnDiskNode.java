/* Released under GPL 2.0
 * (C) 2010 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage.files;

import com.vanheusden.BlackHole.Utils;

public class TreeOnDiskNode {
	static String version = "$Id: TreeOnDiskNode.java 606 2013-07-06 22:07:22Z folkert $";
	byte [] value;
	long left, right;
	int hashSize;

	public TreeOnDiskNode(byte [] value, long left, long right, int hashSize) {
		assert value != null;
		this.value = value;
		this.left = left;
		this.right = right;
		this.hashSize = hashSize;
	}

	public TreeOnDiskNode(byte [] in, int hashSize) {
		assert in != null;
		assert in.length == (hashSize + 8 + 8);
		this.value = Utils.arrayExtract(in, 0, hashSize);
		this.left = Utils.byteArrayToLong(in, hashSize + 0);
		this.right = Utils.byteArrayToLong(in, hashSize + 8);
		this.hashSize = hashSize;
	}

	public String toString() {
		return "left: " + left + ", right: " + right;
	}

	public byte [] getValue() {
		return value;
	}

	public long getLeft() {
		return left;
	}

	public long getRight() {
		return right;
	}

	public void setLeft(long left) {
		this.left = left;
	}

	public void setRight(long right) {
		this.right = right;
	}

	public byte [] getAsByteArray() {
                byte [] out = new byte[hashSize + 8 + 8];

                Utils.arrayCopy(out, 0, value);
		Utils.longToByteArray(left, out, hashSize + 0);
		Utils.longToByteArray(right, out, hashSize + 8);

                return out;
	}
}
