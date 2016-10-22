/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.auth;

import com.vanheusden.BlackHole.*;
import com.vanheusden.BlackHole.storage.files.*;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Users {
	static String version = "$Id: Users.java 606 2013-07-06 22:07:22Z folkert $";
	TableOnDisk users;
	final int maxNUsers = 128;
	MessageDigest md;
	static Users instance;

	protected Users() {
	}

	protected Users(String file) throws IOException, NoSuchAlgorithmException, VHException {
		boolean newFile = !new File(file).exists();
		users = new TableOnDisk(file, 16 + 16, maxNUsers, null);

                md = MessageDigest.getInstance("MD5");

		if (newFile) {
			addUser("root", "root");
			Log.log(LogLevel.LOG_WARN, "No user database exists, creating new one with default user root/root");
		}
	}

	public static Users getInstance(String file) throws IOException, NoSuchAlgorithmException, VHException {
		if (instance == null)
			instance = new Users(file);

		return instance;
	}

	public byte [] genRecord(String username, String password) {
                byte [] hash = md.digest(password.getBytes());
		byte [] record = new byte[32];

		Utils.arrayCopy(record, 0, username.getBytes());
		Utils.arrayCopy(record, 16, hash);

		return record;
	}

	public boolean authenticateUser(String username, String password) throws IOException, VHException {
		username = username.toLowerCase();

		byte [] recordCmp = genRecord(username, password);

		for(int index=0; index<maxNUsers; index++) {
			byte [] recordIn = users.retrieveElement(index, false, -1);
			if (Arrays.equals(recordCmp, recordIn))
				return true;
		}

		return false;
	}

	public boolean addUser(String username, String password) throws IOException, VHException {
		username = username.toLowerCase();
		int index;
		for(index=0; index<maxNUsers; index++) {
			byte [] recordIn = users.retrieveElement(index, false, -1);
			if (recordIn[0] == 0x00)
				break;
		}

		if (index == maxNUsers)
			return false;	// not enough space

		byte [] record = genRecord(username, password);
		users.storeElement(index, record, -1);

		return true;
	}

	public boolean changePassword(String username, String oldPassword, String newPassword) throws IOException, VHException {
		username = username.toLowerCase();
		byte [] recordOld = genRecord(username, oldPassword);
		byte [] recordNew = genRecord(username, newPassword);

		for(int index=0; index<maxNUsers; index++) {
			byte [] recordIn = users.retrieveElement(index, false, -1);
			if (Arrays.equals(recordOld, recordIn))
				users.storeElement(index, recordNew, -1);
			return true;
		}

		return false;
	}

	public boolean delUser(String username) throws IOException, VHException {
		username = username.toLowerCase();

		byte [] recordCmp = new byte[32];
		Utils.arrayCopy(recordCmp, 0, username.getBytes());

		for(int index=0; index<maxNUsers; index++) {
			byte [] recordIn = users.retrieveElement(index, false, -1);

			boolean found = true;
			for(int cmpIndex=0; cmpIndex<16; cmpIndex++) {
				if (recordCmp[cmpIndex] != recordIn[cmpIndex]) {
					found = false;
					break;
				}
			}

			if (found) {
				byte [] clean = new byte[32];
				users.storeElement(index, clean, -1);
				return true;
			}
		}

		return false;
	}

	public List<String> listUsers() throws IOException, VHException {
		List<String> list = new ArrayList<String>();

		for(int index=0; index<maxNUsers; index++) {
			byte [] recordIn = users.retrieveElement(index, false, -1);
			if (recordIn[0] != 0x00) {
				StringBuilder username = new StringBuilder();
				for(int getIndex=0; getIndex<16; getIndex++) {
					if (recordIn[getIndex] == 0x00)
						break;

					username.append((char)recordIn[getIndex]);
				}

				list.add(username.toString());
			}
		}

		return list;
	}
}
