package net.sf.jnbds;


/**
 * This Class represents Backends which essentially get, put, and delete files.
 * While Backends are free to implement advanced semantics, the callers often do the same, so such a duplication of effort should be avoided.
 * 
 * Each Backend is basically a "pipe and filter" -- some can be chained, some "tee" their input, and some act as endpoints.
 * Many of the Backends use the State Pattern to manage their Closed, Readable, and ReadWritable states.
 * 
 * Each Backend is initialized via the structure.xml file, but in its own particular way. See each Backend for the proper xml format.
 * 
 * Aggregation of files is tricky because that will require a method of tracking the groupings, which is another layer of complexity.
 * It might be better to leave that to the RangeHandlers.
 * The Backend is free to store more than one copy of a Range and only report one instance. However, if space is low it must delete the extra copies.
 * 
 * Guarantees?:
 * 
 * - this Class will report any ranges that are not the right length and then not open.
 * - there are no integrity checks for ranges.
 * - there are no checks for missing ranges.
 * - chances are there could be files that the RangeHandler knows nothing about. Eventually they should be "overwritten" and deleted.
 */
public interface Backend {
	
	public boolean close();
	
	public boolean delete( String key );
	
	public boolean exists( String key );
	
	/**
     * TODO: should it return an empty list or a null when the Backend is Closed?
	 */
	public String[] list();
	
//	public long getSize();
	
	public boolean openForReadOnly();
	
    public boolean openForReadWrite();
	
    public byte[] read( String key );
	public boolean read( String key, long pos, byte[] buf, int off, int len );
	
	public boolean rename( String current_key, String new_key );
    
	/**
	 * If return true then file was written successfully. Note that even if false the file could still have been written successfully.
	 */
	public boolean write( String key, byte[] buf );
	public boolean write( String key, long pos, byte[] buf, int off, int len );
}
