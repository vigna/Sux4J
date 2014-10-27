package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;

import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

public class GenerateRandom64BitStrings {
	public static final Logger LOGGER = LoggerFactory.getLogger( GenerateRandom64BitStrings.class );
	
	public static void main( final String[] arg ) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP( GenerateRandom64BitStrings.class.getName(), "Generates a list of sorted 64-bit random strings using only characters in the ISO-8859-1 printable range [32..256).",
				new Parameter[] {
					new FlaggedOption( "repeat", JSAP.INTSIZE_PARSER, "1", JSAP.NOT_REQUIRED, 'r', "repeat", "Repeat each byte this number of times" ),
					new FlaggedOption( "gap", JSAP.INTSIZE_PARSER, "1", JSAP.NOT_REQUIRED, 'g', "gap", "Impose a minimum gap." ),
					new UnflaggedOption( "n", JSAP.LONG_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of strings (too small values might cause overflow)." ),
					new UnflaggedOption( "output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The output file." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final long n = jsapResult.getLong( "n" );
		final int repeat = jsapResult.getInt( "repeat" );
		final int gap = jsapResult.getInt( "gap" );
		final String output = jsapResult.getString( "output" );
		
		RandomGenerator r = new XorShift1024StarRandomGenerator();
	
		ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.expectedUpdates = n;
		pl.start( "Generating... " );
		
		BigInteger l = BigInteger.ZERO, t;
		BigInteger limit = BigInteger.valueOf( 224 ).pow( 8 );
		long incr = (long)Math.floor( 1.99 * ( limit.divide( BigInteger.valueOf( n ) ).longValue() ) ) - 1;
		
		@SuppressWarnings("resource")
		final FastBufferedOutputStream fbs = new FastBufferedOutputStream( new FileOutputStream( output ) );
		final BigInteger divisor = BigInteger.valueOf( 224 );
		
		LOGGER.info( "Increment: " + incr );
		
		BigInteger a[];
		int[] b = new int[ 8 ];
		
		for( long i = 0; i < n; i++ ) {
			l = l.add( BigInteger.valueOf( ( r.nextLong() & 0x7FFFFFFFFFFFFFFFL ) % incr + gap ) );
			t = l; 
			if ( l.compareTo( limit ) >= 0 ) throw new AssertionError( Long.toString( i ) );
			for( int j = 8; j-- != 0; ) {
				a = t.divideAndRemainder( divisor );
				b[ j ] = a[ 1 ].intValue() + 32;
				assert b[ j ] < 256;
				assert b[ j ] >= 32;
				t = a[ 0 ];
			}

			for( int j = 0; j < 8; j++ ) 
				for( int k = repeat; k-- != 0; ) 
					fbs.write( b[ j ] );
			fbs.write( 10 );
			pl.lightUpdate();
		}
		
		
		pl.done();
		fbs.close();
		
		LOGGER.info( "Last/limit: " + ( l.doubleValue() / limit.doubleValue() ) );
	}
}
