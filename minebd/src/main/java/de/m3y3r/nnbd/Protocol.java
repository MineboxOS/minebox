package de.m3y3r.nnbd;

public class Protocol {

	/* global flags */
	public static final int NBD_FLAG_FIXED_NEWSTYLE = 1 << 0; /* new-style export that actually supports extending */
	public static final int NBD_FLAG_NO_ZEROES = 1 << 1; /* we won't send the 128 bits of zeroes if the client sends NBD_FLAG_C_NO_ZEROES */

	/* Options that the client can select to the server */
	public static final int NBD_OPT_EXPORT_NAME = 1; /** Client wants to select a named export (is followed by name of export) */
	public static final int NBD_OPT_ABORT = 2; /** Client wishes to abort negotiation */
	public static final int NBD_OPT_LIST = 3;

	/* values for transmission flags field */
	public static final int NBD_FLAG_HAS_FLAGS = (1 << 0); /* Flags are there */
	public static final int NBD_FLAG_READ_ONLY = (1 << 1); /* Device is read-only */
	public static final int NBD_FLAG_SEND_FLUSH = (1 << 2); /* Send FLUSH */
	public static final int NBD_FLAG_SEND_FUA = (1 << 3); /* Send FUA (Force Unit Access) */
	public static final int NBD_FLAG_ROTATIONAL = (1 << 4); /* Use elevator algorithm - rotational media */
	public static final int NBD_FLAG_SEND_TRIM = (1 << 5); /* Send TRIM (discard) */

	/* commands */
	public static final int NBD_CMD_READ = 0;
	public static final int NBD_CMD_WRITE = 1;
	public static final int NBD_CMD_DISC = 2;
	public static final int NBD_CMD_FLUSH = 3;
	public static final int NBD_CMD_TRIM = 4;

	/* response flags */
	public static final int NBD_REP_FLAG_ERROR = (1 << 31); /** If the high bit is set, the reply is an error */
	public static final int NBD_REP_ERR_UNSUP = (1 | NBD_REP_FLAG_ERROR); /** Client requested an option not understood by this version of the server */
	public static final int NBD_REP_ERR_POLICY = (2 | NBD_REP_FLAG_ERROR); /** Client requested an option not allowed by server configuration. (e.g., the option was disabled) */
	public static final int NBD_REP_ERR_INVALID = (3 | NBD_REP_FLAG_ERROR); /** Client issued an invalid request */
	public static final int NBD_REP_ERR_PLATFORM = (4 | NBD_REP_FLAG_ERROR);

}
