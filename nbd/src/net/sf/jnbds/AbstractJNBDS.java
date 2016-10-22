package net.sf.jnbds;


import java.io.*;
import java.net.*;


/**
 * The base Class for the Java Network Block Device Server.
 * Currently nothing is multi-threaded.
 */
public abstract class AbstractJNBDS implements Runnable {
	protected IReadRangeHandler backend = null;
	private int client_port;
	private Thread client_thread = null;
	private Socket conn = null;
	final private int read_length_limit = 1024 * 1024;
	
	private static final byte[] cliserv_magic = new byte[] { 0x00, 0x00, 0x42, 0x02, ( byte )( 0x81 ), ( byte )( 0x86 ), 0x12, 0x53};
	private static final byte[] init_passwd = new byte[] { 'N', 'B', 'D', 'M', 'A', 'G', 'I', 'C' };
	private static final String version = "20050317";
	
	
	public AbstractJNBDS( IReadRangeHandler backend ) throws BackendException {
	    this.backend = backend;
	}
	
	
	public void listen( int serverPort ) {
		ServerSocket ss = null;
	    try {
			ss = new ServerSocket( serverPort );
			System.out.println( "jnbds listening on port: " + ss.getLocalPort() );
			conn = ss.accept();
			client_thread = new Thread( this );
			client_thread.start();
			
			// wait for input so that we can shut down...
			System.in.read();
	    }
	    catch ( IOException ex1 ) {}
//	    catch ( BackendException ex2 ) {}
	    catch ( Exception ex3 ) {}
	    finally {
	        System.out.println( "closing..." );
	        client_thread.interrupt();
	        try {
	            backend.close();
	            conn.close();
	    		ss.close();
	        }
	        catch ( IOException ex2 ) {}
	    }
	}
	
	
	/**
	 * TODO: Need to make it Runnable.
	 */
	public void run() {
		try {
			final OutputStream outputStream = conn.getOutputStream();
			outputStream.write( init_passwd );
			outputStream.write( cliserv_magic );
			outputStream.write( Util.longToByteArray( backend.length() ) );
			for ( int i = 0; i < 128; i++ ) outputStream.write( 0 );
			
			byte[] read_magic = new byte[ 4 ];
			byte[] type = new byte[ 4 ];
			byte[] handle = new byte[ 8 ];
			byte[] from = new byte[ 8 ];
			byte[] len = new byte[ 4 ];
			
			//loop over the connection...
			for ( ;; ) {
				final InputStream inputStream = conn.getInputStream();
				inputStream.read( read_magic, 0, 4 );
				inputStream.read( type, 0, 4 );
				inputStream.read( handle, 0, 8 );
				inputStream.read( from, 0, 8 );
				inputStream.read( len, 0, 4 );
				
				int read_length = Util.byteArrayToInt( len );
				if ( read_length > read_length_limit ) throw new Exception( "read_length of " + read_length + " is greater than read_length_limit of " + read_length_limit );
				
				if ( Util.byteArrayToInt( read_magic ) != 0x25609513 ) {
					throw new Exception( "Unexpected protocol version! Got: " + Util.byteArrayToInt( read_magic ) + " , expected: 0x25609513" );
				}
				
				// read request
				if ( Util.byteArrayToInt( type ) == 0 ) {
					// send 'ack'
					outputStream.write( Util.intToByteArray( 0x67446698 ) );
					outputStream.write( Util.intToByteArray( 0 ) );
					outputStream.write( handle );
					
					long current = Util.byteArrayToLong( from );
					
					System.out.println( "r\t" + current + "\t" + read_length );
					
					byte[] buf = new byte[ read_length ];
					backend.read( current, buf );
					outputStream.write( buf, 0, read_length );
				}
				
				// write request
				else if ( Util.byteArrayToInt( type ) == 1 ) {
					System.out.println( "w\t" + Util.byteArrayToLong( from ) + "\t" + read_length );
					
					boolean success = true;
					byte[] buf = new byte[ read_length ];
					
					int off = 0;
					while ( off < buf.length ) {
						off += inputStream.read( buf, off, buf.length - off );
					}
					
					success = write( Util.byteArrayToLong( from ), buf );
					
					// send 'ack'
					outputStream.write( Util.intToByteArray( 0x67446698 ) );
					outputStream.write( Util.intToByteArray( ( success ? 0 : 1 ) ) );
					outputStream.write( handle );
				}
				
				// invalid request
				else {
					throw new Exception( "Unexpected command type: " + Util.byteArrayToInt( type ) );
				}
			}
		}
		catch ( Exception ex1 ) {
			ex1.printStackTrace();
		}
	}
	
	
	protected abstract boolean write( long pos, byte[] buf ) throws BackendException;
}
