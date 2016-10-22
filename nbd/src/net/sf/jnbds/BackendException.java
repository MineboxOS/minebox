package net.sf.jnbds;


/**
 * A generic backend Exception, useful for encapsulating all the different Exceptions that could happen.
 */
public class BackendException extends Exception {
	
	
	BackendException( String s ) {
		super( s );
	}
	
	
	BackendException( Throwable ex1 ) {
		super( ex1 );
	}
}
