package net.sf.jnbds;


import java.sql.*;
import java.util.regex.*;
import org.jdom.*;


public class ReadRangeHandler implements IReadRangeHandler {
	
    protected Backend backend = null;
    protected int block_size = 0;
	protected Connection conn = null;
    final protected String meta_file = "meta";
    protected int num_blocks = 0;
	protected boolean opened = false;
	protected Element structure = null;
	
	
	public boolean close() {
	    try {
			if ( conn != null && conn.isClosed() == false ) {
			    // use SHUTDOWN SCRIPT here and then zip it and give it to the backend...
				Statement stmt_shutdown = conn.createStatement( ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );
				stmt_shutdown.execute( "SHUTDOWN SCRIPT" );
			}
	    }
	    catch ( SQLException ex1 ) {}
	    finally {
	        try {
		        if ( conn != null ) conn.close();
	        }
	        catch ( SQLException ex2 ) {}
	        finally {
	            conn = null;
	        }
	    }
		opened = ! backend.close();
		return opened;
	}
	
	
	public long length() {
	    return block_size * num_blocks;
	}
	
	
	public boolean openForRead() {
		if ( opened == false && backend.openForReadOnly() ) {
		    openHelper();
		}
		return opened;
	}
	
	
	private void dumpSQL() {
	    try {
			Statement stmt_get_ranges = conn.createStatement( ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );
			ResultSet rs = stmt_get_ranges.executeQuery( "select * from ranges" );
			
			while ( rs.next() ) {
			    System.out.println( rs.getLong( "ID" ) + "\t" + rs.getLong( "INCL" ) + "\t" + rs.getLong( "EXCL" ) );
			}
	    }
	    catch ( SQLException ex1 ) {}
	    finally {
	        opened = false;
	    }
	}
	
	
	protected void openHelper() {
	    try {
		    Class.forName( "org.hsqldb.jdbcDriver" );
		    conn = DriverManager.getConnection( structure.getChild( "jdbc").getChildTextTrim( "connect" ), structure.getChild( "jdbc").getChildTextTrim( "user" ), structure.getChild( "jdbc").getChildTextTrim( "password" ) );
		    if ( getMeta() ) {
		        opened = true;
		    }
	    }
	    catch ( Exception ex1 ) {}
		
	    if ( opened == false ) {
	        close();
	    }
	}
	
	
	public boolean read( long pos, byte[] buf ) {
	    try {
			Statement stmt_get_ranges = conn.createStatement( ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );
			ResultSet rs = stmt_get_ranges.executeQuery( "select * from ranges where incl < " + ( pos + buf.length ) + " and excl > " + ( pos ) );
			
			// we fill the array with zeroes in case the ResultSet is "sparse"...
			for ( int i = 0; i < buf.length; i++ ) {
		        buf[ i ] = 0x00;
			}
			
			while ( rs.next() ) {
			    long incl = rs.getLong( "INCL" );
			    long excl = rs.getLong( "EXCL" );
		        int start = ( int )( Math.max( incl, pos ) - incl );
		        int end = ( int )( Math.min( pos + buf.length, excl ) - incl );
		        
		        String id = String.valueOf( rs.getInt( "ID" ) );
		        
			    backend.read( String.valueOf( rs.getInt( "ID" ) ), start, buf, ( int )( Math.max( incl, pos ) - pos ), end - start );
			}
			
			return true;
	    }
	    catch ( SQLException ex1 ) {}
	    return false;
	}
	
	
	public ReadRangeHandler( Element structure ) {
	    this.structure = structure;
	    backend = ( Backend )Util.initBackendFromJDOM( structure.getChild( "backend" ) );
	}
	
	
	protected boolean getMeta() {
	    // read the meta_file into a String...
	    String m = new String( backend.read( meta_file ) );
	    
	    Pattern p_block_size = Pattern.compile( ".*block_size=(\\d+)", Pattern.DOTALL );
	    Matcher matcher = p_block_size.matcher( m );
	    if ( matcher.lookingAt() == false ) return false;
	    block_size = Integer.parseInt( matcher.group( 1 ) );
	    
	    Pattern p_num_blocks = Pattern.compile( ".*num_blocks=(\\d+)", Pattern.DOTALL );
	    matcher = p_num_blocks.matcher( m );
	    if ( matcher.lookingAt() == false ) return false;
	    num_blocks = Integer.parseInt( matcher.group( 1 ) );
	    
	    return true;
	}
}
