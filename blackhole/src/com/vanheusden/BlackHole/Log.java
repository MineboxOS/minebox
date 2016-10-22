/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole;

import com.vanheusden.BlackHole.Utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.CopyOnWriteArrayList;

public class Log {
	static String version = "$Id: Log.java 606 2013-07-06 22:07:22Z folkert $";
	static LogLevel debugLevel = LogLevel.LOG_DEBUG, logDebugLevel = LogLevel.LOG_DEBUG;
	static String syslogHost;
	static String logfile;

	static final CopyOnWriteArrayList<OutputStream> clients = new CopyOnWriteArrayList<OutputStream>();

	public static void setDebugLevel(LogLevel dl) {
		debugLevel = dl;
	}

	public static void setLogfileDebugLevel(LogLevel lfdl) {
		logDebugLevel = lfdl;
	}

	public static void setSyslogHost(String host) {
		syslogHost = host;
	}

	public static void setLogfile(String lf) {
		logfile = lf;
	}

	private Log() {
		assert false;
	}

	static public void addOutput(OutputStream client) {
		clients.add(client);
	}

	static public void showAssertionError(AssertionError e) {
		Log.log(LogLevel.LOG_CRIT, "Exception: " + e);
		Log.log(LogLevel.LOG_CRIT, "Details: " + e.getMessage());
		Log.log(LogLevel.LOG_CRIT, "Thread: " + Thread.currentThread().getName());
		Log.log(LogLevel.LOG_CRIT, "Stack-trace:");
		for(StackTraceElement ste: e.getStackTrace())
		{
			Log.log(LogLevel.LOG_CRIT, " " + ste.getClassName() + ", "
					+ ste.getFileName() + ", "
					+ ste.getLineNumber() + ", "
					+ ste.getMethodName() + ", "
					+ (ste.isNativeMethod() ?
						"is native method" : "NOT a native method"));
		}
	}

	static public void showException(Exception e) { // OK
		Log.log(LogLevel.LOG_CRIT, "Exception: " + e);
		Log.log(LogLevel.LOG_CRIT, "Details: " + e.getMessage());
		Log.log(LogLevel.LOG_CRIT, "Thread: " + Thread.currentThread().getName());
		Log.log(LogLevel.LOG_CRIT, "Stack-trace:");
		for(StackTraceElement ste: e.getStackTrace())
		{
			Log.log(LogLevel.LOG_CRIT, " " + ste.getClassName() + ", "
					+ ste.getFileName() + ", "
					+ ste.getLineNumber() + ", "
					+ ste.getMethodName() + ", "
					+ (ste.isNativeMethod() ?
						"is native method" : "NOT a native method"));
		}
	}

        static public String formatDate(Calendar when) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat("MMM.dd  HH:mm:ss");

                return dateFormatter.format(when.getTime());
        }

	public static void sendViaSyslog(LogLevel ll, String what) {
		if (syslogHost != null) {
			String slMsg = "<" + (1 * 8 + ll.getLevel()) + ">" + what;
			byte [] msg = Utils.strToBytes(slMsg);

			try {
				InetAddress address = InetAddress.getByName(syslogHost);
				DatagramPacket packet = new DatagramPacket(msg, msg.length, address, 514);
				DatagramSocket dsocket = new DatagramSocket();
				dsocket.send(packet);
				dsocket.close();
			}
			catch(UnknownHostException uhe) { System.err.println("Syslog: unknown host " + syslogHost + ": " + uhe); }
			catch(SocketException se) { System.err.println("Syslog: socket exception: " + se); }
			catch(IOException ie) { System.err.println("Syslog: I/O exception: " + ie); }
		}
	}

	public static String genLogLine(LogLevel ll, String what) {
		Calendar when = Calendar.getInstance();
		String ts = formatDate(when);

		String msgTs = ts + " [" + ll + "] " + what;

		return msgTs;
	}

	public static void log(LogLevel ll, String what) {
		String msg = null;

		if (ll.getLevel() <= debugLevel.getLevel()) {
			if (msg == null)
				msg = genLogLine(ll, what);

			sendViaSyslog(ll, msg);

			System.out.println(msg);

			int index=0;
			String msgOut = msg + "\r\n";
			byte [] msgOutAr = msgOut.getBytes();
			while(index < clients.size()) {
				try {
					clients.get(index).write(msgOutAr, 0, msgOutAr.length);
					clients.get(index).flush();
					index++;
				}
				catch(IOException ioe) {
					System.out.println("Cannot send to client: " + ioe);
					clients.remove(index);
				}
			}
		}

		if (ll.getLevel() <= logDebugLevel.getLevel()) {
			if (logfile != null) {
				if (msg == null)
					msg = genLogLine(ll, what);

				try {
					BufferedWriter out = new BufferedWriter(new FileWriter(logfile, true));
					out.write(msg, 0, msg.length());
					out.newLine();
					out.close();
				}
				catch(IOException ie) {
					System.err.println("Cannot access " + logfile + ": " + ie);
				}
			}
		}
	}
}
