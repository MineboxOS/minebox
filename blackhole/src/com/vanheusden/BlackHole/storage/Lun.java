/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import java.util.ArrayList;
import java.util.List;

public class Lun {
	static String version = "$Id: Lun.java 606 2013-07-06 22:07:22Z folkert $";
	final long GB = 1024L * 1024L * 1024L;
	long size;
	List<Integer> map = new ArrayList<Integer>();

	Lun() {
		size = 0;
	}

	Lun(long size, List<Integer> p) {
		this.size = size;
		grow(size, p);
	}

	long getSize() {
		return size;
	}

	List<Integer> getPartitions() {
		return map;
	}

	int longToInt(long value) {
		assert value <= 2147483647;
		return (int)value;
	}

	void grow(long newSize, List<Integer> partitions) {
		int nPartitions = longToInt((size + GB - 1) / GB);
		assert nPartitions >= map.size();
		map.addAll(partitions);
		this.size = newSize;
	}

	long getMapOffset(long offsetIn) {
		int partition = longToInt(offsetIn / GB);
		long offset = offsetIn % GB;
		return (map.get(partition) * GB) + offset; // get offset in datastore and add offset in that partition
	}
}
