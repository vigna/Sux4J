package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.Util;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Random;

import org.apache.log4j.Logger;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

public class GenerateRandom32BitStrings {
	public static final Logger LOGGER = Util.getLogger( GenerateRandom32BitStrings.class );
	
	public static void main( final String[] arg ) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP( GenerateRandom32BitStrings.class.getName(), "Generates 32-bit random strings",
				new Parameter[] {
					new UnflaggedOption( "n", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of strings." ),
					new UnflaggedOption( "output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The output file." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final int n = jsapResult.getInt( "n" );
		final String output = jsapResult.getString( "output" );
		
		@SuppressWarnings("unchecked")
		Random r = new Random();
	
		ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.expectedUpdates = n;
		pl.start( "Generating... " );
		
		double l = 0, t;
		double limit = Math.pow( 224, 4 );
		int incr = (int)Math.floor( 1.9999999999 * ( limit / n ) );
		
		MutableString s = new MutableString();
		final PrintWriter pw = new PrintWriter( new OutputStreamWriter( new FileOutputStream( output ), "ISO-8859-1" ) );
		
		
		for( int i = 0; i < n; i++ ) {
			t = ( l += ( r.nextInt( incr ) + 1 ) );
			if ( l >= limit ) throw new AssertionError( Integer.toString( i ) );
			s.length( 0 );
			for( int j = 4; j-- != 0; ) {
				s.append( (char)( t % 224 + 32 ) );
				t = Math.floor( t / 224 );
			}
			
			s.reverse().println( pw );
			
			pl.lightUpdate();
		}
		
		
		pl.done();
		pw.close();
		
		LOGGER.info( "Last/limit: " + ( l / limit ) );
	}
}
