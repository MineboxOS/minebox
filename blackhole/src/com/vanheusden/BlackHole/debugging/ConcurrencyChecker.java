/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.debugging;

import java.util.*;

public class ConcurrencyChecker implements Runnable {
	static String version = "$Id: ConcurrencyChecker.java 606 2013-07-06 22:07:22Z folkert $";
	public void run() {
		Map<String, StackTraceElement[]> knownConflicts = new TreeMap<String, StackTraceElement[]>();
		Random r = new Random(System.currentTimeMillis());

		for(;;) {
			// will contain a list of all 'currently running methods in a thread"
			Map<String, Thread> list = new TreeMap<String, Thread>();

			Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
			Iterator it = map.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry pairs = (Map.Entry)it.next();
				Thread currentThread = (Thread)pairs.getKey();

				StackTraceElement[] trace = (StackTraceElement[])pairs.getValue();
				for(int depth=0; depth<trace.length; depth++) {
					// also an other thread running this method?
					String sOut = trace[depth].getClassName() + ":" + trace[depth].getMethodName();
					if (list.containsKey(sOut)) {
						if (currentThread != list.get(sOut)) {
							// already mentioned?
							if (!knownConflicts.containsKey(sOut)) {
								knownConflicts.put(sOut, trace);
								System.err.println("Concurrency check: " + currentThread.getName() + " / " + list.get(sOut).getName() + ": " + sOut);
								System.err.println(" trace for: " + ((Thread)pairs.getKey()).getName());
								for(StackTraceElement traceElem : trace) {
									System.err.println("  " + traceElem.toString());
								}
								Thread other = list.get(sOut);
								System.err.println(" trace for: " + other.getName());
								StackTraceElement[] otherTrace = map.get(other);
								for(StackTraceElement traceElem : otherTrace) {
									System.err.println("  " + traceElem.toString());
								}
								System.err.println("---");
							}

							break;
						}
					}
					else {
						list.put(sOut, (Thread)pairs.getKey());
					}
				}
			}

			try { Thread.sleep(1 + r.nextInt(500), r.nextInt(1000000)); } catch(InterruptedException ie) { break; }
		}
	}
}
