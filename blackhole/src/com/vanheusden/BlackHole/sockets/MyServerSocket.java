/* Released under GPL 2.0
 * (C) 2009 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.sockets;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class MyServerSocket {
	static String version = "$Id: MyServerSocket.java 606 2013-07-06 22:07:22Z folkert $";
	private ServerSocket serverSocket = null;

	public MyServerSocket(String adapter, int port) throws IOException {
		serverSocket = new ServerSocket();
		serverSocket.bind(new InetSocketAddress(adapter, port));
		serverSocket.setSoTimeout(500);
	}

	public MyClientSocket acceptConnection() throws IOException {
		Socket socket = null;
                for(;;) {
                        try {
                                socket = serverSocket.accept();
                                if (socket != null)
                                        break;
                        }
                        catch(SocketTimeoutException ste) {
                        }

                        if (Thread.interrupted())
                                return null;
                }
		if (socket != null)
			return new MyClientSocket(socket);

		return null;
	}

	public void close() throws IOException {
		serverSocket.close();
		serverSocket = null;
	}
}
