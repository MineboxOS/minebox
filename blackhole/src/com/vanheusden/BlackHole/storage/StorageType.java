/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

public enum StorageType { STORAGE_FILES, STORAGE_SQL, STORAGE_KC, STORAGE_MONGODB;
	final public String toString() {
		switch(this) {
			case STORAGE_FILES:
				return "files";
			case STORAGE_SQL:
				return "sql";
			case STORAGE_KC:
				return "kyoto-cabinet";
			case STORAGE_MONGODB:
				return "mongodb";
		}
		return null;
	}

	final static public StorageType getType(String name) {
		if (name.equalsIgnoreCase("files"))
			return STORAGE_FILES;
		if (name.equalsIgnoreCase("sql"))
			return STORAGE_SQL;
		if (name.equalsIgnoreCase("kc") || name.equalsIgnoreCase("kyoto-cabinet"))
			return STORAGE_KC;
		if (name.equalsIgnoreCase("mongodb"))
			return STORAGE_MONGODB;

		return null;
	}
};
