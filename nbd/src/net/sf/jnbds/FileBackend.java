package net.sf.jnbds;


import java.io.*;
import java.util.*;
import org.jdom.*;


/**
 * <p>
 * The xml in the structure.xml file must be as follows:
 * <pre>
	<backend class="net.sf.jnbds.FileBackend">
		<name>dir_name</name>
	</backend>
 * </pre>
 * </p>
 */
class FileBackend implements Backend {
    
    protected Backend state = null;
    protected Element structure = null;
    
    
	public boolean close() {
	    return state.close();
	}
	
	
    public boolean delete( String key ) {
        return state.delete( key );
    }
    
    
    public boolean exists( String key ) {
        return state.exists( key );
    }
    
    
    public FileBackend( Element structure ) {
        this.structure = structure;
        state = new ClosedFileBackend( this );
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
class ClosedFileBackend implements Backend {
    
    private FileBackend backend = null;
    
	public boolean close() {
		return true;
	}
	
	
    ClosedFileBackend( FileBackend backend ) {
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
	    backend.state = new ReadableFileBackend( backend );
	    backend = null;
	    return true;
	}
	
	
	public boolean openForReadWrite() {
	    backend.state = new ReadWritableFileBackend( backend );
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


class ReadableFileBackend implements Backend {
    
    private FileBackend backend = null;
    protected File storage_dir = null;
    
    
    /**
     * TODO: finish checkup()...
     * get the list of Ranges.
     * check how many are corrupt.
     * if any Range is corrupt then report an error and stop.
     * if the last written Range is corrupt then the admin can manually delete it and only it and everything should be okay.
     */
    private boolean checkup() {
        // code here...
        return true;
    }
    
    
	public boolean close() {
	    backend.state = new ClosedFileBackend( backend );
	    backend = null;
		return true;
	}
	
	
    public boolean delete( String key ) {
        return false;
    }
    
    
    public boolean exists( String key ) {
        return getRangeHelper( key ).exists();
    }
    
    
    ReadableFileBackend( FileBackend backend ) {
        this.backend = backend;
        storage_dir = new File( backend.structure.getChildTextTrim( "name" ) );
    }
    
    
    protected File getRangeHelper( String key ) {
        return new File( storage_dir, key );
    }
    
    
//    public long getSize() {
//        return 0;	// TODO: how do I figure out the size? Should the RangeHandler know this instead?
//    }
    
    
    public String[] list() {
        return storage_dir.list();
    }
	
	
	public boolean openForReadOnly() {
	    return true;
	}
	
	
	public boolean openForReadWrite() {
	    return false;
	}
    
    
	public byte[] read( String key ) {
        File target = getRangeHelper( key );
        byte[] ba = new byte[ ( int )target.length() ];
        if ( read( key, 0, ba, 0, ba.length ) ) return ba;
        else return null;
	    
//	    try {
//	        File target = getRangeHelper( key );
//	        InputStream is = new BufferedInputStream( new FileInputStream( target ) );
//	        byte[] ba = new byte[ ( int )target.length() ];
//	        
//	        int off = 0;
//	        int num = is.read( ba, off, ba.length - off );
//	        while ( num != -1 ) {
//	            off += num;
//		        num = is.read( ba, off, ba.length - off );
//	        }
//	        
//	        is.close();
//	        return ba;
//	    }
//	    catch ( IOException ex1 ) {}
//	    
//	    return null;
	}
	
    public boolean read( String key, long pos, byte[] buf, int off, int len ) {
        try {
	        RandomAccessFile raf = new RandomAccessFile( getRangeHelper( key ), "r" );
	        
	        raf.seek( pos );
	        raf.readFully( buf, off, len );
//	        int final_off = off + len;
//	        int current_off = off + raf.read( buf, off, len );
//	        while ( current_off < final_off ) {
//	            current_off += raf.read( buf, current_off, final_off - current_off );
//	        }
	        
	        raf.close();
	        return true;
        }
        catch ( IOException ex1 ) {}
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


class ReadWritableFileBackend extends ReadableFileBackend {
    
    public boolean delete( String key ) {
        return getRangeHelper( key ).delete();
//        File foo = getRangeHelper( key );
//        boolean bar = foo.exists();
//        return foo.delete();
    }
	
	
    ReadWritableFileBackend( FileBackend backend ) {
        super( backend );
        setup();	// TODO: capture the boolean return value
    }
	
	
	public boolean openForReadOnly() {
	    return false;
	}
	
	
	public boolean openForReadWrite() {
	    return true;
	}
	
	
    public boolean write( String key, byte[] buf ) {
        try {
	        File target = getRangeHelper( key );
	        if ( target.exists() ) {
	            if ( target.delete() == false ) return false;
	        }
	        if ( target.createNewFile() && write( key, 0, buf, 0, buf.length ) ) {
	            return true;
	        }
        }
        catch ( IOException ex1 ) {}
        return false;
    }
    
    
	/**
	 * Writes are synchronous.
	 */
    public boolean write( String key, long pos, byte[] buf, int off, int len ) {
        try {
	        RandomAccessFile raf = new RandomAccessFile( getRangeHelper( key ), "rws" );
	        
	        raf.seek( pos );
	        raf.write( buf, off, len );
	        raf.close();
	        
	        return true;
        }
        catch ( IOException ex1 ) {}
        return false;
    }
    
    
    public boolean rename( String current_key, String new_key ) {
        return false;	// TODO: need to write this code!
    }
    
    
    /**
     * Creates the directory to store the files in.
     */
    private boolean setup() {
        if ( storage_dir.exists() == false ) storage_dir.mkdirs();
        return storage_dir.exists();
    }
}
