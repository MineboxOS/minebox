package net.sf.jnbds;


import java.io.*;
import java.util.zip.*;
import org.jdom.*;


/**
 * This Class (de)compresses the files as they are read/written using the Inflater/Deflater java.zip code.
 * Obviously this means that a person could fit more data onto a Backend, but the exact amount depends on how compressible the data being written is.
 * Ideally the Client should use a compressed Filesystem, but this Backend might still be able to wring out some performance.
 * This Class has not been optimized.
 * <p>
 * The xml in the structure.xml file must be as follows:
 * <pre>
	<backend class="net.sf.jnbds.ZipBackend">
		<backend class="..."/>
	</backend>
 * </pre>
 * </p>
 */
public class ZipBackend implements Backend {
    
    private Backend backend = null;
    private Element structure = null;
    
    
	public boolean close() {
	    return backend.close();
	}
	
	
    public boolean delete( String key ) {
        return backend.delete( key );
    }
    
    
    public boolean exists( String key ) {
        return backend.exists( key );
    }
    
    
//    public long getSize() {
//        return state.getSize();
//    }
    
    
//    private int keyHelper( String key ) {
//        return key.hashCode() % backends.length;
//    }
    
    
    public String[] list() {
        return backend.list();
    }
	
	
	public boolean openForReadOnly() {
	    return backend.openForReadOnly();
	}
	
	
	public boolean openForReadWrite() {
	    return backend.openForReadWrite();
	}
	
	
    public byte[] read( String key ) {
        byte[] ba = backend.read( key );
        if ( ba == null ) return null;
        
        try {
	        InflaterInputStream iis = new InflaterInputStream( new ByteArrayInputStream( ba ) );
	        ByteArrayOutputStream baos = new ByteArrayOutputStream( ba.length * 2 );
	        byte[] read = new byte[ 512 ];
	        int num = iis.read( read );
	        while ( num != -1 ) {
	            baos.write( read, 0, num );
	            num = iis.read( read );
	        }
	        iis.close();
	        baos.close();
	        return baos.toByteArray();
        }
        catch( IOException ex1 ) {}
        return null;
    }
    public boolean read( String key, long pos, byte[] buf, int off, int len ) {
        byte[] ba = read( key );
        if ( ba == null ) return false;
        
        System.arraycopy( ba, ( int )pos, buf, off, len );
        return true;
    }
    
    
    public boolean rename( String current_key, String new_key ) {
        return false;
    }
    
    
    public ZipBackend( Element structure ) {
        this.structure = structure;
        backend = ( Backend )Util.initBackendFromJDOM( structure.getChild( "backend" ) );
    }
    
    
    public boolean write( String key, byte[] buf ) {
//        byte[] ba = backend.read( key );
//        if ( ba == null ) return null;
//        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        DeflaterOutputStream dos = new DeflaterOutputStream( baos );
	        dos.write( buf );
	        dos.close();
	        return backend.write( key, baos.toByteArray() );
        }
        catch( IOException ex1 ) {}
        return false;
    }
    public boolean write( String key, long pos, byte[] buf, int off, int len ) {
        // TODO: this code needs to read the key, inflate it, write into it, deflate it, and write it to the backend.
        return false;
    }
}
