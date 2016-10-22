/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

public enum HashType { MD2, MD5, SHA1, SHA256, SHA384, SHA512;
	static String version = "$Id: HashType.java 606 2013-07-06 22:07:22Z folkert $";
		final public String getName() {
			switch(this) {
				case MD2:
					return "MD2";
				case MD5:
					return "MD5";
				case SHA1:
					return "SHA1";
				case SHA256:
					return "SHA-256";
				case SHA384:
					return "SHA-384";
				case SHA512:
					return "SHA-512";
			}

			return null;
		}

		final static public HashType getType(String name) {
			if (name.equalsIgnoreCase("MD2"))
				return MD2;
			if (name.equalsIgnoreCase("MD5"))
				return MD5;
			if (name.equalsIgnoreCase("SHA1"))
				return SHA1;
			if (name.equalsIgnoreCase("SHA-256"))
				return SHA256;
			if (name.equalsIgnoreCase("SHA-384"))
				return SHA384;
			if (name.equalsIgnoreCase("SHA-512"))
				return SHA512;

			return null;
		}
 };
