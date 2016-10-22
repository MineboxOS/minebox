package net.sf.jnbds;


import java.io.*;
import org.jdom.*;
import org.jdom.input.SAXBuilder;


/**
 * An Java Network Block Device which supports read/write.
 */
public class ReadWriteJNBDS extends AbstractJNBDS {
    
    private IReadWriteRangeHandler rw_rh = null;
    
    
	public static void main( String[] args ) throws BackendException {
		if ( args.length != 4 ) {
			System.out.println( "This is the ReadWriteJNBDS Class.\n" );	
			System.out.println( "Usage (order is important right now): -p port -s structure_xml\n" );
			System.out.println( "-p\tport can be 0 in which case the server will bind to any free port and report it." );
			System.out.println( "-s\tthe structure.xml file." );
			System.exit( 0 );
		}
		
		try {
			ReadWriteJNBDS jnbds = null;
		    jnbds = new ReadWriteJNBDS( new ReadWriteRangeHandler( ( new SAXBuilder() ).build( args[ 3 ] ).getRootElement().getChild( "rangehandler" ) ) );
			jnbds.listen( Integer.parseInt( args[ 1 ] ) );
		}
		catch( JDOMException ex1 ) {}
		catch( IOException ex1 ) {}
	}
	
	
    public ReadWriteJNBDS( IReadWriteRangeHandler handler ) throws BackendException {
        super( handler );
        rw_rh = handler;
        rw_rh.close();
        rw_rh.openForReadWrite();
    }
    
    
	protected boolean write( long pos, byte[] buf ) throws BackendException {
	    rw_rh.write( pos, buf );
		return true;
	}
}
