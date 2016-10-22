package net.sf.jnbds;


import java.util.Random;
import org.jdom.input.SAXBuilder;


public class ReadWriteRangeHandlerTest {
    
	public static void main( String[] args ) throws Exception {
	    ReadWriteRangeHandler rwb = new ReadWriteRangeHandler( ( new SAXBuilder() ).build( args[ 0 ] ).getRootElement().getChild( "rangehandler" ) );
	    
	    try {
		    rwb.openForReadWrite();
		    int num_blocks = rwb.num_blocks;	// 256;	//rwb.num_blocks;
		    int block_size = rwb.block_size;	// 4096;
		    
		    byte[] data = new byte[ num_blocks * block_size ];
		    for ( int i = 0; i < data.length; i++ ) {
		        data[ i ] = 0x00;
		    }
		    
		    Random rand = new Random( 0 );
		    boolean read_next = false;
		    
		    for ( int j = 0; j < 100; j++ ) {
		        int bp = rand.nextInt( num_blocks - 1 );
		        int num_test_blocks = Math.min( rand.nextInt( num_blocks - bp ), rand.nextInt( 32 ) ) + 1;
		        
		        byte[] buf = new byte[ num_test_blocks * block_size ];
				
		        if ( rand.nextDouble() > 0.9 ) {
		            if ( read_next ) read_next = false;
		            else read_next = true;
		        }
		        
		        if ( read_next ) {
		            // read
					System.out.println( "read\t" + bp + "\t" + num_test_blocks );
			        rwb.read( bp * block_size, buf );
			        for ( int i = 0; i < buf.length; i++ ) {
			            if ( buf[ i ] != data[ i + bp * block_size ] ) {
			                throw new Exception();
			            }
			        }
		        }
		        else {
		            // write
					System.out.println( "write\t" + bp + "\t" + num_test_blocks );
		            rand.nextBytes( buf );
			        System.arraycopy( buf, 0, data, bp * block_size, buf.length );
			        rwb.write( bp * block_size, buf );
		        }
		        
		        Thread.sleep( 500 );
		    }
	    }
	    catch ( InterruptedException ex1 ) {
	        throw new BackendException( ex1 );
	    }
	    catch ( Exception ex1 ) {
	        throw new BackendException( ex1 );
	    }
	    finally {
	        rwb.close();
	    }
	}
}
