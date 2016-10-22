package net.sf.jnbds;


import java.lang.reflect.*;
import java.io.*;
import java.util.*;
import org.jdom.*;


/**
 * <p>
 * This Class creates read and write caches for two reasons:
 * 1) to avoid accessing slow Backends;
 * 2) to allow random access to files even when a Backend doesn't support that requirement.
 * </p>
 * <p>
 * It is not threaded. Obviously, being single threaded is of limited usefulness, but as I like to say, make it right, then make it fast.
 * Write-backs only occur on writes, so there is a chance that the Client will hide the Server's "freeze" (multiple write-backs happen at the same time).
 * The read and write caches are logically one, but are separated physically. Each cache can be implemented using a different Backend -- neat!
 * </p>
 * <p>
 * The xml in the structure.xml file must be as follows:
 * <pre>
		<backend class="net.sf.jnbds.CachingBackend">
			<cache_size>50</cache_size>
			<real>
				<backend class="..."/>
			</real>
			<read>
				<backend class="..."/>
			</read>
			<write>
				<backend class="..."/>
			</write>
		</backend>
 * </pre>
 * The cache_size can be set to any other natural number, but remember that it takes the files given to it which, 
 * after going through the RangeHandler, might be larger than the Client's.
 * </p>
 */
class CachingBackend implements Backend {
    
    protected Backend state = null;
    protected Element structure = null;
    
    
    public CachingBackend( Element node ) {
        structure = node;
        state = new ClosedCachingBackend( this );
    }
    
    
	public boolean close() {
	    return state.close();
	}
	
	
    public boolean delete( String key ) {
        return state.delete( key );
    }
    
    
    public boolean exists( String key ) {
        return state.exists( key );
    }
    
    
//    public long getSize() {
//        return state.getSize();
//    }
    
    
    public String[] list() {
        return state.list();
    }
	
	
	public boolean openForReadOnly() {
	    return state.openForReadOnly();
	}
	
	
	public boolean openForReadWrite() {
	    return state.openForReadWrite();
	}
	
	
    public byte[] read( String key ) {
        return state.read( key );
    }
    public boolean read( String key, long pos, byte[] buf, int off, int len ) {
        return state.read( key, pos, buf, off, len );
    }
    
    
    public boolean rename( String current_key, String new_key ) {
        return state.rename( current_key, new_key );
    }
    
    
    public boolean write( String key, byte[] buf ) {
        return state.write( key, buf );
    }
    public boolean write( String key, long pos, byte[] buf, int off, int len ) {
        return state.write( key, pos, buf, off, len );
    }
}


/**
 * I'm not sure whether this Class should return false/null for methods that won't do anything (delete, list, etc)
 * or whether it should throw an UnsupportedOperationException, which is an Unchecked Exception.
 * The first way is more forgiving than the second way.
 */
class ClosedCachingBackend implements Backend {
    
    private CachingBackend backend = null;
    
	public boolean close() {
		return true;
	}
	
	
    ClosedCachingBackend( CachingBackend backend ) {
        this.backend = backend;
    }
    
    
    public boolean delete( String key ) {
        return false;
    }
    
    
    public boolean exists( String key ) {
        return false;
    }
    
    
//    public long getSize() {
//        return 0;	// TODO: how do I figure out the size? Should the RangeHandler know this instead?
//    }
    
    
    public String[] list() {
        return null;
    }
    
	
	public boolean openForReadOnly() {
	    backend.state = new ReadableCachingBackend( backend );
	    backend = null;
	    return true;
	}
	
	
	public boolean openForReadWrite() {
	    backend.state = new ReadWritableCachingBackend( backend );
	    backend = null;
	    return true;
	}
    
    
    public byte[] read( String key ) {
        return null;
    }
    
    
    public boolean read( String key, long pos, byte[] buf, int off, int len ) {
        return false;
    }
	
	
    public boolean rename( String current_key, String new_key ) {
        return false;
    }
    
    
    public boolean write( String key, byte[] buf ) {
        return false;
    }
    
    
    public boolean write( String key, long pos, byte[] buf, int off, int len ) {
        return false;
    }
}


/**
 * ReadableCachingBackend will write some of the ranges it reads from its Backend to the disk in order to cache them.
 * It won't honor write requests though.
 * So there is writing involved, but it should be entirely transparent.
 */
class ReadableCachingBackend implements Backend {
    
    protected int cache_size = 0;
    private CachingBackend parent = null;
    protected Backend real_backend = null;
    protected Backend read_cache = null;
    protected Backend write_cache = null;
    
    
    /**
     * TODO: finish checkup()...
     */
    private boolean checkup() {
        // code here...
        return true;
    }
    
    
	public boolean close() {
	    if ( read_cache.close() && write_cache.close() && real_backend.close() ) {
		    parent.state = new ClosedCachingBackend( parent );
		    parent = null;
			return true;
	    }
	    return false;
	}
	
	
    public boolean delete( String key ) {
        return false;
    }
    
    
    public boolean exists( String key ) {
        return write_cache.exists( key ) || read_cache.exists( key ) || real_backend.exists( key );
    }
    
    
//    private void doStructure() {
//        Element the_backend = null;
//        
//        try {
//            the_backend = parent.structure.getChild( "real" ).getChild( "backend" );
//            Class the_class = Class.forName( the_backend.getAttributeValue( "class" ) );
//	        real_backend = ( Backend )the_class.getConstructor( new Class[] { Element.class } ).newInstance( new Object[] { the_backend } );
//	        real_backend.openForReadOnly();
//	        
//            the_backend = parent.structure.getChild( "read" ).getChild( "backend" );
//	        read_cache = ( Backend )Class.forName( the_backend.getAttributeValue( "class" ) ).getConstructor( new Class[] { Element.class } ).newInstance( new Object[] { the_backend } );
//	        read_cache.openForReadWrite();
//	        
//            the_backend = parent.structure.getChild( "write" ).getChild( "backend" );
//	        write_cache = ( Backend )Class.forName( the_backend.getAttributeValue( "class" ) ).getConstructor( new Class[] { Element.class } ).newInstance( new Object[] { the_backend } );
//	        write_cache.openForReadOnly();
//        }
//        catch( NoSuchMethodException ex1 ) {}
//        catch( ClassNotFoundException ex2 ) {}
//        catch( InvocationTargetException ex2 ) {}
//        catch( IllegalAccessException ex2 ) {}
//        catch( InstantiationException ex2 ) {}
//    }
    
    
    ReadableCachingBackend( CachingBackend parent ) {
        this.parent = parent;
        cache_size = Integer.parseInt( parent.structure.getChildTextTrim( "cache_size" ) );
        
        real_backend = ( Backend )Util.initBackendFromJDOM( parent.structure.getChild( "real" ).getChild( "backend" ) );
        real_backend.openForReadOnly();
        
        read_cache = ( Backend )Util.initBackendFromJDOM( parent.structure.getChild( "read" ).getChild( "backend" ) );
        read_cache.openForReadWrite();
        
        write_cache = ( Backend )Util.initBackendFromJDOM( parent.structure.getChild( "write" ).getChild( "backend" ) );
        write_cache.openForReadOnly();
//        read_cache = new FileBackend( "ReadCache" );
//        read_cache.openForReadWrite();
//        write_cache = new FileBackend( "WriteCache" );
//        write_cache.openForReadOnly();
//        //real_backend = new SleepBackend( "FileBackend" );
//        //real_backend = new FileBackend( "FileBackend" );
//        //real_backend = new RAID0Backend( "" );
//        real_backend = new ZipBackend( "" );
//        real_backend.openForReadOnly();
    }
    
    
//    public long getSize() {
//        return 0;	// TODO: how do I figure out the size? Should the RangeHandler know this instead?
//    }
    
    
    public String[] list() {
        String[] list = null;
        Set all = new HashSet();
        
        list = real_backend.list();
        for ( int i = 0; i < list.length; i++ ) {
            all.add( list[ i ] );
        }
        
        list = read_cache.list();
        for ( int i = 0; i < list.length; i++ ) {
            all.add( list[ i ] );
        }
        
        list = write_cache.list();
        for ( int i = 0; i < list.length; i++ ) {
            all.add( list[ i ] );
        }
        
        Object[] set_list = all.toArray();
        list = new String[ set_list.length ];
        for ( int i = 0; i < set_list.length; i++ ) {
            list[ i ] = ( String )set_list[ i ];
        }
        
        return list;
        
    }
	
	
	public boolean openForReadOnly() {
	    return true;
	}
	
	
	public boolean openForReadWrite() {
	    return false;
	}
    
    
    protected void readHelper( String key ) {
        //System.out.println( read_cache.list().length + "\t" + write_cache.list().length );
        
        if ( write_cache.exists( key ) == false && read_cache.exists( key ) == false ) {
            String[] rc_keys = read_cache.list();
            String[] wc_keys = write_cache.list();
            while ( rc_keys.length > 0 && rc_keys.length + wc_keys.length > cache_size ) {
                int chosen = ( new Random() ).nextInt( rc_keys.length );
                read_cache.delete( rc_keys[ chosen ] );
                rc_keys = read_cache.list();
                wc_keys = write_cache.list();
            }
            
            // copy the key from the real_backend into the read_cache...
            byte[] ba = real_backend.read( key );
            if ( ba != null ) {
                read_cache.write( key, ba );
            }
        }
    }
    
    
//    /**
//     * This original version only considered the size of the read_cache and only removed one key at a time.
//     */
//    protected void orig_readHelper( String key ) {
//        if ( write_cache.exists( key ) == false && read_cache.exists( key ) == false ) {
//            String[] keys = read_cache.list();
//            if ( keys.length > 100 ) {
//                int chosen = ( new Random() ).nextInt( keys.length );
//                read_cache.delete( keys[ chosen ] );
//            }
//            
//            // copy the key from the real_backend into the read_cache...
//            byte[] ba = real_backend.read( key );
//            if ( ba != null ) {
//                read_cache.write( key, ba );
//            }
//        }
//    }
    
    
	public byte[] read( String key ) {
	    readHelper( key );
	    if ( write_cache.exists( key ) ) return write_cache.read( key );
	    return read_cache.read( key );
	}
	
	
    public boolean read( String key, long pos, byte[] buf, int off, int len ) {
	    readHelper( key );
	    if ( write_cache.exists( key ) ) return write_cache.read( key, pos, buf, off, len );
        return read_cache.read( key, pos, buf, off, len );
    }
    
    
    public boolean rename( String current_key, String new_key ) {
        return false;
    }
	
	
    public boolean write( String key, byte[] buf ) {
        return false;
    }
    
    
    public boolean write( String key, long pos, byte[] buf, int off, int len ) {
        return false;
    }
}


/**
 * This Class represents the readable and writable state of a CachingBackend.
 */
class ReadWritableCachingBackend extends ReadableCachingBackend {
    
    public boolean delete( String key ) {
        if ( write_cache.exists( key ) ) {
            if ( write_cache.delete( key ) == false ) return false;
        }
        else if ( read_cache.exists( key ) ) {
            if ( read_cache.delete( key ) == false ) return false;
        }
        
        if ( real_backend.exists( key ) ) {
            return real_backend.delete( key );
        }
        
        return false;
    }
	
	
    ReadWritableCachingBackend( CachingBackend backend ) {
        super( backend );
        write_cache.close();
        write_cache.openForReadWrite();
        real_backend.close();
        real_backend.openForReadWrite();
    }
	
	
	public boolean openForReadOnly() {
	    return false;
	}
	
	
	public boolean openForReadWrite() {
	    return true;
	}
	
	
	private void writeBack( String key ) {
        //System.out.println( read_cache.list().length + "\t" + write_cache.list().length );
        
        String[] keys = write_cache.list();
        Random rand = new Random();
        
        int transmitted = 0;
        long time = System.currentTimeMillis();
        
        for ( int i = 0; i < keys.length; i++ ) {
            if ( keys[ i ].compareTo( key ) != 0 ) {
	            byte[] ba = write_cache.read( keys[ i ] );
	            // if we cannot write the key to the real_backend then don't delete it...
	            if ( ba != null && real_backend.write( keys[ i ], ba ) ) {
	                read_cache.write( keys[ i ], ba );
	                write_cache.delete( keys[ i ] );
	                transmitted += ba.length;
	            }
            }
        };
        
        long finish_time = System.currentTimeMillis();
        System.out.println( "write-back:\t" + transmitted + " in " + ( finish_time - time ) + " millis is " + ( ( transmitted / 1024 ) / ( ( double )( finish_time - time ) / 1000 ) ) + " KB/s" );
	}
	
	
	protected void writeHelper( String key ) {
        if ( ( new Random() ).nextDouble() < 0.1 ) writeBack( key );
	    
        // don't use read() because it will cause unnecessary writing to the read_cache...
	    if ( write_cache.exists( key ) ) {}
        else if ( read_cache.exists( key ) ) {
	        byte[] ba = read_cache.read( key );
	        if ( ba != null && read_cache.delete( key ) ) {
	            write_cache.write( key, ba );
	        }
        }
        else {
            if ( real_backend.exists( key ) ) {
	            byte[] ba = real_backend.read( key );
	            if ( ba != null ) {
	                write_cache.write( key, ba );
	            }
            }
        }
	}
	
	
//	/**
//	 * This original version would only write-back the keys to the real_backend.
//	 */
//	protected void orig_writeHelper( String key ) {
//        String[] keys = write_cache.list();
//        Random rand = new Random();
//        if ( keys.length > 10 && rand.nextDouble() < 0.10 ) {
//            int transmitted = 0;
//            long time = System.currentTimeMillis();
//            
//            for ( int i = 0; i < keys.length; i++ ) {
////	            int chosen;
////	            do {
////	                chosen = ( new Random() ).nextInt( keys.length );
////	            } while ( keys[ chosen ].compareTo( key ) == 0 );
//	            
//	            // if we cannot write the key to the real_backend then don't delete it...
//                if ( keys[ i ].compareTo( key ) != 0 ) {
//		            byte[] ba = write_cache.read( keys[ i ] );
//		            if ( ba != null && real_backend.write( keys[ i ], ba ) ) {
//		                write_cache.delete( keys[ i ] );
//		                transmitted += ba.length;
//		            }
//                }
//            }
//            
//            long finish_time = System.currentTimeMillis();
//            System.out.println( transmitted + " in " + ( finish_time - time ) + " millis is " + ( ( transmitted / 1024 ) / ( ( double )( finish_time - time ) / 1000 ) ) + " KB/s" );
//        }
//        
//	    if ( write_cache.exists( key ) ) return;
//	    
//        // don't use read() because it will pollute the read_cache
//        if ( read_cache.exists( key ) ) {
//	        byte[] ba = read_cache.read( key );
//	        if ( ba != null && read_cache.delete( key ) ) {
//	            write_cache.write( key, ba );
//	        }
//        }
//        else {
//            if ( real_backend.exists( key ) ) {
//	            byte[] ba = real_backend.read( key );
//	            if ( ba != null ) {
//	                write_cache.write( key, ba );
//	            }
//            }
//        }
//	}
	
	
	public boolean write( String key, byte[] buf ) {
        writeHelper( key );
        return write_cache.write( key, buf );
    }
    
    
    public boolean write( String key, long pos, byte[] buf, int off, int len ) {
        writeHelper( key );
        return write_cache.write( key, pos, buf, off, len );
    }
    
    
    public boolean rename( String current_key, String new_key ) {
        return false;	// TODO: need to write this code!
    }
}
