package net.sf.jnbds;

import java.io.IOException;

import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;


/**
 * An Java Network Block Device which only supports reads.
 */
public class ReadJNBDS extends AbstractJNBDS {
    
    
	public static void main( String[] args ) throws BackendException {
		if ( args.length != 4 ) {
			System.out.println( "This is the ReadJNBDS Class.\n" );	
			System.out.println( "Usage (order is important right now): -p port -s structure_xml\n" );
			System.out.println( "-p\tport can be 0 in which case the server will bind to any free port and report it." );
			System.out.println( "-s\tthe structure.xml file." );
			System.exit( 0 );
		}
		
		try {
			ReadJNBDS jnbds = null;
		    jnbds = new ReadJNBDS( new ReadRangeHandler( ( new SAXBuilder() ).build( args[ 3 ] ).getRootElement().getChild( "rangehandler" ) ) );
			jnbds.listen( Integer.parseInt( args[ 1 ] ) );
		}
		catch( JDOMException ex1 ) {}
		catch( IOException ex1 ) {}
	}
	
	
    public ReadJNBDS( IReadRangeHandler backend ) throws BackendException {
        super( backend );
        backend.openForRead();
    }
    
    
	protected boolean write( long pos, byte[] buf ) throws BackendException {
	    return false;
	}
}
