/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

public class EncryptionParameters {
	static String version = "$Id: EncryptionParameters.java 606 2013-07-06 22:07:22Z folkert $";
	String cipher;
	String password;

	public EncryptionParameters(String password, String cipher) {
		this.cipher = cipher;
		this.password = password;
	}

	public String getCipher() {
		return cipher;
	}

	public String getPassword() {
		return password;
	}
}
