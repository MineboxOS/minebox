/* Released under GPL 2.0
 * (C) 2011-2013 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kyotocabinet.*;

class TransactionCache {
	Map<byte [], byte[]> update_add_records = new HashMap<byte [], byte []>();
	Set<byte []> delete_records = new HashSet<byte []>();
	DB db;

	TransactionCache(DB db) {
		this.db = db;
	}

	void update_add_record(byte [] key, byte [] value) {
		delete_records.remove(key);

		update_add_records.put(key, value);
	}

	void delete_record(byte [] key) {
		update_add_records.remove(key);

		delete_records.add(key);
	}

	Map<byte [], byte[]> get_update_add_records() {
		Map<byte [], byte[]> dummy = update_add_records;

		update_add_records = null;

		return dummy;
	}

	Set<byte []> get_delete_records() {
		Set<byte []> dummy = delete_records;

		delete_records = null;

		return dummy;
	}

	byte [] get_by_key(byte [] key) {
		byte [] value = update_add_records.get(key);

		if (value == null)
			value = db.get(key);

		return value;
	}
}
