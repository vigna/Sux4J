package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.io.FileLinesList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

public class FunctionSpeedTest {

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP( FunctionSpeedTest.class.getName(), "Test the speed of a function",
				new Parameter[] {
					new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read terms." ),
					new FlaggedOption( "n", JSAP.INTSIZE_PARSER, "1000000", JSAP.NOT_REQUIRED, 'n',  "number-of-strings", "The (maximum) number of strings used for random testing." ),
					new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding." ),
					new FlaggedOption( "save", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "save", "In case of a random test, save to this file the strings used." ),
					new Switch( "zipped", 'z', "zipped", "The term list is compressed in gzip format." ),
					new Switch( "random", 'r', "random", "Test randomly selected and shuffled strings." ),
					new Switch( "check", 'c', "check", "Check that the term list is mapped to its ordinal position." ),
					new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised function." ),
					new UnflaggedOption( "termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "Read terms from this file." ),
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final String functionName = jsapResult.getString( "function" );
		final String termFile = jsapResult.getString( "termFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean check = jsapResult.getBoolean( "check" );
		final boolean random = jsapResult.getBoolean( "random" );
		final String save = jsapResult.getString( "save" );
		final int maxStrings = jsapResult.getInt( "n" );
		
		if ( save != null && ! random ) throw new IllegalArgumentException( "You can save test string only for random tests" );
		
		@SuppressWarnings("unchecked")
		final Object2LongFunction<? extends CharSequence> function = (Object2LongFunction<? extends CharSequence>)BinIO.loadObject( functionName );
		final FileLinesCollection flc = new FileLinesCollection( termFile, encoding.name(), zipped );

		if ( random ) {
			final FileLinesList fll = new FileLinesList( termFile, encoding.name() );
			final int size = fll.size();
			int n = Math.min( maxStrings, size );
			final MutableString[] test = new MutableString[ n ];
			final int step = size / n;
			for( int i = 0; i < n; i++ ) test[ i ] = fll.get( i * step );
			Collections.shuffle( Arrays.asList( test ) );

			if ( save != null ) {
				final PrintWriter pw = new PrintWriter( new OutputStreamWriter( new FastBufferedOutputStream( new FileOutputStream( save ) ), encoding ) );
				for( MutableString s: test ) s.println( pw );
				pw.close();
			}
			
			long total = 0;
			for( int k = 13; k-- != 0; ) {
				long time = -System.nanoTime();
				for( int i = 0; i < n; i++ ) {
					function.getLong( test[ i ] );
					if ( i++ % 100000 == 0 ) System.out.print('.');
				}
				System.out.println();
				time += System.nanoTime();
				if ( k < 10 ) total += time;
				System.out.println( Util.format( time / 1E9 ) + "s, " + Util.format( (double)time / n ) + " ns/item" );
			}
			System.out.println( "Average: " + Util.format( total / 10E9 ) + "s, " + Util.format( total / ( 10. * n ) ) + " ns/item" );
		}
		else {
			long total = 0;
			int size = 0;
			for( int k = 13; k-- != 0; ) {
				final Iterator<? extends CharSequence> i = flc.iterator();

				long time = -System.nanoTime();
				int j = 0;
				long index;
				while( i.hasNext() ) {
					index = function.getLong( i.next() );
					if ( check && index != j ) throw new AssertionError( index + " != " + j ); 
					if ( j++ % 100000 == 0 ) System.err.print('.');
				}
				size = j;
				System.err.println();
				time += System.nanoTime();
				if ( k < 10 ) total += time;
				System.err.println( Util.format( time / 1E9 ) + "s, " + Util.format( (double)time / j ) + " ns/item" );
			}
			System.out.println( "Average: " + Util.format( total / 10E9 ) + "s, " + Util.format( total / ( 10. * size ) ) + " ns/item" );
		}
	}
}
