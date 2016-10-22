/* Released under GPL 2.0
 * (C) 2009-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.sockets;

import com.vanheusden.BlackHole.VHException;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class MyClientSocket {
	static String version = "$Id: MyClientSocket.java 606 2013-07-06 22:07:22Z folkert $";
	private Socket socket;
	private InputStream inputStream;
	private OutputStream outputStream;

	public MyClientSocket(Socket s) throws IOException {
		socket = s;
		socket.setKeepAlive(true);
		// socket.setTcpNoDelay(true);

		inputStream = socket.getInputStream();
		outputStream = socket.getOutputStream();
	}

	public void setTcpNoDelay() throws SocketException {
		socket.setTcpNoDelay(true);
	}

	public void closeSocket() throws IOException {
		if (socket != null)
			socket.close();
	}

	public void getBytes(byte [] out, int offset, int len) throws IOException, VHException {
		int totalRead = 0;
		int left = len;
		while(left > 0) {
			int nRead = inputStream.read(out, offset, left);
			if (nRead == -1)
				throw new VHException("read from socket error, expected: " + len + ", got: " + totalRead);
			if (Thread.interrupted())
				throw new VHException("read from socket interrupted");
			offset += nRead;
			left -= nRead;
			totalRead += nRead;
		}

		assert totalRead == len;
	}

	public void getBytes(byte [] out) throws IOException, VHException {
		getBytes(out, 0, out.length);
	}

	public void putBytes(byte [] bytes) throws IOException {
		outputStream.write(bytes);
	}

	public void putBytes(byte [] bytes, int offset, int len) throws IOException {
		outputStream.write(bytes, offset, len);
	}

	public int getU32() throws IOException, VHException {
		byte [] in = new byte[4];

		getBytes(in);

		return
			((in[0] & 0xff) << 24) +
			((in[1] & 0xff) << 16) +
			((in[2] & 0xff) <<  8) +
			((in[3] & 0xff)      );
	}

	public long getU64() throws IOException, VHException {
		long data1 = getU32();
		long data2 = getU32();

		return (data1 << 32) + (data2 & 0xFFFFFFFFL);
	}

	public void putU32(int data) throws IOException {
		byte [] out = {
			(byte)(data >> 24),
			(byte)(data >> 16),
			(byte)(data >>  8),
			(byte)(data      ) };

		putBytes(out);
	}

	public void putU64(long data) throws IOException {
		putU32((int)(data >> 32));
		putU32(((int)data) & 0xffffffff);
	}

	public void flush() throws IOException {
		outputStream.flush();
	}

	public String getName() {
		return socket.toString();
	}

	public Socket getSocket() {
		return socket;
	}
}
