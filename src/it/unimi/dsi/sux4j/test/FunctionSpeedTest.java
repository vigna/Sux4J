package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.io.FileLinesList;

import java.io.File;
import java.io.FileInputStream;
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

		final SimpleJSAP jsap = new SimpleJSAP( FunctionSpeedTest.class.getName(), "Test the speed of a function. Performs thirteen repetitions: the first three ones are warmup, and the average of the remaining ten is printed on standard output. The detailed results are logged to standard error.",
				new Parameter[] {
					new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read terms." ),
					new FlaggedOption( "n", JSAP.INTSIZE_PARSER, "1000000", JSAP.NOT_REQUIRED, 'n',  "number-of-strings", "The (maximum) number of strings used for random testing." ),
					new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding." ),
					new FlaggedOption( "save", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "save", "In case of a random test, save to this file the strings used." ),
					new FlaggedOption( "file", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'f', "file", "In case of a random test, store the random strings in this file and use it to scan them." ),
					new Switch( "zipped", 'z', "zipped", "The term list is compressed in gzip format (for sequential tests only)." ),
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
		final String file = jsapResult.getString( "file" );
		final String save = jsapResult.getString( "save" );
		final int maxStrings = jsapResult.getInt( "n" );
		
		if ( zipped && random ) throw new IllegalArgumentException( "You cannot use zipped files for random tests" );
		if ( jsapResult.userSpecified( "n" ) && ! random ) throw new IllegalArgumentException( "The number of string is meaningful for random tests only" );
		if ( save != null && ! random ) throw new IllegalArgumentException( "You can save test string only for random tests" );
		if ( file != null && ! random ) throw new IllegalArgumentException( "The \"file\" option is meaningful for random tests only" );
		
		@SuppressWarnings("unchecked")
		final Object2LongFunction<? extends CharSequence> function = (Object2LongFunction<? extends CharSequence>)BinIO.loadObject( functionName );
		final FileLinesCollection flc = new FileLinesCollection( termFile, encoding.name() );

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
			
			if ( file != null ) {
				final FastBufferedOutputStream fbos =  new FastBufferedOutputStream( new FileOutputStream( file ) );
				for( MutableString s: test ) s.writeSelfDelimUTF8( fbos );
				fbos.close();
				final FastBufferedInputStream fbis = new FastBufferedInputStream( new FileInputStream( file ) );
				System.gc();
				System.gc();
				
				long total = 0;
				final MutableString s = new MutableString();
				for( int k = 13; k-- != 0; ) {
					fbis.position( 0 );
					long time = -System.nanoTime();
					for( int i = 0; i < n; i++ ) {
						function.getLong( s.readSelfDelimUTF8( fbis ) );
						if ( ( i % 0xFFFFF ) == 0 ) System.out.print('.');
					}
					System.out.println();
					time += System.nanoTime();
					if ( k < 10 ) total += time;
					System.out.println( Util.format( time / 1E9 ) + "s, " + Util.format( (double)time / n ) + " ns/item" );
				}
				System.out.println( "Average: " + Util.format( total / 10E9 ) + "s, " + Util.format( total / ( 10. * n ) ) + " ns/item" );
				
				fbis.close();
				new File( file ).delete();
			}
			else {
				System.gc();
				System.gc();

				long total = 0;
				for( int k = 13; k-- != 0; ) {
					long time = -System.nanoTime();
					for( int i = 0; i < n; i++ ) {
						function.getLong( test[ i ] );
						if ( ( i % 0xFFFFF ) == 0 ) System.out.print('.');
					}
					System.out.println();
					time += System.nanoTime();
					if ( k < 10 ) total += time;
					System.out.println( Util.format( time / 1E9 ) + "s, " + Util.format( (double)time / n ) + " ns/item" );
				}
				System.out.println( "Average: " + Util.format( total / 10E9 ) + "s, " + Util.format( total / ( 10. * n ) ) + " ns/item" );
			}
		}
		else {
			System.gc();
			System.gc();
			
			long total = 0, t = -1;
			final long size = flc.size();
			for( int k = 13; k-- != 0; ) {
				final Iterator<? extends CharSequence> iterator = flc.iterator();

				long time = -System.nanoTime();
				long index;
				for( long i = 0; i < size; i++ ) {
					index = function.getLong( iterator.next() );
					t ^= index;
					if ( check && index != i ) throw new AssertionError( index + " != " + i ); 
					if ( ( i & 0xFFFFF ) == 0 ) System.err.print('.');
				}
				System.err.println();
				time += System.nanoTime();
				if ( k < 10 ) total += time;
				System.err.println( Util.format( time / 1E9 ) + "s, " + Util.format( (double)time / size ) + " ns/item" );
			}
			System.out.println( "Average: " + Util.format( total / 1E10 ) + "s, " + Util.format( total / ( 10. * size ) ) + " ns/item" );
			if ( t == 0 ) System.err.println( t );
		}
	}
}
