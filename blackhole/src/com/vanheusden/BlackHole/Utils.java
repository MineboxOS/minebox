/* Released under GPL 2.0
 * (C) 2009-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole;

public class Utils {
	static String version = "$Id: Utils.java 606 2013-07-06 22:07:22Z folkert $";
	static public void intJoinIgnoreEx(Thread t) {
		t.interrupt();

		try {
			t.join();
		}
		catch(InterruptedException ie) {
			System.err.println("Interrupt during join on " + t.getName());
		}
	}

        static public int byteArrayToInt(byte [] in) {
                assert in.length == 4;
                int out = 0;

                for(int index=0; index<4; index++) {
                        out <<= 8;
                        out |= in[index] & 0xff;
                }

                return out;
        }

        static public int byteArrayToInt(byte [] in, int offset) {
                int out = 0;

                for(int index=0; index<4; index++) {
                        out <<= 8;
                        out |= in[offset + index] & 0xff;
                }

                return out;
        }

        static public byte [] intToByteArray(int in) {
                byte [] out = new byte[4];

                for(int index=3; index>=0; index--) {
                        out[index] = (byte)in;
                        in >>= 8;
                }

                return out;
        }

	static public long byteArrayToLong(byte [] in) {
		assert in.length == 8;

		return ((((((((long)(in[0] & 0xff) << 8) | (long)(in[1] & 0xff)) << 8 | (long)(in[2] & 0xff)) << 8 | (long)(in[3] & 0xff)) << 8 | (long)(in[4] & 0xff)) << 8 | (long)(in[5] & 0xff)) << 8 | (long)(in[6] & 0xff)) << 8 | (long)(in[7] & 0xff);
	}

	static public long byteArrayToLong(byte [] in, int offset) {
		return ((((((((long)(in[offset + 0] & 0xff) << 8) | (long)(in[offset + 1] & 0xff)) << 8 | (long)(in[offset + 2] & 0xff)) << 8 | (long)(in[offset + 3] & 0xff)) << 8 | (long)(in[offset + 4] & 0xff)) << 8 | (long)(in[offset + 5] & 0xff)) << 8 | (long)(in[offset + 6] & 0xff)) << 8 | (long)(in[offset + 7] & 0xff);
	}

	static public byte [] longToByteArray(long in) {
		byte [] out = new byte[8];

		for(int index=7; index>=0; index--) {
			out[index] = (byte)in;
			in >>= 8;
		}

		return out;
	}

	static public void longToByteArray(long in, byte [] out, int offset) {
		for(int index=7; index>=0; index--) {
			out[offset + index] = (byte)in;
			in >>= 8;
		}
	}

        static public void putU32(byte [] out, int offset, int data)
        {
		out[offset++] = (byte)(data >> 24);
		out[offset++] = (byte)(data >> 16);
		out[offset++] = (byte)(data >>  8);
		out[offset++] = (byte)(data      );
        }

        static public void putU64(byte [] out, int offset, long data)
        {
		out[offset++] = (byte)(data >> 56);
		out[offset++] = (byte)(data >> 48);
		out[offset++] = (byte)(data >> 40);
		out[offset++] = (byte)(data >> 32);
		out[offset++] = (byte)(data >> 24);
		out[offset++] = (byte)(data >> 16);
		out[offset++] = (byte)(data >>  8);
		out[offset++] = (byte)(data      );
        }

	static public void arrayCopy(byte [] dest, int offset, byte [] in) {
		System.arraycopy(in, 0, dest, offset, in.length);
	}

	static public byte [] arrayExtract(byte [] in, int offset, int len) {
		byte [] out = new byte[len];
		System.arraycopy(in, offset, out, 0, len);
		return out;
	}

	static public byte [] arrayDuplicate(byte [] in) {
		byte [] out = new byte[in.length];
		arrayCopy(out, 0, in);
		return out;
	}

	public static int compareValues(byte [] in1, byte [] in2) {
		assert in1.length == in2.length;

		for(int index=0; index<in1.length; index++) {
			int diff = (in1[index] & 0xff) - (in2[index] & 0xff);
			if (diff != 0)
				return diff;
		}

		return 0;
	}

	public static byte [] strToBytes(String in) {
		int len = in.length();
		byte [] out = new byte[len];
		for(int index=0; index<len; index++)
			out[index] = (byte)in.charAt(index);
		return out;
	}

	public static String byteArrayToHexString(byte[] b) {
		StringBuffer sb = new StringBuffer(b.length * 2);

		for (int i = 0; i < b.length; i++){
			int v = b[i] & 0xff;

			if (v < 16)
				sb.append('0');

			sb.append(Integer.toHexString(v));
		}

		return sb.toString();
	}
}
