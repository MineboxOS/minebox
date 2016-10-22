/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.*;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;

import java.util.concurrent.Semaphore;

public class Hasher {
	static String version = "$Id$";
	List<Map.Entry<Long, Object[]>> list = null;
	AtomicInteger pointer = new AtomicInteger();
	int listSize;
	Thread [] t;
	//
	int nCPUs;
	static Hasher instance;
	MessageDigest [] md;
	static HashType ht;

	protected Hasher(HashType hashType) throws NoSuchAlgorithmException {
		nCPUs = Runtime.getRuntime().availableProcessors();
		t = new Thread[nCPUs];

		String hashName = hashType.getName();
		md = new MessageDigest[nCPUs];
		for(int index=0; index<nCPUs; index++)
			md[index] = MessageDigest.getInstance(hashName);
	}

	public static Hasher getInstance(HashType hashType) throws NoSuchAlgorithmException {
		if (instance == null) {
			instance = new Hasher(hashType);
			ht = hashType;
		}

		assert ht == hashType;

		return instance;
	}

	public static Hasher getInstance() throws NoSuchAlgorithmException {
		assert instance != null;

		return instance;
	}

	public void doit(List<Map.Entry<Long, Object[]>> list) {
		// System.out.println("doit started");

		this.list = list;
		listSize = list.size();
		pointer.set(0);

		for(int nr=0; nr<nCPUs; nr++) {
			t[nr] = new Thread(new HasherDoit(list, listSize, pointer, md[nr]));
			t[nr].start();
		}

		for(int nr=0; nr<nCPUs; nr++) {
			try {
				t[nr].join();
			}
			catch(InterruptedException ie) {
				Log.log(LogLevel.LOG_DEBUG, "Hasher: interrupted while waiting for thread " + nr + " of " + nCPUs);
			}
		}

		// System.out.println("doit finished");
	}

	class HasherDoit implements Runnable {
		final List<Map.Entry<Long, Object[]>> list;
		final AtomicInteger pointer;
		final int listSize;
		final MessageDigest md;

		HasherDoit(List<Map.Entry<Long, Object[]>> list, int listSize, AtomicInteger pointer, MessageDigest md) {
			this.list = list;
			this.listSize = listSize;
			this.pointer = pointer;
			this.md = md;
		}

		public void run() {
			for(;;) {
				int current = pointer.getAndIncrement();
				if (current >= listSize)
					break;

				// hash
				Map.Entry<Long, Object[]> me = list.get(current);
				Object [] pair = me.getValue();
				byte [] data = (byte [])pair[0];
				pair[1] = md.digest(data);
			}
		}
	}
}
