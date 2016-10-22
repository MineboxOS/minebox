package net.sf.jnbds;


import org.jdom.*;


/**
 * This Class simulates a slow Backend by sleeping for a set amount of time for many operations.
 * This Class should be retooled to a ShaperBackend which emulates a connection that has both latency and bandwidth constraints.
 * This Class is useful for testing slow Backends locally (using FileBackend, for example) with advanced caching algorithms (like CachingBackend).
 * <p>
 * The xml in the structure.xml file must be as follows:
 * <pre>
	<backend class="net.sf.jnbds.SleepBackend">
		<sleep>1000</sleep>
		<backend class="..."/>
	</backend>
 * </pre>
 * </p>
 */
class SleepBackend implements Backend {
    
    private Backend backend = null;
    private Element structure = null;
    private int sleep = 0;
    
    
	public boolean close() {
	    try {
	        Thread.sleep( sleep );
	    }
	    catch ( InterruptedException ex1 ) {}
	    return backend.close();
	}
	
	
    public boolean delete( String key ) {
	    try {
	        Thread.sleep( sleep );
	    }
	    catch ( InterruptedException ex1 ) {}
        return backend.delete( key );
    }
    
    
    /**
     * Doesn't sleep because this information should be cached.
     */
    public boolean exists( String key ) {
//	    try {
//	        System.out.println( "sleep exists()" );
//	        Thread.sleep( sleep );
//	    }
//	    catch ( InterruptedException ex1 ) {}
        return backend.exists( key );
    }
    
    
//    public long getSize() {
//        return state.getSize();
//    }
    
    
    /**
     * Doesn't sleep because this information should be cached.
     */
    public String[] list() {
//	    try {
//	        Thread.sleep( sleep );
//	    }
//	    catch ( InterruptedException ex1 ) {}
        return backend.list();
    }
	
	
	public boolean openForReadOnly() {
	    try {
	        Thread.sleep( sleep );
	    }
	    catch ( InterruptedException ex1 ) {}
	    return backend.openForReadOnly();
	}
	
	
	public boolean openForReadWrite() {
	    try {
	        Thread.sleep( sleep );
	    }
	    catch ( InterruptedException ex1 ) {}
	    return backend.openForReadWrite();
	}
	
	
    public byte[] read( String key ) {
	    try {
	        Thread.sleep( sleep );
	    }
	    catch ( InterruptedException ex1 ) {}
        return backend.read( key );
    }
    public boolean read( String key, long pos, byte[] buf, int off, int len ) {
	    try {
	        Thread.sleep( sleep );
	    }
	    catch ( InterruptedException ex1 ) {}
        return backend.read( key, pos, buf, off, len );
    }
    
    
    public boolean rename( String current_key, String new_key ) {
	    try {
	        Thread.sleep( sleep );
	    }
	    catch ( InterruptedException ex1 ) {}
        return backend.rename( current_key, new_key );
    }
    
    
    SleepBackend( Element structure ) {
        this.structure = structure;
        sleep = Integer.parseInt( structure.getChildTextTrim( "sleep" ) );
        backend = ( Backend )Util.initBackendFromJDOM( structure.getChild( "backend" ) );
    }
    
    
    public boolean write( String key, byte[] buf ) {
	    try {
	        Thread.sleep( sleep );
	    }
	    catch ( InterruptedException ex1 ) {}
        return backend.write( key, buf );
    }
    
    
    public boolean write( String key, long pos, byte[] buf, int off, int len ) {
	    try {
	        Thread.sleep( sleep );
	    }
	    catch ( InterruptedException ex1 ) {}
        return backend.write( key, pos, buf, off, len );
    }
}
