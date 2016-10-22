/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.zoning;

import com.vanheusden.BlackHole.VHException;
import com.vanheusden.BlackHole.Log;
import com.vanheusden.BlackHole.LogLevel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ZoneManager {
	static String version = "$Id: ZoneManager.java 606 2013-07-06 22:07:22Z folkert $";
	String file;
	Map<Integer, List<Zone>> zoning = new TreeMap<Integer, List<Zone>>();
	Semaphore sem = new Semaphore(1, true);
	
	public ZoneManager(String file) throws VHException, IOException {
		this.file = file;

		loadZoning();
	}
	
	public void loadZoning() throws VHException, IOException {
		zoning = new TreeMap<Integer, List<Zone>>();
		
		try {
			BufferedReader fh = new BufferedReader(new FileReader(file));

			int lineNr = 0;
			for(;;) {
				lineNr++;
				String line = fh.readLine();
				if (line == null)
					break;
				line = line.trim();
				if (line.length() == 0)
					continue;
				if (line.substring(0, 1).equals("#"))
					continue;

				String [] elements = line.split("=");
				if (elements.length != 2)
					throw new VHException("Zoning line " + lineNr + ": invalid line");

				addZone(Integer.valueOf(elements[0]), Zone.getInstance(elements[1]));
			}

			fh.close();
		}
		catch(FileNotFoundException fnfe) {
			Log.log(LogLevel.LOG_WARN, "Zoning: " + file + " not found, first run?");
		}
	}
	
	void writeLine(BufferedWriter fh, String what) throws IOException {
		fh.write(what, 0, what.length());
		fh.newLine();
	}

	public void saveZoning() throws VHException, IOException {
		BufferedWriter fh = new BufferedWriter(new FileWriter(file));
		
		Iterator it = zoning.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry pairs = (Map.Entry)it.next();
			Integer lun = (Integer)pairs.getKey();
			List<Zone> zoneSet = zoning.get(lun);
			for(Zone current : zoneSet) {
				writeLine(fh, "" + lun + "=" + current.toString());
			}
		}
		
		fh.close();
	}

	public void addZone(int lun, String descr) throws VHException, IOException {
		addZone(lun, Zone.getInstance(descr));
	}
	
	public void addZone(int lun, Zone zone) throws VHException, IOException {
		sem.acquireUninterruptibly();
		try {
			List<Zone> zoneSet = zoning.get(Integer.valueOf(lun));
			if (zoneSet == null)
				zoneSet = new ArrayList<Zone>();
			zoneSet.add(zone);
			zoning.put(Integer.valueOf(lun), zoneSet);
			saveZoning();
		}
		finally {
			sem.release();
		}
	}

	public boolean delZone(int lun, int hash) throws VHException, IOException {
		sem.acquireUninterruptibly();
		List<Zone> zoneSet = zoning.get(Integer.valueOf(lun));
		
		for(int index=0; index<zoneSet.size(); index++) {
			if (zoneSet.get(index).hashCode() == hash) {
				zoneSet.remove(index);
				try {
					saveZoning();
				}
				finally {
					sem.release();
				}
				return true;
			}
		}
		sem.release();

		return false;
	}

	public List<String> getZoneList(int lun) {
		sem.acquireUninterruptibly();
		List<Zone> zoneSet = zoning.get(Integer.valueOf(lun));
		List<String> out = new ArrayList<String>();

		if (zoneSet != null) {
			for(Zone current : zoneSet)
				out.add("" + current.hashCode() + ": " + current.toString());
		}
		sem.release();

		return out;
	}
	
	public boolean isAllowed(int lun, Socket client) throws UnknownHostException {
		sem.acquireUninterruptibly();
		List<Zone> zoneSet = zoning.get(Integer.valueOf(lun));
		if (zoneSet == null || zoneSet.size() == 0) { // empty/non existing zoneset means allow from all
			sem.release();
			return true;
		}

		try {
			for(Zone current : zoneSet) {
				if (current.isAllowed(client) == false) {
					sem.release();
					return false;
				}
			}
		}
		catch(VHException vhe) {
		}
		catch(UnknownHostException uhe) {
			throw uhe;
		}
		finally {
			sem.release();
		}

		return true;
	}
}
