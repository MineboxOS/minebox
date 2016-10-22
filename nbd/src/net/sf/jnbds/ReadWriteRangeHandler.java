package net.sf.jnbds;


import java.io.*;
import java.sql.*;
import java.util.*;
import org.jdom.*;
import org.jdom.input.SAXBuilder;


public class ReadWriteRangeHandler extends ReadRangeHandler implements IReadWriteRangeHandler {
    
    private int bob_size = 65536;
    
    
    /**
     * Right now block_size should be 512 * 2**n bytes long.
     * Does this let us avoid blocks that overlap, or can it be any size, actually?
     */
    protected boolean format( int block_size, int num_blocks ) {
        // for simplicity's sake loop and delete everything on the Backend...
        backend.close();
        backend.openForReadWrite();
        
	    String[] files = backend.list();
	    for ( int i = 0; files != null && i < files.length; i++ ) {
	        backend.delete( files[ i ] );
	    }
	    
	    // create the meta file...
	    StringWriter sw = new StringWriter();
	    sw.write( "block_size=" + block_size + "\n" );
	    sw.write( "num_blocks=" + num_blocks + "\n" );
	    backend.write( meta_file, sw.toString().getBytes() );
	    backend.close();
	    
        close();
        openForReadWrite();
	    try {
		    // empty the db...
		    // create the db and then close it and then serialize it to the backend so that the proper open() calls will read it in...
			Statement stmt_format = conn.createStatement( ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );
			stmt_format.execute( "DELETE FROM RANGES *" );
	    }
	    catch ( SQLException ex1 ) {}
	    close();
	    
	    return true;
    }
    
    
	public boolean openForReadWrite() {
		if ( opened == false && backend.openForReadWrite() ) {
		    openHelper();
		}
		return opened;
	}
	
	
	/**
	 * 
	 * @param structure The <rangehandler> element.
	 */
	public ReadWriteRangeHandler( Element structure ) {
	    super( structure );
	    bob_size = Integer.parseInt( structure.getChildTextTrim( "bob_size" ) );
	}
	
	
	public boolean write( long pos, byte[] buf ) {
		if ( pos % 512 != 0 || buf.length < 512 || buf.length % 512 != 0 ) {
		    return false;
		}
		
		try {
			for ( long i = ( pos / bob_size ) * bob_size; i < pos + buf.length; i += bob_size ) {
				int src_offset = ( int )Math.max( i - pos, 0 );
				int dst_offset = ( int )Math.max( pos - i, 0 );
				int length = ( int )( Math.min( i + bob_size, pos + buf.length ) - ( pos + src_offset ) );
				
				Statement stmt_get_ranges = conn.createStatement( ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );
				ResultSet rs = stmt_get_ranges.executeQuery( "select * from ranges where incl < " + ( i + bob_size ) + " and excl > " + ( i ) );
				
			    if ( rs.next() ) {
			        backend.write( String.valueOf( rs.getLong( "ID" ) ), dst_offset, buf, src_offset, length );
			    }
			    else {
			        byte[] foo = new byte[ bob_size ];
			        System.arraycopy( buf, src_offset, foo, dst_offset, length );
			        Statement stmt_insert_range = conn.createStatement();
			        stmt_insert_range.execute( "INSERT INTO ranges VALUES ( null, " + i + ", " + ( i + bob_size ) + ")" );
			        Statement stmt_id = conn.createStatement();
			        ResultSet rs_id = stmt_insert_range.executeQuery( "CALL IDENTITY()" );
			        rs_id.next();
			        backend.write( String.valueOf( rs_id.getLong( 1 ) ), foo );
			    }
			}
			return true;
		}
		catch ( SQLException ex1 ) {}
		return false;
	}
	
	
	public static void main( String[] args ) throws BackendException {
	    if ( ( args.length == 5 ) && ( "--format".equalsIgnoreCase( args[ 0 ] ) ) ) {
	        System.out.println( "Formatting the jnbds..." );
	        boolean success = false;
	        try {
		        ReadWriteRangeHandler rw_rh = new ReadWriteRangeHandler( ( new SAXBuilder() ).build( args[ 4 ] ).getRootElement().getChild( "rangehandler" ) );
		        success = rw_rh.format( Integer.parseInt( args[ 1 ] ), Integer.parseInt( args[ 2 ] ) );
	        }
	        catch( JDOMException ex1 ) {}
	        catch( IOException ex1 ) {}
	        
	        if ( success ) {
	            System.out.println( "success" );
	        }
	        else {
	            System.out.println( "failure" );
	        }
	        System.exit( 0 );
	    }
		else {
			System.out.println( "This is the ReadWriteRangeHandler Class.\n" );	
			System.out.println( "Usage (order is important right now): --format block_size num_blocks -s structure_xml\n" );
			System.exit( 0 );
		}
	}
}
