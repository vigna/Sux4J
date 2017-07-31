package it.unimi.dsi.sux4j.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

import com.google.common.base.Charsets;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public class ByteArrayFunctionSpeedTest {

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP( ByteArrayFunctionSpeedTest.class.getName(), "Test the speed of a function on byte arrays. Performs thirteen repetitions: the first three ones are warmup, and the average of the remaining ten is printed on standard output. The detailed results are logged to standard error.",
				new Parameter[] {
					new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read terms." ),
					new FlaggedOption( "n", JSAP.INTSIZE_PARSER, "1000000", JSAP.NOT_REQUIRED, 'n',  "number-of-strings", "The (maximum) number of strings used for random testing." ),
					new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding." ),
					new FlaggedOption( "save", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "save", "In case of a random test, save to this file the strings used." ),
					new Switch( "zipped", 'z', "zipped", "The term list is compressed in gzip format." ),
					new Switch( "random", 'r', "random", "Test a shuffled subset of strings." ),
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
		
		if ( jsapResult.userSpecified( "n" ) && ! random ) throw new IllegalArgumentException( "The number of string is meaningful for random tests only" );
		if ( save != null && ! random ) throw new IllegalArgumentException( "You can save test string only for random tests" );
		
		@SuppressWarnings("unchecked")
		final Object2LongFunction<byte[]> function = (Object2LongFunction<byte[]>)BinIO.loadObject( functionName );
		@SuppressWarnings("resource")
		final FastBufferedInputStream fbis = new FastBufferedInputStream( zipped ? new GZIPInputStream( new FileInputStream( termFile ) ) : new FileInputStream( termFile ) ); 
		final Collection<byte[]> lines = new AbstractCollection<byte[]>() {
			@Override
			public Iterator<byte[]> iterator() {
				final byte[] buffer = new byte[ 16384 ];
				try {
					fbis.position( 0 );
				}
				catch ( IOException e ) {
					throw new RuntimeException( e.getMessage(), e );
				}
				return new ObjectIterator<byte[]>() {
					boolean toRead = true;
					int read;

					@Override
					public boolean hasNext() {
						if ( toRead == false ) return read != -1;
						toRead = false;
						try {
							read = fbis.readLine( buffer, FastBufferedInputStream.ALL_TERMINATORS );
						}
						catch ( IOException e ) {
							throw new RuntimeException( e.getMessage(), e );
						}
						if ( read == buffer.length ) throw new IllegalStateException();
						return read != -1;
					}

					@Override
					public byte[] next() {
						if ( ! hasNext() ) throw new NoSuchElementException();
						toRead = true;
						return Arrays.copyOf( buffer, read );
					}
				};
			}

			@Override
			public int size() {
				throw new UnsupportedOperationException();
			}
		};
		
		long size = 0;
		for( Iterator<byte[]> ii = lines.iterator(); ii.hasNext(); size++ ) ii.next();

		if ( random ) {
			final int n = (int)Math.min( maxStrings, size );
			byte[][] test = new byte[ n ][];
			final int step = (int)( size / n ) - 1;
			final Iterator<byte[]> iterator = lines.iterator();
			for( int i = 0; i < n; i++ ) {
				test[ i ] = iterator.next().clone();
				for( int j = step; j-- != 0; ) iterator.next();
			}
			Collections.shuffle( Arrays.asList( test ) );

			if ( save != null ) {
				final PrintStream ps = new PrintStream( save, encoding.name() );
				for( byte[] s: test ) ps.println( new String( s, Charsets.ISO_8859_1 ) );
				ps.close();
			}

			FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
			for( byte[] s: test ) {
				fbaos.write( s );
				fbaos.write( 10 );
			}
			fbaos.close();
			test = null;

			System.gc();
			System.gc();
	
			byte[] buffer = new byte[ 16384 ];
			long total = 0, t = -1;
			for( int k = 13; k-- != 0; ) {
				fbis.position( 0 );
				long time = -System.nanoTime();
				for( int i = 0; i < n; i++ ) {
					final int read = fbis.readLine( buffer );
					t ^= function.getLong( Arrays.copyOf( buffer, read ) );
					if ( ( i % 0xFFFFF ) == 0 ) System.err.print('.');
				}
				System.err.println();
				time += System.nanoTime();
				if ( k < 10 ) total += time;
				System.err.println( Util.format( time / 1E9 ) + "s, " + Util.format( (double)time / n ) + " ns/item" );
			}
			System.out.println( "Average: " + Util.format( total / 1E10 ) + "s, " + Util.format( total / ( 10. * n ) ) + " ns/item" );
			if ( t == 0 ) System.err.println( t );
		}
		else {
			System.gc();
			System.gc();
			
			long total = 0, t = -1;
			for( int k = 13; k-- != 0; ) {
				final Iterator<byte[]> iterator = lines.iterator();

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
