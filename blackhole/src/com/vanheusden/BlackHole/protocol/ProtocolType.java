/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.protocol;

public enum ProtocolType { PROTOCOL_NBD, PROTOCOL_ISCSI, PROTOCOL_IMDISK;
	static String version = "$Id: ProtocolType.java 606 2013-07-06 22:07:22Z folkert $";
		final public String toString() {
			switch(this) {
				case PROTOCOL_NBD:
					return "nbd";
				case PROTOCOL_ISCSI:
					return "iSCSI";
				case PROTOCOL_IMDISK:
					return "IMDisk";
			}
			return null;
		}
};
