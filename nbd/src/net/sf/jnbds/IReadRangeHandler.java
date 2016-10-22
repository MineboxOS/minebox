package net.sf.jnbds;


/**
 * An Interface for RangeHandlers which only support reads.
 * 
 * Advanced methods of handling Ranges would probably benefit from the Strategy Pattern.
 * The State Pattern could also be used to support Readable and ReadWritable modes.
 * 
 * It could probably do a LRU and/or fractal-grouping on the Ranges and aggregate them together that way.
 */
public interface IReadRangeHandler {
	
	boolean close();
	
	/**
	 * Total space for the Backend.
	 * TODO: return -1 in case of error?
	 */
	long length();
	
	boolean openForRead();
	
	boolean read( long pos, byte[] buf );
}
