/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.config;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.sockets.*;
import com.vanheusden.BlackHole.stats.*;
import com.vanheusden.BlackHole.storage.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.management.*;
import java.lang.StackTraceElement;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class HTTPServer implements Runnable {
	static String version = "$Id: HTTPServer.java 606 2013-07-06 22:07:22Z folkert $";
	String adapter;
	int port;
	int webServerHits, webServer404;
	CopyOnWriteArrayList<Thread> threads;
	Storage storage;
	Stats stats = Stats.getInstance();

	public HTTPServer(String adapter, int port, CopyOnWriteArrayList<Thread> threads, Storage storage) {
		this.adapter = adapter;
		this.port = port;
		this.threads = threads;
		this.storage = storage;
	}

	public void addPageHeader(List<String> whereTo) {
		whereTo.add("<HTML><BODY><table width=\"100%\" bgcolor=\"#000000\" cellpadding=\"0\" cellspacing=\"0\"><tr><td><A HREF=\"/\"><img src=\"http://www.vanheusden.com/images/vanheusden02.jpg?source=coffeesaint\" BORDER=\"0\"></A></td></tr></table><BR>\n");
		// whereTo.add("<TABLE><TR VALIGN=TOP><TD VALIGN=TOP ALIGN=LEFT WIDTH=225><IMG SRC=\"http://vanheusden.com/java/CoffeeSaint/coffeesaint.jpg?source=coffeesaint\" BORDER=\"0\" ALT=\"logo (C) Bas Schuiling\"></TD><TD ALIGN=LEFT>\n");
		whereTo.add("<TABLE><TR VALIGN=TOP><TD ALIGN=LEFT>\n");

		whereTo.add("<BR><H1>" + BlackHole.version + "</H1><BR><BR>");
	}

	public void addPageTail(List<String> whereTo, boolean mainMenu) {
		if (mainMenu)
			whereTo.add("<BR><BR><BR><A HREF=\"/\">Back to main menu</A></TD></TR></TABLE></BODY></HTML>");
		else
			whereTo.add("<BR><BR><BR></TD></TR></TABLE></BODY></HTML>");
	}

	public void sendReply_root(MyHTTPServer socket) throws IOException {
		List<String> reply = new ArrayList<String>();

		reply.add("HTTP/1.0 200 OK\r\n");
		reply.add("Connection: close\r\n");
		reply.add("Content-Type: text/html\r\n");
		reply.add("\r\n");
		addPageHeader(reply);
		reply.add("<A HREF=\"/cgi-bin/statistics.cgi\">statistics</A><BR>");
		reply.add("<A HREF=\"/graphs.html\">graphs</A><BR>");
		reply.add("<A HREF=\"/cgi-bin/stacktraces.cgi\">stack traces</A><BR>");
		reply.add("<A HREF=\"/cgi-bin/locks.cgi\">locks</A><BR>");
		addPageTail(reply, false);

		socket.sendReply(reply);
	}

	public void sendReply_quit(MyHTTPServer socket) throws IOException {
		List<String> reply = new ArrayList<String>();

		reply.add("HTTP/1.0 200 OK\r\n");
		reply.add("Connection: close\r\n");
		reply.add("Content-Type: text/html\r\n");
		reply.add("\r\n");
		addPageHeader(reply);
		reply.add("Terminating...");
		addPageTail(reply, true);

		socket.sendReply(reply);

		Thread stop = new Thread("HTTPServer: thread killer") {
			public void run() {
				for(;;) {
					boolean alive = false;
					for(Thread t : threads) {
						if (t.isAlive()) {
							alive = true;
							t.interrupt();
						}
					}
					if (alive == false)
						System.exit(0);

					try {
						Thread.sleep(250);
					}
					catch(InterruptedException ie) {
						break;
					}
				}
			}
		};

		stop.start();
	}

	public void sendReply_cgibin_statistics_cgi(MyHTTPServer socket) throws IOException {
		List<String> reply = new ArrayList<String>();

		reply.add("HTTP/1.0 200 OK\r\n");
		reply.add("Connection: close\r\n");
		reply.add("Content-Type: text/html\r\n");
		reply.add("\r\n");
		addPageHeader(reply);
		try {
			reply.add("<TABLE>\n");
			reply.add("<TR><TD>Total running time:</TD><TD>" + ((double)(System.currentTimeMillis() - BlackHole.runningSince) / 1000.0) + "s</TD></TR>\n");
			reply.add("<TR><TD>Number of webserver hits:</TD><TD>" + webServerHits + "</TD></TR>\n");
			reply.add("<TR><TD>Number of 404 pages serverd:</TD><TD>" + webServer404 + "</TD></TR>\n");
			reply.add("</TABLE>\n");
			reply.add("<BR>\n");
			reply.add("<TABLE>\n");
			OperatingSystemMXBean osmxb = ManagementFactory.getOperatingSystemMXBean();
			reply.add("<TR><TD>System load:</TD><TD>" + osmxb.getSystemLoadAverage() + "</TD></TR>\n");
			ThreadMXBean tmxb = ManagementFactory.getThreadMXBean();
			reply.add("<TR><TD>CPU time used:</TD><TD>" + (tmxb.getCurrentThreadCpuTime() / 1000000000) + "s</TD></TR>\n");
			reply.add("</TABLE>\n");
			reply.add("<BR>\n");
//			reply.add("<TABLE>\n");
//			reply.add("<TR><TD>% gained:</TD><TD>" + (storage.getPercentageBlockUseDecrease()) + "%</TD></TR>\n");
//			reply.add("</TABLE>\n");
//			reply.add("<BR>\n");
//			double avg = (double)TreeOnDisk.totalNSearchLengths.get() / (double)TreeOnDisk.nSearches.get();
//			double sd = Math.sqrt((TreeOnDisk.stddevLengths.get() / (double)TreeOnDisk.nSearches.get()) - Math.pow(avg, 2.0));
//			reply.add("<TR><TD>Stddev tree depth:</TD><TD>" + sd + "</TD></TR>\n");

			reply.add("<PRE>");
			for(String s : stats.getList())
				reply.add("" + s + "\n");
			reply.add("</PRE>");
		}
		catch(ArithmeticException ae) {
			reply.add("Exception: " + ae);
		}

		addPageTail(reply, true);

		socket.sendReply(reply);
	}

	public void sendReply_cgibin_stacktraces_cgi(MyHTTPServer socket) throws IOException {
		List<String> reply = new ArrayList<String>();

		reply.add("HTTP/1.0 200 OK\r\n");
		reply.add("Connection: close\r\n");
		reply.add("Content-Type: text/html\r\n");
		reply.add("\r\n");
		addPageHeader(reply);
		try {
			reply.add("<TABLE BORDER=1>\n");
			Map<Thread,StackTraceElement[]> traces = Thread.getAllStackTraces();
			Iterator<Thread> it = traces.keySet().iterator();
			while(it.hasNext()) {
				Thread current = it.next();

				reply.add("<TR><TH COLSPAN=2>" + current.getName() + "</TH><TH COLSPAN=2>" + current.getId() + "</TH></TR>\n");
				StackTraceElement [] ste = traces.get(current);
				for(StackTraceElement currentSte : ste) {
					reply.add("<TR><TD>" + currentSte.getClassName() + "</TD><TD>" + currentSte.getFileName() + "</TD><TD>" + currentSte.getLineNumber() + "</TD><TD>" + currentSte.getMethodName() + "</TD></TR>\n");
				}
			}

			reply.add("</TABLE>\n");
			reply.add("<BR>\n");
		}
		catch(ArithmeticException ae) {
			reply.add("Exception: " + ae);
		}

		addPageTail(reply, true);

		socket.sendReply(reply);
	}

	public void sendReply_cgibin_locks_cgi(MyHTTPServer socket) throws IOException {
		List<String> reply = new ArrayList<String>();

		reply.add("HTTP/1.0 200 OK\r\n");
		reply.add("Connection: close\r\n");
		reply.add("Content-Type: text/html\r\n");
		reply.add("\r\n");
		addPageHeader(reply);
		try {
			reply.add("<TABLE BORDER=1>\n");
			ThreadMXBean bean = ManagementFactory.getThreadMXBean();
			ThreadInfo[] tis = bean.getThreadInfo(bean.getAllThreadIds(), true, true);
			for(ThreadInfo ti : tis) {
				reply.add("<TR><TD>" + ti.getLockName() + "</TD><TD>" + ti.getLockOwnerName() + "</TD><TD>" + ti.getThreadName() + "</TD><TD>" + ti.getLockInfo() + "</TD></TR>\n");
			}

			reply.add("</TABLE>\n");
			reply.add("<BR>\n");
		}
		catch(ArithmeticException ae) {
			reply.add("Exception: " + ae);
		}

		addPageTail(reply, true);

		socket.sendReply(reply);
	}

	public void sendReply_imagejpg(MyHTTPServer socket, BufferedImage bi) {
		String header = "HTTP/1.0 200 OK\r\nConnection: close\r\nContent-Type: image/png\r\n\r\n";
		
		try {
			socket.getOutputStream().write(header.getBytes());
			ImageIO.write(bi, "png", socket.getOutputStream());
			socket.close();
		}
		catch(IOException ioe) {
			// really don't care if the transmit failed; browser
			// probably closed session
			// don't care if we could display the image or not
		}
	}

	public void sendReply_404(MyHTTPServer socket, String url) throws IOException {
		List<String> reply = new ArrayList<String>();

		reply.add("HTTP/1.0 404 Url not known\r\n");
		reply.add("Connection: close\r\n");
		reply.add("Content-Type: text/html\r\n");
		reply.add("\r\n");
		addPageHeader(reply);
		reply.add("URL \"" + url + "\" not known!");
		addPageTail(reply, true);

		socket.sendReply(reply);
	}

	public void run() {
		MyHTTPServer socket;

		try {
			socket = new MyHTTPServer(adapter, port);

			for(;;) {
				try {
					List<String> request = socket.acceptConnectionGetRequest();
					if (request == null) {
						Log.log(LogLevel.LOG_INFO, "HTTPServer got interrupted");
						break;
					}
					String url = request.get(0).substring(4).trim(), pars = null;
					int space = url.indexOf(" ");
					if (space != -1) {
						url = url.substring(0, space);
					}

					int q = url.indexOf("?");
					if (q != -1) {
						pars = url.substring(q + 1);
						url = url.substring(0, q);
					}

					webServerHits++;

					if (url.equals("/") || url.equals("/index.html"))
						sendReply_root(socket);
					else if (url.equals("/cgi-bin/statistics.cgi"))
						sendReply_cgibin_statistics_cgi(socket);
					else if (url.equals("/cgi-bin/stacktraces.cgi"))
						sendReply_cgibin_stacktraces_cgi(socket);
					else if (url.equals("/cgi-bin/locks.cgi"))
						sendReply_cgibin_locks_cgi(socket);
					else if (url.equals("/cgi-bin/quit.cgi"))
						sendReply_quit(socket);
					else
					{
						sendReply_404(socket, url);
						webServer404++;
					}
				}
				catch(IOException e)
				{
					System.err.println("Exception during command processing: " + e);
					Log.log(LogLevel.LOG_WARN, "HTTPServer exception: " + e);
				}
			}
		}
		catch(IOException e)
		{
			Log.log(LogLevel.LOG_CRIT, "HTTPServer Cannot create listen socket: " + e);
			System.exit(127);
		}
	}
}
