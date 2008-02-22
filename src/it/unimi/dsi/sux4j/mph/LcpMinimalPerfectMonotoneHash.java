package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 2.1 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.bits.Utf16TransformationStrategy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/** A minimal perfect monotone hash implementation based on fixed-size bucketing.
 * *
 * @author Sebastiano Vigna
 * @since 0.1
 */

public class LcpMinimalPerfectMonotoneHash<T> extends AbstractHash<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( LcpMinimalPerfectMonotoneHash.class );
	@SuppressWarnings("unused")
	private static final boolean DEBUG = false;
	
	/** The number of elements. */
	final protected int n;
	/** The size of a bucket. */
	final protected int bucketSize;
	/** {@link Fast#ceilLog2(int)} of {@link #bucketSize}. */
	final protected int log2BucketSize;
	/** The mask for {@link #log2BucketSize} bits. */
	final protected int bucketSizeMask;
	/** A function mapping each element to the offset inside its bucket (lowest {@link #log2BucketSize} bits) and
	 * to the length of the longest common prefix of its bucket (remaining bits). */
	final protected MWHCFunction<T> offsetLcpLength;
	/** A function mapping each longest common prefix to its bucket. */
	final protected MWHCFunction<BitVector> lcp2Bucket;
	/** The transformation strategy. */
	final protected TransformationStrategy<? super T> transform;
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final T key = (T)o;
		final long value = offsetLcpLength.getLong( key );
		return lcp2Bucket.getLong( transform.toBitVector( key ).subVector( 0, value >>> log2BucketSize ) ) * bucketSize + ( value & bucketSizeMask );
	}

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public LcpMinimalPerfectMonotoneHash( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) {

		// First of all we compute the size, either by size(), if possible, or simply by iterating.
		if ( iterable instanceof Collection ) n = ((Collection<? extends T>)iterable).size();
		else {
			int c = 0;
			// Do not add a suppression annotation--it breaks javac
			for( T o: iterable ) c++;
			n = c;
		}
		
		this.transform = transform;

		Iterator<? extends T> iterator = iterable.iterator();
		
		if ( ! iterator.hasNext() )	{
			bucketSize = bucketSizeMask = log2BucketSize = 0;
			lcp2Bucket = null;
			offsetLcpLength = null;
			return;
		}

		int t = (int)Math.ceil( 1 + HypergraphSorter.GAMMA * Math.log( 2 ) + Math.log( n ) - Math.log( 1 + Math.log( n ) ) );
		log2BucketSize = Fast.ceilLog2( t );
		bucketSize = 1 << log2BucketSize;
		bucketSizeMask = bucketSize - 1;
		
		final int numBuckets = ( n + bucketSize - 1 ) / bucketSize;
		
		LongArrayBitVector prev = LongArrayBitVector.getInstance();
		BitVector curr = null;
		int prefix, currLcp = 0;
		final BitVector[] lcp = new BitVector[ numBuckets ];
		int maxLcp = 0;
		long maxLength = 0;
		
		for( int b = 0; b < numBuckets; b++ ) {
			prev.replace( transform.toBitVector( iterator.next() ) );
			maxLength = Math.max( maxLength, prev.length() );
			currLcp = (int)prev.length();
			final int currBucketSize = Math.min( bucketSize, n - b * bucketSize );
			
			for( int i = 0; i < currBucketSize - 1; i++ ) {
				curr = transform.toBitVector( iterator.next() );
				final int cmp = prev.compareTo( curr );
				if ( cmp > 0 ) throw new IllegalArgumentException( "The list is not sorted" );
				if ( cmp == 0 ) throw new IllegalArgumentException( "The list contains duplicates" );
				
				currLcp = (int)Math.min( curr.longestCommonPrefixLength( prev ), currLcp );
				prev.replace( curr );
				
				maxLength = Math.max( maxLength, prev.length() );
			}

			lcp[ b ] = prev.subVector( 0, currLcp  ).copy();
			maxLcp = Math.max( maxLcp, currLcp );
		}
		
		
		/*BitVector[] l = lcp.clone();
		Arrays.sort( l );
		for( int i = l.length- 1; i-- != 0 ; ) if ( l[ i ].equals( l[ i + 1 ] ))throw new AssertionError();*/
		
		
		// Build function assigning each lcp to its bucket.
		lcp2Bucket = new MWHCFunction<BitVector>( Arrays.asList( lcp ), BitVectors.identity(), null, Fast.ceilLog2( numBuckets ) );

		if ( DEBUG ) {
			int p = 0;
			for( BitVector v: lcp ) System.err.println( v  + " " + v.length() );
			for( BitVector v: Arrays.asList( lcp ) ) {
				final long value = lcp2Bucket.getLong( v );
				if ( p++ != value ) {
					System.err.println( "p: " + (p-1) + "  value: " + value + " key:" + v );
					throw new AssertionError();
				}
			}
		}

		// Build function assigning the lcp length and the bucketing data to each element.
		offsetLcpLength = new MWHCFunction<T>( iterable, transform, new AbstractLongList() {
			public long getLong( int index ) {
				return lcp[ index / bucketSize ].length() << log2BucketSize | index % bucketSize; 
			}
			public int size() {
				return n;
			}
		}, log2BucketSize + Fast.ceilLog2( maxLcp ) );

		if ( DEBUG ) {
			int p = 0;
			for( T key: iterable ) {
				final long value = offsetLcpLength.getLong( key );
				if ( p++ != lcp2Bucket.getLong( transform.toBitVector( key ).subVector( 0, value >>> log2BucketSize ) ) * bucketSize + ( value & bucketSizeMask ) ) {
					System.err.println( "p: " + ( p - 1 ) 
							+ "  Key: " + key 
							+ " bucket size: " + bucketSize 
							+ " lcp " + transform.toBitVector( key ).subVector( 0, value >>> log2BucketSize ) 
							+ " lcp length: " + ( value >>> log2BucketSize ) 
							+ " bucket " + lcp2Bucket.getLong( transform.toBitVector( key ).subVector( 0, value >>> log2BucketSize ) ) 
							+ " offset: " + ( value & bucketSizeMask ) );
					throw new AssertionError();
				}
			}
		}
		
		final int lnLnN = (int)Math.ceil( Math.log( 1 + Math.log( n  ) ) );
		LOGGER.debug( "Bucket size: " + bucketSize );
		LOGGER.debug( "Forecast bit cost per element: " + ( HypergraphSorter.GAMMA + Fast.log2( bucketSize ) + Fast.log2( Math.E ) + Fast.ceilLog2( maxLength - lnLnN ) ) );
		LOGGER.debug( "Empirical bit cost per element: " + ( HypergraphSorter.GAMMA + log2BucketSize + Fast.ceilLog2( maxLcp ) + Fast.ceilLog2( numBuckets ) / (double)bucketSize + (double)transform.numBits() / n ) );
		LOGGER.debug( "Actual bit cost per element: " + (double)numBits() / n );
	}


	/** Returns the number of terms hashed.
	 *
	 * @return the number of terms hashed.
	 */
	public int size() {
		return n;
	}

	/** Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return offsetLcpLength.numBits() + lcp2Bucket.numBits() + transform.numBits();
	}

	public boolean hasTerms() {
		return false;
	}
	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MinimalPerfectHash.class.getName(), "Builds a minimal perfect monotone hash table reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read strings." ),
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to increase entropy (only available for offline construction)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new FlaggedOption( "stringFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read strings from this file (without loading them into core memory) instead of standard input." ),
			new UnflaggedOption( "table", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash table." )
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final int bufferSize = jsapResult.getInt( "bufferSize" );
		final String tableName = jsapResult.getString( "table" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );

		if ( huTucker && stringFile == null ) throw new IllegalArgumentException( "Hu-Tucker coding requires offline construction" );

		final LcpMinimalPerfectMonotoneHash<CharSequence> lcpMinimalPerfectMonotoneHash;

		if ( stringFile == null ) {
			ArrayList<MutableString> stringList = new ArrayList<MutableString>();
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.itemsName = "strings";
			final LineIterator stringIterator = new LineIterator( new FastBufferedReader( new InputStreamReader( System.in, encoding ), bufferSize ), pl );

			pl.start( "Reading strings..." );
			while( stringIterator.hasNext() ) stringList.add( stringIterator.next().copy() );
			pl.done();

			LOGGER.info( "Building minimal perfect monotone hash table..." );
			lcpMinimalPerfectMonotoneHash = new LcpMinimalPerfectMonotoneHash<CharSequence>( stringList, new Utf16TransformationStrategy() );
		}
		else {
			LOGGER.info( "Building minimal perfect monotone hash table..." );
			FileLinesCollection flc = new FileLinesCollection( stringFile, "UTF-8", zipped );
			lcpMinimalPerfectMonotoneHash = new LcpMinimalPerfectMonotoneHash<CharSequence>( flc, huTucker ? new HuTuckerTransformationStrategy( flc, true ) : new Utf16TransformationStrategy() );
		}

		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( lcpMinimalPerfectMonotoneHash, tableName );
		LOGGER.info( "Completed." );
	}
}
