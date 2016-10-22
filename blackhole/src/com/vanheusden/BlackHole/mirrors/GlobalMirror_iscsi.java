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
import java.net.SocketException;
import java.net.Socket;
import java.util.Arrays;

public class GlobalMirror_iscsi extends GlobalMirror implements Runnable {
	static String version = "$Id: GlobalMirror_iscsi.java 606 2013-07-06 22:07:22Z folkert $";
	String targetName;
	String initiatorName;
	InputStream inputStream;
	OutputStream outputStream;
	PDU pduIn;
	int iBlockSize;
	int cmdNr = -1;
	boolean discNotified = false;

	public GlobalMirror_iscsi(String host, int port, String target, String initiator, String mmJournalBitmap, int lun, long backendSize, int blockSize, SectorMapper sm) throws IOException {
		super(host, port, mmJournalBitmap, lun, backendSize, blockSize, sm);

		targetName = target;
		initiatorName = initiator;
	}

	void initSession() throws IOException {
		Log.log(LogLevel.LOG_DEBUG, "iGM init session");

		// FIXME
                s = new Socket(host, port);
                inputStream = s.getInputStream();
                outputStream = s.getOutputStream();

                // login & open LUN
                Log.log(LogLevel.LOG_INFO, "login & open LUN " + targetName);
                PDU pduOut = new PDU_0x03__login__request(0, targetName, 2, initiatorName);
                outputStream.write(pduOut.getAsByteArray());
                pduIn = PDU.recvPdu(inputStream);
                if (pduIn == null || pduIn.getByte(36) != 0) {
			Log.log(LogLevel.LOG_CRIT, "Failed opening LUN " + targetName);
			System.exit(1);
		}
                cmdNr = 1;
                Log.log(LogLevel.LOG_INFO, "use sn: " + cmdNr);
		// login, step 2
		if ((pduIn.getByte(1) & 128) == 0) {
			Log.log(LogLevel.LOG_INFO, "login, step 2");
			pduOut = new PDU_0x03__login__request(1, null, 3, initiatorName);
			outputStream.write(pduOut.getAsByteArray());
			pduIn = PDU.recvPdu(inputStream);
			if (pduIn.getByte(36) != 0) System.out.println("FAILURE");
		}

		CDB cdbOut;
/*
		// inquiry
		cdbOut = new CDB_inquiry_request(36);
		for (int retry=0; retry<2; retry++) {
			pduOut = new PDU_0x01_scsi_request(cmdNr, pduIn.getStatSN() + 1, cdbOut);
			outputStream.write(pduOut.getAsByteArray());
			pduIn = PDU.recvPdu(inputStream);
			Log.log(LogLevel.LOG_INFO, "statsn: " + pduIn.getStatSN());
			if (pduIn == null || pduIn.getByte(3) != 0) {
				Log.log(LogLevel.LOG_WARN, "inquiry failed");
				break;
			}
			System.out.println("" + Arrays.toString(pduIn.getCDB().getDataAsByteArray()));
			int newNBytes = pduIn.getCDB().getDataByte(4) + 5;
			if (newNBytes == cdbOut.getByte(4) + 5) break;
			cdbOut = new CDB_inquiry_request(newNBytes);
			System.out.println("Retrying inquiry with " + newNBytes);
		}
		if (pduIn == null || pduIn.getByte(3) != 0) {
			Log.log(LogLevel.LOG_WARN, "\"inquiry\" scsi command failed: " + (pduIn != null ? "" + pduIn.getByte(3) : "connection dropped"));
		}
		else {
			CDB_inquiry_response cdbRC = new CDB_inquiry_response(pduIn.getCDB());
			Log.log(LogLevel.LOG_INFO, "\"inquiry\" succeeded " + cdbRC.getVendorID() + " / " + cdbRC.getProductID());
		}

		// test unit ready
		for(int retry=0; retry<10; retry++) {
			cdbOut = new CDB_test_unit_ready(0);
			pduOut = new PDU_0x01_scsi_request(cmdNr, pduIn.getStatSN() + 1, cdbOut);
			outputStream.write(pduOut.getAsByteArray());
			pduIn = PDU.recvPdu(inputStream);
			Log.log(LogLevel.LOG_INFO, "statsn: " + pduIn.getStatSN() + " cmd: " + pduIn.getBits(0, 0, 6));
			if (pduIn == null || pduIn.getByte(3) != 0) {
				Log.log(LogLevel.LOG_WARN, "\"test unit ready\" scsi command failed: " + (pduIn != null ? "" + pduIn.getByte(3) : "connection dropped") + " (" + retry + ")");
				Thread.sleep(500);
			}
			else {
				Log.log(LogLevel.LOG_INFO, "\"test unit ready\" succeeded");
				break;
			}
		}
*/
                // get capacity
                Log.log(LogLevel.LOG_INFO, "get capacity");
                cdbOut = new CDB_read_capacity();
                pduOut = new PDU_0x01_scsi_request(cmdNr, pduIn.getStatSN() + 1, cdbOut);
                outputStream.write(pduOut.getAsByteArray());
                pduIn = PDU.recvPdu(inputStream);
                Log.log(LogLevel.LOG_INFO, "statsn: " + pduIn.getStatSN() + " cmd: " + pduIn.getBits(0, 0, 6));
                if (pduIn == null || pduIn.getByte(3) != 0) {
			Log.log(LogLevel.LOG_WARN, "Failed retrieving capacity of LUN " + targetName + ", assuming 512b blocksize and device big enough (" + backendSize + " bytes) " + (pduIn != null ? "" + pduIn.getByte(3) : ""));
			iBlockSize = 512;
		}
		else {
			CDB_read_capacity_response cdbRC = new CDB_read_capacity_response(pduIn.getCDB());
			long nBlocks = cdbRC.getLBA();
			Log.log(LogLevel.LOG_INFO, "number of blocks: " + nBlocks);
			iBlockSize = cdbRC.getBlockSize();
			Log.log(LogLevel.LOG_INFO, "block size: " + iBlockSize);
			long iSize = nBlocks * cdbRC.getLBA();
			if (iSize < backendSize) {
				Log.log(LogLevel.LOG_CRIT, "LUN " + targetName + " too small (" + iSize + ")");
				System.exit(1);
			}
		}
	}

	void disconnect() throws IOException {
		try {
			if (s != null)
				s.close();
		}
		finally {
			mcs = null;
			s = null;
			pduIn = null;
		}
	}

	public boolean transmitBlock(long offset, byte [] data) throws IOException, VHException {
		if (s == null || !s.isConnected()) {
			try {
				Thread.sleep(100);
			}
			catch(InterruptedException ie) {
				Log.showException(ie);
			}

			disconnect();
			reconnect();
		}

		if (s != null && s.isConnected()) {
			try {
				// write block
				PDU pduOut = new PDU_0x01_scsi__write__request(cmdNr, pduIn.getStatSN() + 1, offset / iBlockSize, iBlockSize, data);
				outputStream.write(pduOut.getAsByteArray());

				pduIn = PDU.recvPdu(inputStream);
				PDU_0x21_scsi_response pduInR = (PDU_0x21_scsi_response)pduIn;
				if (pduInR.getStatus() != 0) {
					System.out.println("" + pduInR.getStatus());
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

				discNotified = false;
				return true;
			}
			catch(SocketException se) { if (!discNotified) { Log.log(LogLevel.LOG_WARN, "GM transmitBlock() socket exception " + se); discNotified = true; } }
			catch(IOException ie) { if (!discNotified) { Log.log(LogLevel.LOG_WARN, "GM transmitBlock() IO exception " + ie);  discNotified = true; }}
		}

		return false;
	}
}
