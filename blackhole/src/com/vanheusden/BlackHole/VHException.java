/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole;

public class VHException extends Exception {
	static String version = "$Id: VHException.java 606 2013-07-06 22:07:22Z folkert $";
	public VHException() {
	}

	public VHException(Throwable cause) {
		super(cause);
	}

	public VHException(String message) {
		super(message);
	}

	public VHException(String message, Throwable cause) {
		super(message, cause);
	}
}
