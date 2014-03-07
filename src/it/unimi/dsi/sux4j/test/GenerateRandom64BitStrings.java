package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;

import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

public class GenerateRandom64BitStrings {
	public static final Logger LOGGER = LoggerFactory.getLogger( GenerateRandom64BitStrings.class );
	
	public static void main( final String[] arg ) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP( GenerateRandom64BitStrings.class.getName(), "Generates 64-bit random strings",
				new Parameter[] {
					new UnflaggedOption( "n", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of strings." ),
					new UnflaggedOption( "output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The output file." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final int n = jsapResult.getInt( "n" );
		final String output = jsapResult.getString( "output" );
		
		RandomGenerator r = new XorShift1024StarRandomGenerator();
	
		ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.expectedUpdates = n;
		pl.start( "Generating... " );
		
		BigInteger l = BigInteger.ZERO, t;
		BigInteger limit = BigInteger.valueOf( 224 ).pow( 8 );
		long incr = (long)Math.floor( 1.99 * ( limit.divide( BigInteger.valueOf( n ) ).longValue() ) ) - 1;
		
		final MutableString s = new MutableString();
		final PrintWriter pw = new PrintWriter( new OutputStreamWriter( new FileOutputStream( output ), "ISO-8859-1" ) );
		final BigInteger divisor = BigInteger.valueOf( 224 );
		
		LOGGER.info( "Increment: " + incr );
		
		BigInteger a[];
		
		for( int i = 0; i < n; i++ ) {
			l = l.add( BigInteger.valueOf( ( r.nextLong() & 0x7FFFFFFFFFFFFFFFL ) % incr + 1 ) );
			t = l; 
			if ( l.compareTo( limit ) >= 0 ) throw new AssertionError( Integer.toString( i ) );
			s.length( 0 );
			for( int j = 8; j-- != 0; ) {
				a = t.divideAndRemainder( divisor );
				s.append( (char)( a[ 1 ].longValue() + 32 ) );
				t = a[ 0 ];
			}
			
			s.reverse().println( pw );
			pl.lightUpdate();
		}
		
		
		pl.done();
		pw.close();
		
		LOGGER.info( "Last/limit: " + ( l.doubleValue() / limit.doubleValue() ) );
	}
}
