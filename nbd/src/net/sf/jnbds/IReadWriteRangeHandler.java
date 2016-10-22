package net.sf.jnbds;


/**
 * An Interface for RangeHandlers which support reads and writes.
 */
public interface IReadWriteRangeHandler extends IReadRangeHandler {
	
    boolean openForReadWrite();
    
	boolean write( long pos, byte[] b );
}
