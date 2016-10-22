/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

public class SCSI_response {
	protected byte [] bytes;

	public byte [] getAsByteArray() {
                return bytes;
        }

	public byte [] getAsByteArray(int maxSize) {
		if (maxSize == -1)
			return bytes;

		int outSize = Math.min(maxSize, bytes.length);
		byte [] out = new byte[outSize];

		System.arraycopy(bytes, 0, out, 0, outSize);

		return out;
        }
}
