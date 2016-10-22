/* Released under GPL2, (C) 2009-2011 by folkert@vanheusden.com */
package com.vanheusden.BlackHole.config;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.cache.*;
import com.vanheusden.BlackHole.mirrors.*;
import com.vanheusden.BlackHole.protocol.*;
import com.vanheusden.BlackHole.storage.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Config {
	int blockSize = 4096;
	long fileSize = -1;
	List<MirrorParameters> mirrors = new ArrayList<MirrorParameters>();
	int readCacheElements = 0;
	String dataStorePath = "." + File.separator;
	String snapshotDir = "." + File.separator;
	boolean exitOnClose = true;
	String httpListenAdapter = "0.0.0.0";
	int httpListenPort = -1;
	String telnetListenAdapter = "0.0.0.0";
	int telnetListenPort = -1;
	LogLevel debugLevel = LogLevel.LOG_DEBUG;
	LogLevel logDebugLevel = LogLevel.LOG_DEBUG;
	HashType hashType = HashType.MD5;
	boolean cmd = false;
	boolean mmDBlock = true;
	String configFileName = "bh.conf";
	boolean unsafe = false;
	boolean delayedWrite = false;
	String syslogHost = null;
	String logfile = null;
	StorageType storageType = StorageType.STORAGE_FILES;
	String sqlUrl = null, sqlUser = null, sqlPassword = null;
	String encryptionPassword = null, encryptionCipher = "Blowfish/CFB8/NoPadding";
	CacheClearType cct = CacheClearType.LRU;
	int cffn = 16;
	int forceFlushThreshold = -1;
	int triggerFlushThreshold = -1;
	boolean sortWriteBackListBeforeWrite = false;
	int delayedWriteFlushInterval = 5000;
	int minFlushN = 16;
	boolean readCacheDirtyBlocks;
	CompressionParameters compressionParameters = null;
	String ldapBaseDn = null, ldapUrl = null;
	String localUserFile = null;
	String mongoHost = null, mongoDB = null;

	public Config() {
	}

	public Config(String fileName) throws VHException, IOException {
		configFileName = fileName;
		loadConfig(fileName);
	}

	public String getConfigFilename() {
		return configFileName;
	}

	static MirrorParameters parseMirrorString(String data) throws VHException {
		String [] parts = data.split(" ");
		if (parts.length != 6 && parts.length != 8)
			throw new VHException("Mirror line not 6 (or 8 for iSCSI) parameters");

		MirrorType mt;
		ProtocolType pt;
		String host;
		int port;
		int lun;
		boolean disconnectBlock;
		String par1 = null;
		String par2 = null;

		lun = Integer.valueOf(parts[0]);

		if (parts[1].equalsIgnoreCase("sync"))
			mt = MirrorType.METRO_MIRROR;
		else if (parts[1].equalsIgnoreCase("async"))
			mt = MirrorType.GLOBAL_MIRROR;
		else
			throw new VHException("Mirror type can be either sync or async, not " + parts[1]);

		if (parts[2].equalsIgnoreCase("NBD"))
			pt = ProtocolType.PROTOCOL_NBD;
		else if (parts[2].equalsIgnoreCase("ISCSI"))
			pt = ProtocolType.PROTOCOL_ISCSI;
		else
			throw new VHException("Protocol type can be only iSCSI or NBD currently, not " + parts[2]);

		host = parts[3];

		port = Integer.valueOf(parts[4]);

		disconnectBlock = parts[5].equalsIgnoreCase("true");

		if (pt == ProtocolType.PROTOCOL_ISCSI) {
			par1 = parts[6];
			par2 = parts[7];
		}

		return new MirrorParameters(mt, pt, host, port, disconnectBlock, par1, par2, lun);
	}

	void write(BufferedWriter out, String s) throws IOException {
		out.write(s, 0, s.length());
		out.newLine();
	}

	public void saveConfig(String whereTo, Storage s, MirrorManager mirrorMan) throws IOException {
		if (whereTo == null)
			whereTo = configFileName;

		BufferedWriter out = new BufferedWriter(new FileWriter(whereTo));

		write(out, "block-size=" + blockSize);
		for(MirrorParameters m : mirrorMan.getMirrorPars()) {
			write(out, "mirror=" + m.getMirrorType().toString() + " " + m.getProtocolType().toString() + " " + m.getHost() + " " + m.getPort() + " " + m.getDisconnectBlock());
		}
		write(out, "size=" + fileSize);
		//
		write(out, "cache-elements=" + readCacheElements);
		write(out, "cache-delayed-write=" + delayedWrite);
		write(out, "cache-type=" + cct);
		write(out, "cache-full-flush-n=" + cffn);
		write(out, "cache-dirty-blocks=" + readCacheDirtyBlocks);
		//
		write(out, "dwc-force-flush-threshold=" + forceFlushThreshold);
		write(out, "dwc-flush-trigger-threshold=" + triggerFlushThreshold);
		write(out, "dwc-sort-before-write=" + sortWriteBackListBeforeWrite);
		write(out, "dwc-flush-interval=" + delayedWriteFlushInterval);
		write(out, "dwc-flush-min-n-blocks=" + minFlushN);
		//
		write(out, "path=" + dataStorePath);
		write(out, "snapshotdir=" + snapshotDir);
		write(out, "exit-on-close=" + exitOnClose);
		//
		write(out, "http-listen-adapter=" + httpListenAdapter);
		write(out, "http-listen-port=" + httpListenPort);
		write(out, "ldap-base-dn=" + ldapBaseDn);
		write(out, "ldap-url=" + ldapUrl);
		write(out, "telnet-listen-adapter=" + telnetListenAdapter);
		write(out, "telnet-listen-port=" + telnetListenPort);
		write(out, "local-user-file=" + localUserFile);
		//
		write(out, "debug=" + debugLevel.getName());
		write(out, "hash-type=" + hashType);
		write(out, "unsafe=" + unsafe);
		write(out, "storage-type=" + storageType);
		if (sqlUrl != null) {
			write(out, "sql-url=" + sqlUrl);
			write(out, "sql-user=" + sqlUser);
			write(out, "sql-password=" + sqlPassword);
		}
		if (encryptionPassword != null)
			write(out, "encryption-password=" + encryptionPassword);
		if (encryptionCipher != null)
			write(out, "encryption-cipher=" + encryptionCipher);
		write(out, "compression=" + compressionParameters.toString());

		if (mongoHost != null)
			write(out, "mongoHost=" + mongoHost);
		if (mongoDB != null)
			write(out, "mongoDB=" + mongoDB);

		if (logfile != null)
			write(out, "logfile=" + logfile);
		write(out, "logfile-debug=" + logDebugLevel.getName());
		if (syslogHost != null)
			write(out, "syslog=" + syslogHost);

		out.close();
	}

	public void loadConfig(String fileName) throws VHException, IOException {
		configFileName = fileName;

		BufferedReader in;
		try
		{
			in = new BufferedReader(new FileReader(fileName));

			String line;
			int lineNr = 0;
			try
			{
				while((line = in.readLine()) != null)
				{
					lineNr++;

					if (line.length() == 0 || line.substring(0, 1).equals("#"))
						continue;

					int is = line.indexOf("=");
					if (is == -1)
						throw new VHException("Error on line " + lineNr + ": malformed line.");

					String name = line.substring(0, is).trim();
					String data = line.substring(is + 1).trim();

					boolean isTrue = data.equalsIgnoreCase("true") ? true : false;

					if (name.equals("block-size"))
						blockSize = Integer.valueOf(data);
					else if (name.equals("ldap-base-dn"))
						ldapBaseDn = data;
					else if (name.equals("ldap-url"))
						ldapUrl = data;
					else if (name.equals("local-user-file"))
						localUserFile = data;
					else if (name.equals("mirror"))
						mirrors.add(parseMirrorString(data));
					else if (name.equals("size"))
						fileSize = Long.valueOf(data);
					else if (name.equals("dwc-force-flush-threshold"))
						forceFlushThreshold = Integer.valueOf(data);
					else if (name.equals("dwc-flush-trigger-threshold"))
						triggerFlushThreshold = Integer.valueOf(data);
					else if (name.equals("dwc-sort-before-write"))
						sortWriteBackListBeforeWrite = isTrue;
					else if (name.equals("dwc-flush-interval"))
						delayedWriteFlushInterval = Integer.valueOf(data);
					else if (name.equals("dwc-flush-min-n-blocks"))
						minFlushN = Integer.valueOf(data);
					else if (name.equals("cache-elements"))
						readCacheElements = Integer.valueOf(data);
					else if (name.equals("cache-full-flush-n"))
						cffn = Integer.valueOf(data);
					else if (name.equals("cache-dirty-blocks"))
						readCacheDirtyBlocks = isTrue;
					else if (name.equals("path"))
						dataStorePath = data;
					else if (name.equals("mongo-host"))
						mongoHost = data;
					else if (name.equals("mongo-db"))
						mongoDB = data;
					else if (name.equals("syslog"))
						syslogHost = data;
					else if (name.equals("logfile"))
						logfile = data;
					else if (name.equals("cache-delayed-write"))
						delayedWrite = isTrue;
					else if (name.equals("exit-on-close"))
						exitOnClose = isTrue;
					else if (name.equals("unsafe"))
						unsafe = isTrue;
					else if (name.equals("snapshotdir"))
						snapshotDir = data;
					else if (name.equals("compression"))
						compressionParameters = new CompressionParameters(data);
					else if (name.equals("encryption-password"))
						encryptionPassword = data;
					else if (name.equals("encryption-cipher"))
						encryptionCipher = data;
					else if (name.equals("http-listen-adapter"))
						httpListenAdapter = data;
					else if (name.equals("http-listen-port"))
						httpListenPort = Integer.valueOf(data);
					else if (name.equals("telnet-listen-adapter"))
						telnetListenAdapter = data;
					else if (name.equals("telnet-listen-port"))
						telnetListenPort = Integer.valueOf(data);
					else if (name.equals("sql-url"))
						sqlUrl = data;
					else if (name.equals("sql-user"))
						sqlUser = data;
					else if (name.equals("sql-password"))
						sqlPassword = data;
					else if (name.equals("logfile-debug")) {
						LogLevel l = LogLevel.getLogLevel(data);
						if (l == null)
							logDebugLevel = LogLevel.getLogLevel(Integer.valueOf(data));
						else
							logDebugLevel = l;
					}
					else if (name.equals("debug")) {
						LogLevel l = LogLevel.getLogLevel(data);
						if (l == null)
							debugLevel = LogLevel.getLogLevel(Integer.valueOf(data));
						else
							debugLevel = l;
					}
					else if (name.equals("hash-type")) {
						hashType = HashType.getType(data);
						if (hashType == null) {
							System.err.println("hash-type \"" + data + "\" not known");
							System.exit(1);
						}
					}
					else if (name.equals("cache-type")) {
						cct = CacheClearType.getType(data);
						if (cct == null) {
							System.err.println("cache-type \"" + data + "\" not known");
							System.exit(1);
						}
					}
					else if (name.equals("storage-type")) {
						storageType = StorageType.getType(data);
						if (storageType == null) {
							System.err.println("storage-type \"" + data + "\" not known");
							System.exit(1);
						}
					}
					else
						throw new VHException("Unknown parameter on line " + lineNr);
				}
			}
			catch(ArrayIndexOutOfBoundsException aioobe)
			{
				System.err.println("Please check line " + lineNr + " of configuration-file " + fileName + ": a parameter may be missing");
				System.exit(127);
			}
			catch(NumberFormatException nfeGlobal)
			{
				System.err.println("Please check line " + lineNr + " of configuration-file " + fileName + ": one of the parameters ought to be a number");
				System.exit(127);
			}

			in.close();
		}
		catch(FileNotFoundException e)
		{
			System.err.println("File " + fileName + " not found.");
			System.exit(127);
		}
	}

	public int getBlockSize() {
		return blockSize;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long newSize) {
		fileSize = newSize;
	}

	public int getCacheElements() {
		return readCacheElements;
	}

	public String getDatastorePath() {
		return dataStorePath;
	}

	public boolean getExitOnClose() {
		return exitOnClose;
	}

	public boolean getUnsafe() {
		return unsafe;
	}

	public String getHttpListenAdapter() {
		return httpListenAdapter;
	}

	public int getHttpListenPort() {
		return httpListenPort;
	}

	public String getTelnetListenAdapter() {
		return telnetListenAdapter;
	}

	public int getTelnetListenPort() {
		return telnetListenPort;
	}

	public LogLevel getDebugLevel() {
		return debugLevel;
	}

	public LogLevel getLogfileDebugLevel() {
		return logDebugLevel;
	}

	public HashType getHashType() {
		return hashType;
	}

	public List<MirrorParameters> getMirrors() {
		return mirrors;
	}

	public boolean getCacheDelayedWrite() {
		return delayedWrite;
	}

	public String getSyslogHost() {
		return syslogHost;
	}

	public String getLogfile() {
		return logfile;
	}

	public StorageType getStorageType() {
		return storageType;
	}

	public String getSQLUrl() {
		return sqlUrl;
	}

	public String getSQLUser() {
		return sqlUser;
	}

	public String getSQLPasword() {
		return sqlPassword;
	}

	public String getEncryptionPassword() {
		return encryptionPassword;
	}

	public CacheClearType getCacheClearType() {
		return cct;
	}

	public int getCacheFullFlushN() {
		return cffn;
	}

	public ReadcacheParameters getAllReadcacheParameters() {
		if (readCacheElements == 0)
			return null;

		return new ReadcacheParameters(cct, cffn, readCacheElements);
	}

	public int getForceFlushThreshold() {
		return forceFlushThreshold;
	}

	public int getTriggerFlushThreshhold() {
		return triggerFlushThreshold;
	}

	public boolean getSortWriteBackListBeforeWrite() {
		return sortWriteBackListBeforeWrite;
	}

	public int getDelayedWriteFlushInterval() {
		return delayedWriteFlushInterval;
	}

	public int getMinFlushN() {
		return minFlushN;
	}

	public WritecacheParameters getAllWritecacheParameters() {
		if (delayedWrite == false)
			return null;
		if (triggerFlushThreshold == -1)
			return null;
		if (forceFlushThreshold == -1)
			forceFlushThreshold = (triggerFlushThreshold * 4) / 3;

		return new WritecacheParameters(forceFlushThreshold, triggerFlushThreshold, sortWriteBackListBeforeWrite, delayedWriteFlushInterval, minFlushN, readCacheDirtyBlocks);
	}

	public EncryptionParameters getEncryptionParameters() {
		if (encryptionPassword == null)
			return null;

		return new EncryptionParameters(encryptionPassword, encryptionCipher);
	}

	public CompressionParameters getCompressionParameters() {
		return compressionParameters;
	}

	public String getSnapshotDir() {
		return snapshotDir;
	}

	public String getLDAPBaseDN() {
		return ldapBaseDn;
	}

	public String getLDAPUrl() {
		return ldapUrl;
	}

	public String getLocalUserFile() {
		return localUserFile;
	}

	public String getMongoHost() {
		return mongoHost;
	}

	public String getMongoDB() {
		return mongoDB;
	}
}
