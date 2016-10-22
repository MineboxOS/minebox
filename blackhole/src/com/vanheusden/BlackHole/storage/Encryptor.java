/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.storage;

import com.vanheusden.BlackHole.Utils;
import com.vanheusden.BlackHole.stats.*;
import com.vanheusden.BlackHole.Log;
import com.vanheusden.BlackHole.LogLevel;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Semaphore;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.security.InvalidAlgorithmParameterException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Encryptor {
	static String version = "$Id: Encryptor.java 606 2013-07-06 22:07:22Z folkert $";
	Cipher cEnc, cDec;
	private final Semaphore sem = new Semaphore(1, true);
	//
	Stats stats = Stats.getInstance();
	AtomicLong encTook = new AtomicLong(), decTook = new AtomicLong();
	AtomicLong encCnt = new AtomicLong(), decCnt = new AtomicLong();

	public Encryptor(EncryptionParameters ep) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
		SecretKey secretkey = new SecretKeySpec(ep.getPassword().getBytes(), ep.getCipher());

		IvParameterSpec ivs = new IvParameterSpec( new byte [] { 0, 1, 2, 3, 4, 5, 6, 7 } );

		// create a cipher for Blowfish
		cEnc = Cipher.getInstance(ep.getCipher() + "/CFB8/NoPadding");
		cDec = Cipher.getInstance(ep.getCipher() + "/CFB8/NoPadding");

		// initialise cipher with secret key
		cEnc.init(Cipher.ENCRYPT_MODE, secretkey, ivs);
		cDec.init(Cipher.DECRYPT_MODE, secretkey, ivs);

		stats.add(new StatsElementAverage(encTook, encCnt, StatsOutputType.FLOAT, false, "Encryptor " + ep.getCipher() + ": average time per encryption"));
		stats.add(new StatsElementAverage(decTook, decCnt, StatsOutputType.FLOAT, false, "Encryptor " + ep.getCipher() + ": average time per decryption"));
		stats.add(new StatsElementCounter(encCnt, "Encryptor " + ep.getCipher() + ": number of encryptions"));
		stats.add(new StatsElementCounter(decCnt, "Encryptor " + ep.getCipher() + ": number of decryptions"));
	}

	public byte [] encrypt(byte [] in) throws BadPaddingException, IllegalBlockSizeException {
		byte [] out;

		long start = System.currentTimeMillis();

		sem.acquireUninterruptibly();
		out = cEnc.doFinal(in);
		sem.release();

		encTook.addAndGet(System.currentTimeMillis() - start);
		encCnt.addAndGet(1);

		return out;
	}

	public byte [] decrypt(byte [] in) throws BadPaddingException, IllegalBlockSizeException {
		byte [] out;

		long start = System.currentTimeMillis();

		sem.acquireUninterruptibly();
		out = cDec.doFinal(in);
		sem.release();

		decTook.addAndGet(System.currentTimeMillis() - start);
		decCnt.addAndGet(1);

		return out;
	}
}
