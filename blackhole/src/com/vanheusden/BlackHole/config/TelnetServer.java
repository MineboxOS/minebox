/* Released under GPL 2.0
 * (C) 2010 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.config;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.auth.*;
import com.vanheusden.BlackHole.mirrors.*;
import com.vanheusden.BlackHole.protocol.*;
import com.vanheusden.BlackHole.snapshots.*;
import com.vanheusden.BlackHole.storage.*;
import com.vanheusden.BlackHole.stats.*;
import com.vanheusden.BlackHole.zoning.*;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class TelnetServer implements Runnable {
	static String version = "$Id: TelnetServer.java 606 2013-07-06 22:07:22Z folkert $";
	String adapter;
	int port;
	Storage storage;
	Config config;
	LunManagement lm;
	MirrorManager mm;
	SnapshotManager sm;
	ZoneManager zm;

	class TelnetServerClient implements Runnable {
		Socket cs;
		Storage sp;
		DataInputStream ir;
		OutputStream os;
		Config c;
		LunManagement lm;
		MirrorManager mm;
		SnapshotManager sm;
		ZoneManager zm;
		final long GB = 1024L * 1024L * 1024L;

		TelnetServerClient(Socket cs, Storage sp, Config c, LunManagement lm, MirrorManager mm, SnapshotManager sm, ZoneManager zm) throws IOException {
			this.cs = cs;
			this.sp = sp;
			this.c = c;
			this.lm = lm;
			this.mm = mm;
			this.sm = sm;
			this.zm = zm;

			ir = new DataInputStream(cs.getInputStream());
			os = new BufferedOutputStream(cs.getOutputStream());

			Log.addOutput(os);
		}

		void write(byte [] what) throws IOException {
			os.write(what, 0, what.length);
		}

		void echo(boolean on) throws IOException {
			byte [] dont_auth = { (byte)0xff, (byte)0xf4, 0x25 };
			byte [] suppress_goahead = { (byte)0xff, (byte)0xfb, 0x03 };
			byte [] dont_linemode = { (byte)0xff, (byte)0xfe, 0x22 };
			byte [] dont_new_env = { (byte)0xff, (byte)0xfe, 0x27 };
			byte [] will_echo = { (byte)0xff, (byte)0xfb, 0x01 };
			byte [] wont_echo = { (byte)0xff, (byte)0xfc, 0x01 };
			byte [] dont_echo = { (byte)0xff, (byte)0xfe, 0x01 };
			byte [] do_echo = { (byte)0xff, (byte)0xfd, 0x01 };
			byte [] do_noecho = { (byte)0xff, (byte)0xfd, 0x2d };
			byte [] dont_noecho = { (byte)0xff, (byte)0xfe, 0x2d };

			write(dont_auth);
			write(suppress_goahead);
			write(dont_linemode);
			write(dont_new_env);
			if (on) {
				write(wont_echo);
				write(do_echo);
				write(dont_noecho);
			}
			else {
				write(will_echo);
				write(dont_echo);
				write(do_noecho);
			}
			os.flush();
		}

		void send(String s) throws IOException {
			write(s.getBytes());
			os.flush();
		}

		public void backspace(int n) throws IOException {
			for(int count=0; count<n; count++) {
				os.write(8);
				os.write(' ');
				os.write(8);
			}
		}

		public String readLine(boolean visible, int maxLen) throws IOException, VHException {
			String str = "";

			for(;;) {
				int c = ir.readByte();
				if (c == -1) { // TELNET: 0xff
					ir.readByte(); // 2 parameters
					ir.readByte();
				}
				else if (c == 8 || c == 127) {
					if (str.length() > 0) {
						str = str.substring(0, str.length() - 1);
						backspace(1);
					}
					else
						os.write(7);
				}
				else if (c == 13 || c == 10) {
					return str;
				}
				else if (c >= 32 && c < 127 && str.length() < maxLen) {
					str += (char)c;
					if (visible)
						os.write(c);
				}
				else if (c == 21) { // ^U
					backspace(str.length());
					str = "";
				}
				else if (c == 23) { // ^W
					int len = str.length();
					int index = len - 1;
					while(index > 0 && str.charAt(index) == ' ')
						index--;
					while(index > 0 && str.charAt(index) != ' ')
						index--;
					while(index > 0 && str.charAt(index) == ' ')
						index--;
					index++;
					backspace(len - index);
					str = str.substring(0, index);
				}
				else if (c == 12)
					os.write(12);
				else if (c == 0) {
					// telnet protocol says: send a 0x00 after 0x0d
				}
				else {  
					os.write(7);
				}

				os.flush();
			}
		}

		public void help(boolean useLDAP) throws IOException {
			send("snapshot         snapshot to file\r\n");
			send("encsnapshot      encrypted snapshot to file\r\n");
			send("scount           number of running snapshots\r\n");
			send("slist            list running snapshots\r\n");
			send("\r\n");
			send("df               show diskspace of " + config.getDatastorePath() + ". Please note that this doesn't work with the MySQL backend.\r\n");
			send("\r\n");
			send("listds           list datastore\r\n");
			send("growds           grow datastore (size in GB)\r\n");
			send("\r\n");
			send("listmirrors      list mirror(s)\r\n");
			send("addmirror ...    add a mirror to the system\r\n");
			send("resyncmirror x   completely resync mirror 'x' - use for new mirrors to initialize them\r\n");
			send("\r\n");
			send("listluns         list all luns\r\n");
			send("addnbdlun        add nbd lun: adapter port size name barriers (size in GB, barriers: true/false)\r\n");
			send("addimdisklun     add IMDisk lun: adapter port size name barriers (size in GB, barriers not implemented in IMDisk)\r\n");
			send("growlun          grow lun (size in GB)\r\n");
			send("dellun           deletes a lun, requires the lun number, see listluns\r\n");
			send("copylun          duplicate a lun. requires lun number and yes (blocking) or no (non-blocking)\r\n");
			send("stoplun          stop the listener for this lun\r\n");
			send("startlun         start the listener for this lun\r\n");
			send("killclients      terminate all sessions for a given lun\r\n");
			send("lunset lun what value  change a parameter of a lun:\r\n");
			send("                 adapter: adapter to listen on\r\n");
			send("                 port: port to listen on\r\n");
			send("\r\n");
			send("zoneadd lun...   add a zone. parameters: lun number, type, parameters\r\n");
			send("                 type: ip       ip address\r\n");
			send("                       netmask  network address + netmask\r\n");
			send("                       hostname hostname of the host\r\n");
			send("zonedel lun id   delete a zone, see zonelist to get a list of ids\r\n");
			send("zonelist lun     lists all zones for the selected lun\r\n");
			send("\r\n");
			if (!useLDAP) {
				send("adduser          adds a user\r\n");
				send("deluser          deletes a user\r\n");
				send("passwd           changes the password of the current user\r\n");
				send("\r\n");
			}
			send("uptime           show up-time\r\n");
			send("stats            show statistics\r\n");
			send("sysload          show system load\r\n");
			send("version          show BlackHole version\r\n");
			send("\r\n");
			send("flush            commit buffers to disk\r\n");
			send("help             this help\r\n");
			send("quit / exit      terminate telnet session\r\n");
			send("terminate        terminate BlackHole - running snapshots are aborted\r\n");
		}

		boolean askYesNo(String what) throws VHException, IOException {
			for(;;) {
				send(what + " (y/n)\r\n");

				String line = readLine(true, 4);
				if (line == null)
					throw new VHException("short read from socket");
				send("\r\n");

				line = line.trim().toLowerCase();

				if (line.equals("yes") || line.equals("y"))
					return true;
				if (line.equals("no") || line.equals("n"))
					return false;
			}
		}

		public void run() {
			ThreadLunReaper tlr = ThreadLunReaper.getInstance();

			try {
				Users users = null;
				boolean validUser = true, verifyLDAP = false;

				echo(false);
				send(BlackHole.version + "\r\n");
				send("\r\n");

				if (config.getLDAPUrl() != null && config.getLDAPBaseDN() != null)
					verifyLDAP = true;
				else if (config.getLocalUserFile() != null)
					users = Users.getInstance(config.getLocalUserFile());

				String username = null, password = null;
				if (verifyLDAP || users != null) {
					send("\r\nUsername: ");
					username = readLine(true, 16);
					send("\r\nPassword: ");
					password = readLine(false, 16);
				}

				if (verifyLDAP)
					validUser = LDAP.authenticateUser(config.getLDAPBaseDN(), username, password, config.getLDAPUrl());
				else if (users != null)
					validUser = users.authenticateUser(username, password);

				if (verifyLDAP || users != null) {
					if (validUser)
						send("\r\nAccess granted, welcome!\r\n\r\n");
					else
						send("\r\nAccess denied.\r\n");
				}

				for(;validUser;) {
					send("BH> ");
					String line = readLine(true, 255);
					if (line == null) {
						Log.log(LogLevel.LOG_INFO, "Socket closed");
						break;
					}
					send("\r\n");

					line = line.trim();
					if (line.equals(""))
						continue;

					String [] parts = line.split(" ");
					if (parts.length == 0)
						continue;

					String allPars = null;
					int space = line.indexOf(" ");
					if (space != -1)
						allPars = line.substring(space).trim();

					if (parts[0].equalsIgnoreCase("snapshot")) {
						if (parts.length != 3)
							send("Parameter missing [lun filename]\r\n");
						else {
							sm.startSnapshot(Integer.valueOf(parts[1]), parts[2], null);

							send("Snapshot queued for lun " + parts[1] + "\r\n");
						}
					}
					else if (parts[0].equalsIgnoreCase("encsnapshot")) {
						if (parts.length != 5)
							send("Parameter missing [lun filename password cipher]\r\n");
						else {
							sm.startSnapshot(Integer.valueOf(parts[1]), parts[2], new EncryptionParameters(parts[3], parts[4]));

							send("Encrypted snapshot queued for lun " + parts[1] + "\r\n");
						}
					}
					else if (parts[0].equalsIgnoreCase("help")) {
						help(verifyLDAP);
					}
					else if (parts[0].equalsIgnoreCase("flush")) {
						sp.flushAll();
						mm.flushMirrors();
					}
					else if (parts[0].equalsIgnoreCase("quit") || parts[0].equalsIgnoreCase("exit")) {
						sp.flushAll();
						send("Bye bye\r\n");
						break;
					}
					else if (parts[0].equalsIgnoreCase("zoneadd")) {
						if (parts.length < 4)
							send("Incorrect number of parameters\r\n");
						else {
							int lun = Integer.valueOf(parts[1]);
							String pars = "";
							int index = 2;
							while(index < parts.length)
								pars += " " + parts[index++];
							zm.addZone(lun, pars.trim());
						}
					}
					else if (parts[0].equalsIgnoreCase("zonedel")) {
						if (parts.length != 3)
							send("Incorrect number of parameters [lun id]\r\n");
						else {
							int lun = Integer.valueOf(parts[1]);
							int id = Integer.valueOf(parts[2]);
							if (zm.delZone(lun, id))
								send("Zone deleted\r\n");
							else
								send("Zone not found!\r\n");
						}
					}
					else if (parts[0].equalsIgnoreCase("zonelist")) {
						if (parts.length != 2)
							send("Incorrect number of parameters [lun]\r\n");
						else {
							int lun = Integer.valueOf(parts[1]);
							List<String> zoneList = zm.getZoneList(lun);
							for(String current : zoneList)
								send(current + "\r\n");
						}
					}
					else if (parts[0].equalsIgnoreCase("stats")) {
						send("Statistics:\r\n");
						List<String> s = Stats.getInstance().dump();
						for(String cur : s)
							send(" " + cur + "\r\n");
						send("---\r\n");
					}
					else if (parts[0].equalsIgnoreCase("df")) {
						File f = new File(config.getDatastorePath());
						send("Space on " + config.getDatastorePath() + ": " + ((f.getUsableSpace() + GB - 1) / GB) + "GB\r\n");
					}
					else if (parts[0].equalsIgnoreCase("sysload")) {
						OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
						send("CPU-usage: " + operatingSystemMXBean.getSystemLoadAverage() + "\r\n");
					}
					else if (parts[0].equalsIgnoreCase("version")) {
						send(BlackHole.version + "\r\n");
					}
					else if (parts[0].equalsIgnoreCase("uptime")) {
						long running = System.currentTimeMillis() - BlackHole.runningSince;
						send("Running for " + (running / 1000) + " seconds\r\n");
					}
					else if (parts[0].equalsIgnoreCase("scount")) {
						int nRunningSnapshots = sm.getRunningSnapshots().size();
						send("Number of running snapshots: " + nRunningSnapshots + "\r\n");
					}
					else if (parts[0].equalsIgnoreCase("listds")) {
						send("Datastore size: " + ((storage.getSize() + GB - 1)/ GB) + "GB (" + storage.getSize() + " bytes)\r\n");
						send("GB free: " + (lm.getFree() / GB)+ "GB\r\n");
					}
					else if (parts[0].equalsIgnoreCase("growds")) {
						long newSize = Long.valueOf(parts[1]) * GB;
						config.setFileSize(newSize);
						storage.growDatastore(newSize);
						lm.growDatastore(newSize);
						config.saveConfig(config.getConfigFilename(), storage, mm);
						send("Datastore new size: " + parts[1] + "GB\r\n");
					}
					else if (parts[0].equalsIgnoreCase("slist")) {
						send("Running snapshots:\r\n");
						for(Snapshot current : sm.getRunningSnapshots())
							send(" " + current.getFilename() + ": " + (current.getBytesLeft() / (1024 * 1024)) + "MB left\r\n");
					}
					else if (parts[0].equalsIgnoreCase("lunset")) {
						if (parts.length != 4)
							send("Incorrect number of parameters [lun command parameter value]\r\n");
						else {
							int lun = Integer.valueOf(parts[1]);
							String cmd = parts[2];
							String val = parts[3];

							if (cmd.equalsIgnoreCase("adapter"))
								lm.changeListenAdapter(lun, val);
							else if (cmd.equalsIgnoreCase("port"))
								lm.changeListenPort(lun, Integer.valueOf(val));
							else
								send("Parameter " + cmd + " not known\r\n");

							send("You need to stop and (re-)start the lun to make the changes effective\r\n");
						}
					}
					else if (parts[0].equalsIgnoreCase("listluns")) {
						send("Configured LUNs:\r\n");
						for(String current : lm.getServices())
							send(" " + current + "\r\n");
						send("Running LUNs:\r\n");
						List<ThreadLun> curList = lm.getThreadList();
						for(ThreadLun current : curList) {
							int lunNr = current.getLun();
							long lunSize = lm.getLunSize(lunNr);
							send(" " + current.getLun() + ": " + current.getName() + " " + current.getProtocolListener().getNetworkAddress() + " " + current.getProtocolListener().getProtocol() + " " + (lunSize / GB) + "GB (" + lunSize + ")\r\n");
						}
						send("Connected clients:\r\n");
						for(ThreadLun current : tlr.getList()) {
							if (current.getEndPoint() != null)
								send(" " + current.getLun() + ": " + current.getName() + " " + current.getEndPoint() + "\r\n");
						}

					}
					else if (parts[0].equalsIgnoreCase("growlun")) {
						if (parts.length != 3)
							send("Incorrect number of parameters [lun newSize]\r\n");
						else {
							int lun = Integer.valueOf(parts[1]);
							long newSize = Long.valueOf(parts[2]) * GB;
							long oldSize = lm.getLunSize(lun);
							boolean doIt = true;
							if (oldSize >= newSize) {
								send("Can only grow a lun\r\n");
							}
							else {
								lm.getSectorMapper().growLun(lun, newSize);
								send("Lun " + lun + " grown to " + parts[2] + "GB\r\n");
							}
						}
					}
					else if (parts[0].equalsIgnoreCase("addimdisklun")) {
						if (parts.length != 5)
							send("Incorrect number of parameters [adapter port size name]\r\n");
						else {
							long size = Long.valueOf(parts[3]) * GB;
							if (lm.verifySpaceAvailable(size)) {

								int nr = lm.addLun(ProtocolType.PROTOCOL_IMDISK, new String [] { parts[1], parts[2], "false" }, size, parts[4]);
								if (nr != -1)
									send("Created new lun with number: " + nr + "\r\n");
							}
							else {
								send("Not enough space in datastore for new lun " + parts[4] + "\r\n");
							}
						}
					}
					else if (parts[0].equalsIgnoreCase("addnbdlun")) {
						if (parts.length != 6)
							send("Incorrect number of parameters [adapter port size name barriers]\r\n");
						else {
							long size = Long.valueOf(parts[3]) * GB;
							if (lm.verifySpaceAvailable(size)) {

								int nr = lm.addLun(ProtocolType.PROTOCOL_NBD, new String [] { parts[1], parts[2], parts[5] }, size, parts[4]);
								if (nr != -1)
									send("Created new lun with number: " + nr + "\r\n");
							}
							else {
								send("Not enough space in datastore for new lun " + parts[4] + "\r\n");
							}
						}
					}
					else if (parts[0].equalsIgnoreCase("adduser") && !verifyLDAP) {
						if (parts.length != 2)
							send("Incorrect number of parameters [username]\r\n");
						else {
							send("\r\nEnter password for new user: ");
							String password1 = readLine(false, 16);
							send("\r\nVerify password for new user: ");
							String password2 = readLine(false, 16);
							send("\r\n");

							if (password1.equals(password2) == false)
								send("Passwords do not match\r\n");
							else if (users.addUser(parts[1], password1) == false)
								send("Failed to add user\r\n");
							else
								send("User added");
						}
					}
					else if (parts[0].equalsIgnoreCase("passwd") && !verifyLDAP) {
						send("\r\nEnter old password: ");
						String passwordo = readLine(false, 16);

						send("\r\nEnter new password: ");
						String password1 = readLine(false, 16);
						send("\r\nVerify new password: ");
						String password2 = readLine(false, 16);
						send("\r\n");

						if (password1.equals(password2) == false)
							send("New passwords do not match\r\n");
						else if (users.authenticateUser(username, passwordo) == false)
							send("Old password incorrect\r\n");
						else if (users.changePassword(username, passwordo, password1) == false)
							send("Failed to change password\r\n");
						else
							send("Password changed");
					}
					else if (parts[0].equalsIgnoreCase("listusers") && !verifyLDAP) {
						List<String> uList = users.listUsers();
						send("Users:");
						for(String current : uList)
							send(" " + current);
						send("\r\n");
					}
					else if (parts[0].equalsIgnoreCase("deluser") && !verifyLDAP) {
						if (parts.length != 2)
							send("Incorrect number of parameters [username]\r\n");
						else {
							if (askYesNo("Are you sure?")) {
								if (users.delUser(parts[1]) == false)
									send("Failed deleting user " + parts[1] + "\r\n");
								else
									send("User " + parts[1] + " deleted\r\n");
							}
						}
					}
					else if (parts[0].equalsIgnoreCase("killclients")) {
						if (parts.length != 2)
							send("Incorrect number of parameters [lun]\r\n");
						else {
							if (askYesNo("Are you sure?")) {
								tlr.killLun(Integer.valueOf(parts[1]));
							}
						}
					}
					else if (parts[0].equalsIgnoreCase("dellun")) {
						if (parts.length != 2)
							send("Incorrect number of parameters [lun]\r\n");
						else {
							if (askYesNo("Are you sure?")) {
								boolean purge = askYesNo("Purge old data? (takes a very long time)");
								int nr = Integer.valueOf(parts[1]);
								lm.deleteLun(nr, purge);
								send("Deleted lun " + nr + "\r\n");
							}
						}
					}
					else if (parts[0].equalsIgnoreCase("stoplun")) {
						if (parts.length != 2)
							send("Incorrect number of parameters [lun]\r\n");
						else {
							if (askYesNo("Are you sure?")) {
								int nr = Integer.valueOf(parts[1]);
								if (lm.stopLun(nr))
									send("Lun " + nr + " stopped\r\n");
								else
									send("Lun " + nr + " not known or not running\r\n");
							}
						}
					}
					else if (parts[0].equalsIgnoreCase("startlun")) {
						if (parts.length != 2)
							send("Incorrect number of parameters [lun]\r\n");
						else {
							if (askYesNo("Are you sure?")) {
								int nr = Integer.valueOf(parts[1]);
								lm.startLun(nr);
							}
						}
					}
					else if (parts[0].equalsIgnoreCase("copylun")) {
						if (parts.length != 7)
							send("Incorrect number of parameters [lun | blocking | protocol | adapter | port | name]\r\n");
						else {
							int nr = Integer.valueOf(parts[1]);
							if (parts[3].equalsIgnoreCase("nbd") == false) {
								send("Currently only NBD is supported");
								continue;
							}
							if (!lm.verifySpaceAvailable(lm.getLunSize(nr))) {
								send("Not enough space in datastore");
								continue;
							}
							lm.copyLun(nr, parts[2].equalsIgnoreCase("yes"), ProtocolType.PROTOCOL_NBD, new String [] { parts[4], parts[5] }, parts[6]);
						}
					}
					else if (parts[0].equalsIgnoreCase("listmirrors")) {
						send("Mirrors:\r\n");
						int n = 0;
						for(Mirror mirror : mm.getMirrors()) {
							long beSize = lm.getLunSize(mirror.getLun());
							long bytesLeft = beSize - mirror.getResyncOffset();
							double perc = (mirror.getResyncOffset() * 100.0) / beSize;
							send(" " + mirror.toString() + " " + (mirror.isSyncing() ? "syncing (" + perc + "%)" : "") + ", pending blocks: " + mirror.getPendingBlockCount() + "\r\n");
							n++;
						}
						send("--- " + n + " mirror(s)\r\n");
					}
					else if (parts[0].equalsIgnoreCase("addmirror")) {
						if (parts.length < 6)
							send("addmirror requires 5 parameters [lun sync/async nbd/iscsi host port block [iqn] [iqn]]\r\n");
						else {
							mm.addMirror(Config.parseMirrorString(allPars));
							send("New mirror added\r\n");
							c.saveConfig(null, sp, mm);
							send("Configuration file updated\r\n");
						}
					}
					else if (parts[0].equalsIgnoreCase("resyncmirror")) {
						if (parts.length != 2)
							send("resyncmirror requires 1 parameter: the name of a mirror. use listmirrors to get a list\r\n");
						else {
							for(Mirror mirror : mm.getMirrors()) {
								if (mirror.toString().equals(parts[1])) {
									send("Starting resync of mirror " + mirror.toString() + "\r\n");
									mirror.resyncAllData();
									break;
								}
							}
						}
					}
					else if (parts[0].equalsIgnoreCase("terminate")) {
						if (askYesNo("Are you sure?")) {
							send("Terminating BlackHole!\r\n");
							int nRunningSnapshots = sm.getRunningSnapshots().size();
							if (nRunningSnapshots > 0)
								send("" + nRunningSnapshots + " running snapshots were terminated and are incomplete!\r\n");

							BlackHole.doExit.set(true);
							break;
						}
					}
					else {
						send("Command '" + line + "' not understood\r\n");
					}
				}
			}
			catch(SocketException se) { Log.log(LogLevel.LOG_WARN, "TS run() socket exception " + se); }
			catch(Exception e) { // OK
				Log.showException(e);

				try { send("Closing connection because of exception: " + e); } catch(IOException ioe) { } 
			}
			finally {
				try { cs.close(); } catch(IOException ioe) { } 
			}
		}
	}

	public TelnetServer(String adapter, int port, Storage s, Config c, LunManagement lm, MirrorManager mm, SnapshotManager sm, ZoneManager zm) {
		this.adapter = adapter;
		this.port = port;
		this.storage = s;
		this.config = c;
		this.lm = lm;
		this.mm = mm;
		this.sm = sm;
		this.zm = zm;
	}

	public void run() {
		ServerSocket ss = null;

		try {
			ss = new ServerSocket();
			ss.bind(new InetSocketAddress(adapter, port));
			ss.setSoTimeout(500);
		}
		catch(IOException ioe) {
			System.err.println("Failed starting telnet server socket");
			return;
		}

		Log.log(LogLevel.LOG_INFO, "Telnet listening on " + adapter + ":" + port);

		for(;;) {
			List<Thread> clients = new ArrayList<Thread>();
			try {
				Socket client = ss.accept();
				Log.log(LogLevel.LOG_INFO, "Telnet connection with " + client.getInetAddress().toString());

				Thread clientThread = new Thread(new TelnetServerClient(client, storage, config, lm, mm, sm, zm), "telnet client: " + client.toString());
				clientThread.start();
				clients.add(clientThread);

				for(int index=0; index<clients.size();) {
					if (clients.get(index).isAlive() == false)
						clients.remove(index);
					else
						index++;
				}
			}
			catch(SocketTimeoutException ste) {
				if (Thread.interrupted()) {
					Log.log(LogLevel.LOG_INFO, "TelnetServer thread interrupted");
					break;
				}
			}
			catch(IOException ioe) {
				Log.log(LogLevel.LOG_ERR, "TelnetServer exception: " + ioe);
			}
		}
	}
}
