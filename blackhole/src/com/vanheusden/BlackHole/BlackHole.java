/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole;

import com.vanheusden.BlackHole.cache.*;
import com.vanheusden.BlackHole.config.*;
import com.vanheusden.BlackHole.debugging.*;
import com.vanheusden.BlackHole.iscsi.*;
import com.vanheusden.BlackHole.mirrors.*;
import com.vanheusden.BlackHole.protocol.*;
import com.vanheusden.BlackHole.snapshots.*;
import com.vanheusden.BlackHole.sockets.*;
import com.vanheusden.BlackHole.storage.*;
import com.vanheusden.BlackHole.zoning.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class BlackHole {
	public final static String version = "BlackHole v2.5, (C) 2010-2013 by folkert@vanheusden.com";
	final static String uncleanCheckName = "unclean.dat";
	MyClientSocket mcs;
	Storage storage;
	public static long runningSince;
	public final static AtomicBoolean doExit = new AtomicBoolean();
	public static boolean testcaseMode = false;

	public static boolean isTestcaseMode() {
		return testcaseMode;
	}

	BlackHole(MyClientSocket mcs, Storage storage) {
		this.mcs = mcs;
		this.storage = storage;
	}

	public static void iscsiScanTargets(String host, int port, String myIQN) throws IOException {
		try {
			Socket s = new Socket(host, port);
			InputStream inputStream = s.getInputStream();
			OutputStream outputStream = s.getOutputStream();

			System.out.println("Scanning iSCSI target " + host + " on port " + port + ":");

			// login
			Log.log(LogLevel.LOG_INFO, "login, step 1");
			PDU pduOut = new PDU_0x03__login__request(0, null, 1, myIQN);
			outputStream.write(pduOut.getAsByteArray());
			PDU pduIn = PDU.recvPdu(inputStream);
			if (pduIn.getByte(36) != 0) {
				System.err.println("Target indicates error during login");
				return;
			}

			if ((pduIn.getByte(1) & 128) == 0) {
				// login, part 2
				Log.log(LogLevel.LOG_INFO, "login, step 2");
				pduOut = new PDU_0x03__login__request(1, null, 3, myIQN);
				outputStream.write(pduOut.getAsByteArray());
				pduIn = PDU.recvPdu(inputStream);
				if (pduIn.getByte(36) != 0) System.out.println("FAILURE");
			}

			// get a list of targets
			pduOut = new PDU_0x04__text__request(1, pduIn.getStatSN() + 1, new String [] { "SendTargets=All" });
			outputStream.write(pduOut.getAsByteArray());
			pduIn = PDU.recvPdu(inputStream);
			// display
			byte [] tempBuffer = Arrays.copyOf(pduIn.getData(), pduIn.getData().length);
			for(int index=0; index<pduIn.getData().length; index++) {
				if (tempBuffer[index] == 0x00)
					tempBuffer[index] = '\n';
			}

			String targets = new String(tempBuffer);
			System.out.println("Targets:");
			System.out.println(targets);

			// FIXME: logout
		}
		catch(UnknownHostException uhe) {
			System.err.println("Host " + host + " is now known");
		}
	}

	public static void help() {
		System.out.println("--config x    Configuration file to read from");
		System.out.println("--isci-scan-targets host port iqn      Ask an iSCSI target for the LUNs it has");
		System.out.println("--fsck        storage consistency test");
		System.out.println("--add-nbd-lun adapter port size name   Add a new lun. Name must not contain spaces");
		System.out.println("--help        this help");
	}

	public static void main(String [] args) throws Exception {
		boolean err = false;
		Thread.currentThread().setName("main");

		Storage storage = null;
		SnapshotManager snapMan = null;
		MirrorManager mirrorMan = null;
		final Config config = new Config();
		Thread telnetThread = null;
		Thread webServerThread = null;
		Thread lmThread = null;
		LunManagement lm = null;

		runningSince = System.currentTimeMillis();

		Log.log(LogLevel.LOG_INFO, version);

		Thread cc = null, stdt = null;
		StackTraceDotter std = null;

		String uncleanPath = null;
		File uncleanFile = null;
		boolean uncleanStart = false;

		List<String []> nbdLunsToAdd = new ArrayList<String []>();
		try {
			boolean oneShot = false;
			boolean checkDatastore = false;

			if (args.length == 0)
				System.out.println("Invoke program with '--help' to see a list of commandline switches.");

			for(int index=0; index<args.length; index++) {
				if (args[index].equals("--config")) {
					config.loadConfig(args[++index]);
				}
				else if (args[index].equals("--add-nbd-lun")) {
					String [] dummy = new String[4];
					dummy[0] = args[++index];
					dummy[1] = args[++index];
					dummy[2] = args[++index];
					dummy[3] = args[++index];
					nbdLunsToAdd.add(dummy);
				}
				else if (args[index].equals("--isci-scan-targets")) {
					String host = args[++index];
					int port = Integer.valueOf(args[++index]);
					String iqn = args[++index];
					iscsiScanTargets(host, port, iqn);
					System.exit(0);
				}
				else if (args[index].equals("--fsck"))
					checkDatastore = true;
				else if (args[index].equals("--testcase-mode"))
					testcaseMode = true;
				else if (args[index].equals("--help"))
				{
					help();
					System.exit(0);
				}
				else
				{
					help();
					System.exit(1);
				}
			}

			Log.setDebugLevel(config.getDebugLevel());
			Log.setSyslogHost(config.getSyslogHost());
			Log.setLogfile(config.getLogfile());
			Log.setLogfileDebugLevel(config.getLogfileDebugLevel());

			if (config.getDebugLevel() == LogLevel.LOG_DEBUG) {
				cc = new Thread(new ConcurrencyChecker());
				std = new StackTraceDotter();
				stdt = new Thread(std);
				cc.start();
				stdt.start();
			}

			uncleanPath = config.getDatastorePath() + File.separator + uncleanCheckName;
			uncleanFile = new File(uncleanPath);

			if (uncleanFile.exists()) {
				checkDatastore = true;
				uncleanStart = true;
				uncleanFile.delete();
				Log.log(LogLevel.LOG_WARN, "*** UNCLEAN SHUTDOWN, CHECK FORCED ***");
			}
			uncleanFile.createNewFile();

			Log.log(LogLevel.LOG_INFO, "Storage size: " + (config.getFileSize() / (1024*1024)) + "MB, block size: " + config.getBlockSize());

			if (config.getStorageType() == StorageType.STORAGE_FILES) {
				if (config.getCompressionParameters() != null)
					throw new VHException("One cannot use compression for 'files' storage backend");
				storage = (Storage)new StorageFiles(config.getFileSize(), config.getBlockSize(), config.getDatastorePath(), config.getHashType(), config.getAllReadcacheParameters(), config.getAllWritecacheParameters(), config.getEncryptionParameters(), checkDatastore);
			}
			else if (config.getStorageType() == StorageType.STORAGE_SQL)
				storage = (Storage)new StorageMySQL(config.getFileSize(), config.getBlockSize(), config.getDatastorePath(), config.getSQLUrl(), config.getSQLUser(), config.getSQLPasword(), config.getHashType(), config.getAllWritecacheParameters(), config.getAllReadcacheParameters(), config.getEncryptionParameters(), checkDatastore, config.getCompressionParameters());
			else if (config.getStorageType() == StorageType.STORAGE_KC)
				storage = (Storage)new StorageKC(config.getFileSize(), config.getBlockSize(), config.getDatastorePath(), config.getHashType(), config.getAllReadcacheParameters(), config.getAllWritecacheParameters(), config.getEncryptionParameters(), checkDatastore, config.getCompressionParameters());
			else if (config.getStorageType() == StorageType.STORAGE_MONGODB)
				storage = (Storage)new StorageMongoDB(config.getMongoHost(), config.getMongoDB(), config.getFileSize(), config.getBlockSize(), config.getDatastorePath(), config.getHashType(), config.getAllReadcacheParameters(), config.getAllWritecacheParameters(), config.getEncryptionParameters(), checkDatastore, config.getCompressionParameters());

			Log.log(LogLevel.LOG_INFO, "Read cache: each element takes up " + ReadCache.getTotalElementSize() + " bytes");

			if (checkDatastore) {
				boolean fail = false;
				if (!storage.fsck()) {
					Log.log(LogLevel.LOG_EMERG, "Storage corrupt");
					fail = true;
				}
				else {
					Log.log(LogLevel.LOG_EMERG, "Storage structures seem to be all fine");
					System.out.println("Storage structures seem to be all fine");
					if (uncleanStart)
						Log.log(LogLevel.LOG_INFO, "It is safe to restart the application now that the datastore is verified");
				}
				storage.closeStorageBackend();
				uncleanFile.delete();
				System.exit(fail ? 1 : 0);
			}

			if (config.getUnsafe())
				storage.setUnsafe(true);

			final CopyOnWriteArrayList<Thread> threadList = new CopyOnWriteArrayList<Thread>();

			/* restart old luns */
			SectorMapper sm;
			if (new File(config.getDatastorePath() + "/sectormap.dat").exists())
				sm = new SectorMapper(config.getFileSize(), config.getDatastorePath() + "/sectormap.dat");
			else
				sm = new SectorMapper(config.getFileSize());

			mirrorMan = new MirrorManager(sm, storage, config.getDatastorePath(), config.getBlockSize());

			for(MirrorParameters current : config.getMirrors()) {
				mirrorMan.addMirror(current);
			}

			snapMan = new SnapshotManager(config.getSnapshotDir(), storage, sm, config.getBlockSize());

			ZoneManager zm = new ZoneManager(config.getDatastorePath() + "/zoning.dat");

			lm = new LunManagement(config.getDatastorePath(), storage, mirrorMan, snapMan, sm, config.getDatastorePath() + "/sectormap.dat", zm);

			/* create and start new luns */
			for(String [] current : nbdLunsToAdd) {
				long size = Long.valueOf(current[2]);
				if (!lm.verifySpaceAvailable(size)) {
					System.err.println("not enough space in datastore for new lun " + current[3]);
					System.exit(1);
				}
				if (lm.addLun(ProtocolType.PROTOCOL_NBD, new String [] { current[0], current[1] }, size, current[3]) == -1) {
					System.err.println("Error creating lun");
					System.exit(1);
				}
			}

			lmThread = new Thread(lm, "Terminated connections cleaner");
			lmThread.start();

			if (config.getTelnetListenPort() != -1) {
				telnetThread = new Thread(new TelnetServer(config.getTelnetListenAdapter(), config.getTelnetListenPort(), storage, config, lm, mirrorMan, snapMan, zm), "telnet listener");
				telnetThread.start();
			}

			if (config.getHttpListenPort() != -1) {
				Log.log(LogLevel.LOG_DEBUG, "Web-server listening on " + config.getHttpListenAdapter() + ":" + config.getHttpListenPort());
				webServerThread = new Thread(new HTTPServer(config.getHttpListenAdapter(), config.getHttpListenPort(), threadList, storage), "HTTP server");
				webServerThread.start();
			}

			Log.log(LogLevel.LOG_INFO, "All threads started");
			for(;doExit.get() == false;)
				Thread.sleep(500);
		}
		catch(AssertionError ae) {
			throw ae;
		}
		catch(Exception esc) { // OK
			Log.showException(esc);
			err = true;
		}
		finally {
			try {
				if (lmThread != null) {
					Log.log(LogLevel.LOG_INFO, "Stopping LUN manager thread");
					Utils.intJoinIgnoreEx(lmThread);
				}

				if (lm != null) {
					Log.log(LogLevel.LOG_INFO, "Closing LUN manager");
					lm.close();
				}

				Log.log(LogLevel.LOG_INFO, "Stopping protocol listeners & instances");
				ThreadLunReaper.getInstance().shutdown();

				if (storage != null) {
					Log.log(LogLevel.LOG_INFO, "Storage: close");
					storage.closeStorageBackend();
				}

				if (mirrorMan != null) {
					Log.log(LogLevel.LOG_INFO, "Closing mirror manager");
					mirrorMan.close();
				}
				if (snapMan != null) {
					Log.log(LogLevel.LOG_INFO, "Closing snapshot manager");
					snapMan.close();
				}

				uncleanFile.delete();

				if (telnetThread != null) {
					Log.log(LogLevel.LOG_INFO, "Waiting for telnet server thread to exit");
					Utils.intJoinIgnoreEx(telnetThread);
					Log.log(LogLevel.LOG_INFO, "Telnet server thread exited");
				}

				if (webServerThread != null) {
					Log.log(LogLevel.LOG_INFO, "Waiting for webserver thread to exit");
					Utils.intJoinIgnoreEx(webServerThread);
					Log.log(LogLevel.LOG_INFO, "Webserver thread exited");
				}

				Log.log(LogLevel.LOG_INFO, "Stop debug threads");
				if (cc != null)
					cc.interrupt();
				if (stdt != null) {
					std.terminate();
					stdt.interrupt();
				}
			}
			catch(Exception e) { // OK
				Log.showException(e);
				err = true;
			}
		}
		Log.log(LogLevel.LOG_INFO, "end of program");

		if (err)
			System.exit(1);
	}
}
