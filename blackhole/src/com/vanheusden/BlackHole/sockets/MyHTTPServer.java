/* Released under GPL 2.0
 * (C) 2009 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.sockets;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class MyHTTPServer {
	static String version = "$Id: MyHTTPServer.java 606 2013-07-06 22:07:22Z folkert $";
	private Socket socket;
	private BufferedReader inputStream;
	private BufferedWriter outputStream;
	private ServerSocket serverSocket = null;

	public MyHTTPServer(String adapter, int port) throws IOException {
		serverSocket = new ServerSocket();
		serverSocket.bind(new InetSocketAddress(adapter, port));
		serverSocket.setSoTimeout(500);
	}

	public List<String> acceptConnectionGetRequest() throws IOException {
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

		socket.setKeepAlive(true);

		inputStream  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		outputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

		List<String> request = new ArrayList<String>();
		for(;;) {
			String line = inputStream.readLine();

			if (line.equals(""))
				break;

			request.add(line);
		}

		return request;
	}

	public void sendReply(List<String> reply) throws IOException {
		for(String currentLine : reply)
			outputStream.write(currentLine, 0, currentLine.length());

		outputStream.flush();

		socket.close();
	}

	public OutputStream getOutputStream() throws IOException {
		return socket.getOutputStream();
	}

	public void close() throws IOException {
		outputStream.flush();

		if (socket != null)
			socket.close();
		socket = null;
	}
}
