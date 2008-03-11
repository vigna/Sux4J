package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Random;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

public class GenerateRandom64BitStrings {

	public static void main( final String[] arg ) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP( GenerateRandom64BitStrings.class.getName(), "Generates random strings",
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
		LongOpenHashSet strings = new LongOpenHashSet( n );
		int t;
	
		ProgressLogger pl = new ProgressLogger();
		pl.expectedUpdates = n;
		pl.start( "Generating... " );
		
		for( int i = 0; i < n; i++ ) {
			long l;
			do {
				l = 0;
				for( int j = 0; j < 8; j++ ) {
					t = r.nextInt( 224 ) + 32;
					l |= l << 8 | t;
				}
			} while( ! strings.add( l ) );
			pl.lightUpdate();
		}
		
		pl.done();

		DataOutputStream dos = new DataOutputStream( new FastBufferedOutputStream( new FileOutputStream( output ) ) );
		for( LongIterator i = strings.iterator(); i.hasNext(); ) dos.writeLong( i.nextLong() );
		dos.close();
		strings = null;
		
		final long[] a = new long[ n ];
		BinIO.loadLongs( output, a );
		Arrays.sort( a );
		MutableString s = new MutableString();
		final PrintWriter pw = new PrintWriter( new OutputStreamWriter( new FileOutputStream( output ), "ISO-8859-1" ) );
		int p = Arrays.binarySearch( a, 0 );
		if ( p < 0 ) p = -p - 1;
		
		pl.expectedUpdates = n;
		pl.start( "Saving..." );
		for( int i = 0; i < n; i++ ) {
			s.length( 0 );
			for( int j = 8; j-- != 0; ) s.append( (char)( ( a[ p ] >>> j * 8 ) & 0xFF ) );
			s.println( pw );
			if ( ++p == n ) p = 0;
			pl.lightUpdate();
		}
		pl.done();
		pw.close();
	}
}
