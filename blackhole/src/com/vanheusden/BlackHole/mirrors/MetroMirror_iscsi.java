/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.mirrors;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.iscsi.*;
import com.vanheusden.BlackHole.sockets.*;
import com.vanheusden.BlackHole.storage.*;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;

public class MetroMirror_iscsi extends MetroMirror implements Runnable {
	static String version = "$Id: MetroMirror_iscsi.java 606 2013-07-06 22:07:22Z folkert $";
	String targetName;
	String initiatorName;
	InputStream inputStream;
	OutputStream outputStream;
	PDU pduIn;
	int iBlockSize;
	int cmdNr = -1;

	public MetroMirror_iscsi(String host, int port, String targetName, String initiatorName, String mmJournalBitmap, int lun, long backendSize, int blockSize, boolean disconnectedBlock, SectorMapper sm) throws IOException {
		super(host, port, mmJournalBitmap, lun, backendSize, blockSize, disconnectedBlock, sm);
		this.targetName = targetName;
		this.initiatorName = initiatorName;
		this.lun = lun;
	}

	void initSession() throws IOException {
		Log.log(LogLevel.LOG_DEBUG, "iMM init session");

		// FIXME
                s = new Socket(host, port);
                inputStream = s.getInputStream();
                outputStream = s.getOutputStream();

                // login & open LUN
                Log.log(LogLevel.LOG_INFO, "login & open LUN " + targetName);
                PDU pduOut = new PDU_0x03__login__request(0, targetName, 2, initiatorName);
                outputStream.write(pduOut.getAsByteArray());
                pduIn = PDU.recvPdu(inputStream);
                if (pduIn.getByte(36) != 0) {
			Log.log(LogLevel.LOG_CRIT, "Failed opening LUN " + targetName);
			System.exit(1);
		}
                cmdNr = 1;
                Log.log(LogLevel.LOG_DEBUG, "use sn: " + cmdNr);
		// login, step 2
		if ((pduIn.getByte(1) & 128) == 0) {
			Log.log(LogLevel.LOG_INFO, "login, step 2");
			pduOut = new PDU_0x03__login__request(1, null, 3, initiatorName);
			outputStream.write(pduOut.getAsByteArray());
			pduIn = PDU.recvPdu(inputStream);
			if (pduIn.getByte(36) != 0) System.out.println("FAILURE");
		}

                // get capacity
                Log.log(LogLevel.LOG_INFO, "get capacity");
                CDB cdbOut = new CDB_read_capacity();
                pduOut = new PDU_0x01_scsi_request(cmdNr, pduIn.getStatSN() + 1, cdbOut);
                outputStream.write(pduOut.getAsByteArray());
                pduIn = PDU.recvPdu(inputStream);
                Log.log(LogLevel.LOG_DEBUG, "statsn: " + pduIn.getStatSN());
                if (pduIn.getByte(3) != 0) {
			Log.log(LogLevel.LOG_WARN, "Failed retrieving capacity of LUN " + targetName + ", assuming 512b blocksize and device big enough (" + backendSize + " bytes)");
			iBlockSize = 512;
		}
		else {
			CDB_read_capacity_response cdbRC = new CDB_read_capacity_response(pduIn.getCDB());
			long nBlocks = cdbRC.getLBA();
			Log.log(LogLevel.LOG_DEBUG, "number of blocks: " + nBlocks);
			iBlockSize = cdbRC.getBlockSize();
			Log.log(LogLevel.LOG_DEBUG, "block size: " + iBlockSize);
			long iSize = nBlocks * cdbRC.getLBA();
			if (iSize < backendSize) {
				Log.log(LogLevel.LOG_CRIT, "LUN " + targetName + " too small (" + iSize + ")");
				System.exit(1);
			}
		}
	}

	public void disconnect() throws IOException {
		Log.log(LogLevel.LOG_DEBUG, "iMM close session");
		if (s != null) {
			s.close();
			s = null;
			pduIn = null;
		}
	}

	public boolean transmitBlockLow(long offset, byte [] data) throws IOException {
		try {
			// write block
			PDU pduOut = new PDU_0x01_scsi__write__request(cmdNr, pduIn.getStatSN() + 1, offset / iBlockSize, iBlockSize, data);
			outputStream.write(pduOut.getAsByteArray());

			pduIn = PDU.recvPdu(inputStream);
			PDU_0x21_scsi_response pduInR = (PDU_0x21_scsi_response)pduIn;
			if (pduInR.getStatus() != 0) {
				System.out.println("" + pduInR.getResponse());
				Log.log(LogLevel.LOG_CRIT, "Failed transmitting block " + pduInR.getResponse() + "/" + pduInR.getStatus());
				pduOut = new PDU_0x01_scsi__request_sense__request(cmdNr, pduIn.getStatSN() + 1);
				outputStream.write(pduOut.getAsByteArray());
				pduIn = PDU.recvPdu(inputStream);
				if (pduInR.getStatus() != 0)
					System.out.println("failed requesting sense");
				System.out.println("" + Arrays.toString(pduIn.getCDB().getAsByteArray()));
				System.out.println("" + Arrays.toString(pduIn.getData()));
				System.exit(1);
			}
			if (pduInR.getResponse() != 0) {
				Log.log(LogLevel.LOG_CRIT, "Failed transmitting block: iSCSI error " + pduInR.getResponse());
				System.exit(1);
			}

			return true;
		}
		catch(SocketException se) { Log.log(LogLevel.LOG_WARN, "iMM transmitBlockLow() socket exception " + se); }
		catch(IOException ie) { Log.log(LogLevel.LOG_WARN, "iMM transmitBlockLow() IO exception " + ie); }

		disconnect();

		return false;
	}
}
