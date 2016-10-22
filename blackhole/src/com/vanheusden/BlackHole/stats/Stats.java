/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.stats;

import  com.vanheusden.BlackHole.Log;
import  com.vanheusden.BlackHole.LogLevel;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class Stats {
	static String version = "$Id: Stats.java 606 2013-07-06 22:07:22Z folkert $";
	static Stats instance = null;
	CopyOnWriteArrayList<StatsElement> list = new CopyOnWriteArrayList<StatsElement>();

	public synchronized static Stats getInstance() {
		if (instance == null)
			instance = new Stats();

		return instance;
	}

	protected Stats() {
	}

	public void add(StatsElement se) {
		list.add(se);
	}

	public String [] getList() {
		String [] out = new String[list.size()];

		for(int index=0; index<list.size(); index++)
			out[index] = list.get(index).toString();

		return out;
	}

	public List<String> dump() {
		List<String> out = new ArrayList<String>();

		for(StatsElement se : list)
			out.add(se.toString());

		return out;
	}

	public void close() {
		for(StatsElement se : list)
			Log.log(LogLevel.LOG_INFO, se.toString());
	}
}
