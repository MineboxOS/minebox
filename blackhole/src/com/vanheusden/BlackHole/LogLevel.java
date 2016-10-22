/* Released under GPL 2.0
 * (C) 2010-2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole;

import com.vanheusden.BlackHole.Log;

public enum LogLevel { LOG_EMERG(0, "system is unusable"), LOG_ALERT(1, "action must be taken immediately"), LOG_CRIT(2, "critical conditions"),
	LOG_ERR(3, "error conditions"), LOG_WARN(4, "warning conditions"), LOG_NOTICE(5, "normal, but significant, condition"),
	LOG_INFO(6, "informational message"), LOG_DEBUG(7, "debug-level message");
	static String version = "$Id: LogLevel.java 606 2013-07-06 22:07:22Z folkert $";
	int ll;
	String descr;

	LogLevel(int ll, String descr) {
		this.ll = ll;
		this.descr = descr;
	}

	final public int getLevel() {
		return ll;
	}

	final public String getName() {
		switch(this) {
			case LOG_EMERG:
				return "emerg";
			case LOG_ALERT:
				return "alert";
			case LOG_CRIT:
				return "crit";
			case LOG_ERR:
				return "err";
			case LOG_WARN:
				return "warn";
			case LOG_NOTICE:
				return "notice";
			case LOG_INFO:
				return "info";
			case LOG_DEBUG:
				return "debug";
		}

		return null;
	}

	public static LogLevel getLogLevel(int level) {
		LogLevel [] levels = new LogLevel[] { LOG_EMERG, LOG_ALERT, LOG_CRIT, LOG_ERR, LOG_WARN, LOG_NOTICE, LOG_INFO, LOG_DEBUG };
		if (level > 7) {
			Log.log(LOG_WARN, "Loglevel truncated to 7");
			level = 7;
		}
		return levels[level];
	}

	public static LogLevel getLogLevel(String which) {
		if (which.equalsIgnoreCase("emerg"))
			return LOG_EMERG;
		if (which.equalsIgnoreCase("alert"))
			return LOG_ALERT;
		if (which.equalsIgnoreCase("crit"))
			return LOG_CRIT;
		if (which.equalsIgnoreCase("err"))
			return LOG_ERR;
		if (which.equalsIgnoreCase("warn"))
			return LOG_WARN;
		if (which.equalsIgnoreCase("notice"))
			return LOG_NOTICE;
		if (which.equalsIgnoreCase("info"))
			return LOG_INFO;
		if (which.equalsIgnoreCase("debug"))
			return LOG_DEBUG;

		return null;
	}
};
