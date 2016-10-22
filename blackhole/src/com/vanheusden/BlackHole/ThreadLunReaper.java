/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole;

import com.vanheusden.BlackHole.Utils;
import com.vanheusden.BlackHole.Log;
import com.vanheusden.BlackHole.LogLevel;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

public class ThreadLunReaper implements Runnable {
	static String version = "$Id: ThreadLunReaper.java 606 2013-07-06 22:07:22Z folkert $";
	static ThreadLunReaper instance;
	CopyOnWriteArrayList<ThreadLun> list = new CopyOnWriteArrayList<ThreadLun>();
	Thread t;
	Semaphore threadLunLock = new Semaphore(1, true);

	protected ThreadLunReaper() {
		t = new Thread(this);
		t.start();
	}

	public static ThreadLunReaper getInstance() {
		if (instance != null)
			return instance;

		instance = new ThreadLunReaper();

		return instance;
	}

	public CopyOnWriteArrayList<ThreadLun> getList() {
		return list;
	}

	public void killLun(int lunNr) {
		threadLunLock.acquireUninterruptibly();
		int index = 0;
		while(index < list.size()) {
			if (list.get(index).getLun() == lunNr && list.get(index).getEndPoint() != null) {
				Utils.intJoinIgnoreEx(list.get(index).getThread());
				list.remove(index);
			}
			else {
				index++;
			}
		}
		threadLunLock.release();
	}

	public void add(ThreadLun cur) {
		threadLunLock.acquireUninterruptibly();
		list.add(cur);
		threadLunLock.release();
	}

	public void shutdown() {
		Utils.intJoinIgnoreEx(t);

		Log.log(LogLevel.LOG_INFO, "ThreadLunReaper: shutdown");
		threadLunLock.acquireUninterruptibly();
		for(ThreadLun cur : list) {
			Log.log(LogLevel.LOG_INFO, " stopping: " + cur.getLun() + " " + cur.getName() + " / " + cur.getEndPoint());
			try {
				if (cur.getEndPoint() == null)
					cur.getProtocolListener().stop();
				else
					cur.getHandler().close();
			}
			catch(IOException ioe) {
				Log.showException(ioe);
			}
			Utils.intJoinIgnoreEx(cur.getThread());
		}
		threadLunLock.release();
	}

	public void run() {
		Log.log(LogLevel.LOG_INFO, "Thread reaper started");
		try {
			for(;;) {
				Thread.sleep(2500);

				threadLunLock.acquireUninterruptibly();
				int index = 0;
				while(index < list.size()) {
					Thread currentThread = list.get(index).getThread();
					if (currentThread.isAlive() == false) {
						Log.log(LogLevel.LOG_DEBUG, "Thread " + list.get(index).getName() + " forgotten");
						list.remove(index);
					}
					else {
						index++;
					}
				}
				threadLunLock.release();
			}
		}
		catch(InterruptedException ie) {
			Log.log(LogLevel.LOG_DEBUG, "Asked to stop");
		}
		Log.log(LogLevel.LOG_INFO, "Thread reaper stopped");
	}
}
