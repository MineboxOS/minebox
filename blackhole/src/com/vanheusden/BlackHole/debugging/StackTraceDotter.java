/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.debugging;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class StackTraceDotter implements Runnable {
	static String version = "$Id: StackTraceDotter.java 606 2013-07-06 22:07:22Z folkert $";
	private AtomicBoolean exit = new AtomicBoolean();
	
	public StackTraceDotter() {
	}
	
	public void terminate() {
		exit.set(true);
	}
	
	void writeLine(BufferedWriter handle, String what) throws IOException {
		handle.write(what, 0, what.length());
		handle.newLine();
	}
	
	String stToString(StackTraceElement ste) {
		return ste.getMethodName() + ":" + ste.getLineNumber() + " (" + ste.getClassName() + ")";
	}
	
	public void run() {
		Random r = new Random(System.currentTimeMillis());
		Map<String, Map<Integer, StackTraceElement[]>> history = new TreeMap<String, Map<Integer, StackTraceElement[]>>();
		
		for(;exit.get() == false;) {
			Map<Thread, StackTraceElement[]> currentStackTraces = Thread.getAllStackTraces();
			
			Iterator it = currentStackTraces.entrySet().iterator();
			while(it.hasNext()) {
				Map.Entry pairs = (Map.Entry)it.next();
				String currentThread = ((Thread)pairs.getKey()).getName();
				StackTraceElement[] stackTrace = (StackTraceElement[])pairs.getValue();
				
				// convert current trace to a text-string
				StringBuilder stString = new StringBuilder();
				for(StackTraceElement stElement : stackTrace)
					stString.append(stElement.toString() + "\n");
				// calculate hash on stack-trace, to be used for quick checking for duplicates
				int stStringHash = stString.toString().hashCode();
				
				Map<Integer, StackTraceElement[]> currentThreadTraces = history.get(currentThread);
				if (currentThreadTraces == null)
					currentThreadTraces = new TreeMap<Integer, StackTraceElement[]>();
				currentThreadTraces.put(stStringHash, stackTrace);
				history.put(currentThread, currentThreadTraces);
			}
			
			try { Thread.sleep(1 + r.nextInt(500), r.nextInt(1000000)); } catch(InterruptedException ie) { break; }
		}
		
		// emit
		int nr = 0;
		Iterator<Map.Entry<String, Map<Integer, StackTraceElement[]>>> it = history.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, Map<Integer, StackTraceElement[]>> pairs = (Map.Entry<String, Map<Integer, StackTraceElement[]>>)it.next();
			String currentThread = (String)pairs.getKey();
			Map<Integer, StackTraceElement[]> currentThreadTraces = (Map<Integer, StackTraceElement[]>)pairs.getValue();
System.out.println(currentThread);
			try {
				BufferedWriter out = new BufferedWriter(new FileWriter("traces" + nr + ".dot", true));
				writeLine(out, "digraph {");
				Iterator it2 = currentThreadTraces.entrySet().iterator();
				while(it2.hasNext()) {
					Map.Entry pairs2 = (Map.Entry)it2.next();
					StackTraceElement[] trace = (StackTraceElement[])pairs2.getValue();
					for(int index=1; index<trace.length; index++)
						writeLine(out, "\t\"" + stToString(trace[index - 1]) + "\" -> \"" + stToString(trace[index]) + "\"");
				}
				writeLine(out, "}");
				out.close();
			}
			catch(IOException ie) {
				System.err.println("Failed writing to file: " + ie);
			}
			
			nr++;
		}
	}
}
