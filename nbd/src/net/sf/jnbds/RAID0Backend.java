package net.sf.jnbds;


import java.util.*;
import org.jdom.*;


/**
 * This Class stripes files across its Backends -- it uses ( key.hashCode() % backends.length ) to pick the Backend.
 * It is not threaded.
 * This Class is good for multiple clients who are reading and writing different keys (well, it would be good if it was threaded).
 * A better implementation for a single client would be to break up each key and write equal portions to all the Backends in parallel.
 * <p>
 * The xml in the structure.xml file must be as follows:
 * <pre>
	<backend class="net.sf.jnbds.RAID0Backend">
		<backends>
			<backend class="..."/>
			...
		</backends>
	</backend>
 * </pre>
 * </p>
 */
class RAID0Backend implements Backend {
    
    private Backend[] backends = null;
    private Element structure = null;
    
    
	public boolean close() {
	    boolean success = true;
	    for ( int i = 0; i < backends.length; i++ ) {
	        success = success && backends[ i ].close();
	    }
	    return success;
	}
	
	
    public boolean delete( String key ) {
        return backends[ keyHelper( key ) ].delete( key );
    }
    
    
    public boolean exists( String key ) {
        return backends[ keyHelper( key ) ].exists( key );
    }
    
    
//    public long getSize() {
//        return state.getSize();
//    }
    
    
    private int keyHelper( String key ) {
        return key.hashCode() % backends.length;
    }
    
    
    public String[] list() {
        String[] list = null;
        Set all = new HashSet();
        
        for ( int i = 0; i < backends.length; i++ ) {
	        list = backends[ i ].list();
	        for ( int j = 0; j < list.length; j++ ) {
	            all.add( list[ j ] );
	        }
        }
        
        Object[] set_list = all.toArray();
        list = new String[ set_list.length ];
        for ( int i = 0; i < set_list.length; i++ ) {
            list[ i ] = ( String )set_list[ i ];
        }
        
        return list;
    }
	
	
	public boolean openForReadOnly() {
	    boolean success = true;
	    for ( int i = 0; i < backends.length; i++ ) {
	        success = success && backends[ i ].openForReadOnly();
	    }
	    return success;
	}
	
	
	public boolean openForReadWrite() {
	    boolean success = true;
	    for ( int i = 0; i < backends.length; i++ ) {
	        success = success && backends[ i ].openForReadWrite();
	    }
	    return success;
	}
	
	
    public byte[] read( String key ) {
        return backends[ keyHelper( key ) ].read( key );
    }
    public boolean read( String key, long pos, byte[] buf, int off, int len ) {
        return backends[ keyHelper( key ) ].read( key, pos, buf, off, len );
    }
    
    
    public boolean rename( String current_key, String new_key ) {
        return false;
    }
    
    
    public RAID0Backend( Element structure ) {
        this.structure = structure;
        List l_e = structure.getChild( "backends" ).getChildren( "backend" );
        List l_b = new ArrayList();
        
        for ( ListIterator li = l_e.listIterator(); li.hasNext(); ) {
            l_b.add( Util.initBackendFromJDOM( ( Element )li.next() ) );
        }
        
        backends = new Backend[ l_b.size() ];
        for ( ListIterator li = l_b.listIterator(); li.hasNext(); ) {
            backends[ li.nextIndex() ] = ( Backend )li.next();
        }
    }
    
    
    public boolean write( String key, byte[] buf ) {
        return backends[ keyHelper( key ) ].write( key, buf );
    }
    
    
    public boolean write( String key, long pos, byte[] buf, int off, int len ) {
        return backends[ keyHelper( key ) ].write( key, pos, buf, off, len );
    }
}
