/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.auth;

import com.vanheusden.BlackHole.Log;
import com.vanheusden.BlackHole.LogLevel;

import java.util.Hashtable;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class LDAP {
	static String version = "$Id: LDAP.java 606 2013-07-06 22:07:22Z folkert $";
	public static boolean authenticateUser(String baseDN, String username, String password, String ldapURL) {
		String dn = "uid=" + username + "," + baseDN;
		Hashtable<String, String> environment = new Hashtable<String, String>();
		environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		environment.put(Context.PROVIDER_URL, ldapURL);
		environment.put(Context.SECURITY_AUTHENTICATION, "simple");
		environment.put(Context.SECURITY_PRINCIPAL, dn);
		environment.put(Context.SECURITY_CREDENTIALS, password);

		try {
			DirContext authContext = new InitialDirContext(environment);
			Log.log(LogLevel.LOG_INFO, "Authentication for user " + username + " succeeded");
			return true;
		}
		catch (AuthenticationException ex) {
			Log.log(LogLevel.LOG_WARN, "Authentication for user " + username + " FAILED");
		}
		catch (NamingException ex) {
			Log.log(LogLevel.LOG_WARN, "Exception during LDAP authentication");
			Log.showException(ex);
		}

		return false;
	}
}
