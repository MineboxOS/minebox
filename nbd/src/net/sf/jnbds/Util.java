package net.sf.jnbds;

import java.lang.reflect.InvocationTargetException;

import org.jdom.Element;


/**
 * Static convertion methods for ints/longs to/from byte arrays.
 */
public class Util {
    
    
    /**
     * Two important things:
     * 1) the "class" Attribute has to have a fully-qualified package along with the Class name.
     * 2) the Class that's being loaded has to have a public ( Element ) constructor.
     */
    public static final Object initBackendFromJDOM( Element the_backend ) {
        Object result = null;
        try {
            result = Class.forName( the_backend.getAttributeValue( "class" ) ).getConstructor( new Class[] { Element.class } ).newInstance( new Object[] { the_backend } );
	    }
	    catch( NoSuchMethodException ex1 ) {}
	    catch( ClassNotFoundException ex2 ) {}
	    catch( InvocationTargetException ex2 ) {}
	    catch( IllegalAccessException ex2 ) {}
	    catch( InstantiationException ex2 ) {}
	    
	    return result;
    }
    
    
	public static final int byteArrayToInt( byte[] b ) {
		return (int)((((b[ 0 ] & 0xff) << 24) | ((b[ 1 ] & 0xff) << 16) | ((b[ 2 ] & 0xff) << 8) | ((b[ 3 ] & 0xff) << 0)));
	}
	
	
	public static final long byteArrayToLong( byte[] b ) {
		return ((((long)b[ 0 ] & 0xff) << 56) |
				(((long)b[ 1 ] & 0xff) << 48) |
				(((long)b[ 2 ] & 0xff) << 40) |
				(((long)b[ 3 ] & 0xff) << 32) |
				(((long)b[ 4 ] & 0xff) << 24) |
				(((long)b[ 5 ] & 0xff) << 16) |
				(((long)b[ 6 ] & 0xff) <<  8) |
				(((long)b[ 7 ] & 0xff) <<  0));	}
	
	
	/**
	 * TODO: not sure if we need both ( byte ) and 0xFF here.
	 */
	public static final byte[] intToByteArray( int i ) {
		byte[] b = new byte[ 4 ];
		
		b[ 0 ] = ( byte )( 0xFF & ( i >> 24) );
		b[ 1 ] = ( byte )( 0xFF & ( i >> 16) );
		b[ 2 ] = ( byte )( 0xFF & ( i >> 8) );
		b[ 3 ] = ( byte )( 0xFF & i );
		
		return b;
	}
	
	
	/**
	 * TODO: not sure if we need both ( byte ) and 0xFF here.
	 */
	public static final byte[] longToByteArray( long l ) {
		byte[] b = new byte[ 8 ];
		
		b[ 0 ] = ( byte )( 0xFF & ( l >> 56 ) );
		b[ 1 ] = ( byte )( 0xFF & ( l >> 48 ) );
		b[ 2 ] = ( byte )( 0xFF & ( l >> 40 ) );
		b[ 3 ] = ( byte )( 0xFF & ( l >> 32 ) );
		b[ 4 ] = ( byte )( 0xFF & ( l >> 24 ) );
		b[ 5 ] = ( byte )( 0xFF & ( l >> 16 ) );
		b[ 6 ] = ( byte )( 0xFF & ( l >> 8 ) );
		b[ 7 ] = ( byte )( 0xFF & l );
		
		return b;
	}
}
