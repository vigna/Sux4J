package it.unimi.dsi.sux4j.scratch;

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
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.AbstractHashFunction;
import it.unimi.dsi.sux4j.mph.MWHCFunction;
import it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
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

public class SlicedMinimalPerfectMonotoneHash2<T> extends AbstractHashFunction<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( SlicedMinimalPerfectMonotoneHash2.class );
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
	/** The transformation strategy. */
	final protected TransformationStrategy<? super T> transform;
	private EliasFanoMonotoneLongBigList firstInBucket;
	private int bucketShift;
	private MWHCFunction<BitVector>[] offsets;
	private long offsetCost;
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final BitVector bitVector = transform.toBitVector( (T)o ).fast();
		long bucket = Long.reverse( bitVector.getLong( 0, Math.min( Long.SIZE, bitVector.length() ) ) ) >>> bucketShift;
		long first = firstInBucket.getLong( bucket );
		int size = (int)( firstInBucket.getLong( bucket + 1 ) - first );
		int r = Fast.ceilLog2( size );
		if ( r == -1 ) return -1;
		if ( r == 0 ) return first;
		return firstInBucket.getLong( bucket ) + offsets[ r ].getLong( bitVector );
	}

	@SuppressWarnings({ "unused", "unchecked" }) // TODO: move it to the first for loop when javac has been fixed
	public SlicedMinimalPerfectMonotoneHash2( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) {

		final ProgressLogger pl = new ProgressLogger( LOGGER );

		this.transform = transform;

		Iterator<? extends T> iterator = iterable.iterator();
		
		if ( ! iterator.hasNext() )	{
			n = bucketSize = bucketSizeMask = log2BucketSize = 0;
			return;
		}

		int logU = 0;
		int c = 0;
		
		while( iterator.hasNext() ) {
			logU = Math.max( logU, (int)transform.toBitVector( iterator.next() ).length() );
			c++;
		}
		
		n = c;
		
		BitVector curr = null;

		LOGGER.info( "Log u:" + logU );
		
		log2BucketSize = logU - Fast.mostSignificantBit( n ) + 1;
		bucketSize = 1 << log2BucketSize;
		bucketSizeMask = bucketSize - 1;

		LOGGER.info( "Bucket size:" + bucketSize );
		LOGGER.info( "Number of buckets:" + ( 1 << ( logU - log2BucketSize ) ) );

		long[] bucketSize = new long[ 1 << ( logU - log2BucketSize ) + 1 ];
		
		iterator = iterable.iterator();

		pl.expectedUpdates = n;
		pl.start( "Scanning collection..." );

		long bucket, prevBucket = -1;
		bucketShift = Long.SIZE - ( logU - log2BucketSize );
		for( int i = 0; i < n; i++ ) {
			curr = transform.toBitVector( iterator.next() );
			bucket = Long.reverse( curr.getLong( 0, Math.min( Long.SIZE, curr.length() ) ) ) >>> bucketShift;
			if ( bucket < prevBucket ) throw new IllegalArgumentException( "Keys are not sorted" );
			bucketSize[ (int)bucket + 1 ]++;
			pl.lightUpdate();
			prevBucket = bucket;
		}

		pl.done();
		
		for( int i = 1; i < bucketSize.length; i++ ) bucketSize[ i ] += bucketSize[ i - 1 ];
		int offset[] = new int[ n ];
		
		iterator = iterable.iterator();

		pl.start( "Computing offsets..." );

		ObjectArrayList<BitVector>[] keys = new ObjectArrayList[ log2BucketSize ];
		LongArrayList values[] = new LongArrayList[ log2BucketSize ];
		for( int i = 0; i < log2BucketSize; i++ ) {
			keys[ i ] = new ObjectArrayList<BitVector>();
			values[ i ] = new LongArrayList();
		}
		
		for( int i = 0; i < n; i++ ) {
			curr = transform.toBitVector( iterator.next() ).fast();
			bucket = Long.reverse( curr.getLong( 0, Math.min( Long.SIZE, curr.length() ) ) ) >>> bucketShift;
			final int size = (int)( bucketSize[ (int)( bucket + 1 ) ] - bucketSize[ (int)bucket ] );
			if ( size > 1 ) {
				int r = Fast.ceilLog2( size );
				keys[ r ].add( curr );
				values[ r ].add( (int)( i - bucketSize[ (int)bucket ] ) );
			}
			pl.lightUpdate();
		}
		
		offsets = new MWHCFunction[ log2BucketSize ];
		long offsetCost = 0;
		for( int i = 1; i < log2BucketSize; i++ ) {
			if ( ! values[ i ].isEmpty() ) {
				offsets[ i ] = new MWHCFunction<BitVector>( keys[ i ], TransformationStrategies.identity(), values[ i ], i );
				offsetCost += offsets[ i ].numBits();
			}
		}
		
		this.offsetCost = offsetCost;
		pl.done();
		
		offset = null;
		firstInBucket = new EliasFanoMonotoneLongBigList( LongArrayList.wrap( bucketSize ) );

/*		iterator = iterable.iterator();
		for( int i = 0; i < n; i++ ) {
			curr = transform.toBitVector( iterator.next() ).fast();
			bucket = Long.reverse( curr.getLong( 0, Math.min( Long.SIZE, curr.length() ) ) ) >>> bucketShift;
			if ( i != offsets.getLong( (int)minimalPerfectHash.getLong( curr  ) ) + firstInBucket.getLong( (int)bucket ) ) throw new AssertionError();
		}
*/	
		for( int i = 1; i < log2BucketSize; i++ ) if ( offsets[ i ] != null ) LOGGER.debug( "Cost per element of offsets of width " + i + " :" + (double)offsets[ i ].numBits() / n );
		LOGGER.debug(  "Cost per element of offsets: " + (double)offsetCost / n );
		LOGGER.debug(  "Cost per element of prefix sums: " + (double)firstInBucket.numBits() / n );
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
		return offsetCost + firstInBucket.numBits() + transform.numBits();
	}

	public boolean hasTerms() {
		return false;
	}
	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MinimalPerfectHashFunction.class.getName(), "Builds a minimal perfect monotone hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read strings." ),
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to increase entropy (only available for offline construction)." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 bit encoding." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new FlaggedOption( "stringFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read strings from this file (without loading them into core memory) instead of standard input." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash function." )
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final int bufferSize = jsapResult.getInt( "bufferSize" );
		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );

		if ( huTucker && stringFile == null ) throw new IllegalArgumentException( "Hu-Tucker coding requires offline construction" );

		final SlicedMinimalPerfectMonotoneHash2<CharSequence> lcpMinimalPerfectMonotoneHash;
		final TransformationStrategy<CharSequence> transformationStrategy = iso? TransformationStrategies.prefixFreeIso() : TransformationStrategies.prefixFreeUtf16();

		if ( stringFile == null ) {
			ArrayList<MutableString> stringList = new ArrayList<MutableString>();
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.itemsName = "strings";
			final LineIterator stringIterator = new LineIterator( new FastBufferedReader( new InputStreamReader( System.in, encoding ), bufferSize ), pl );

			pl.start( "Reading strings..." );
			while( stringIterator.hasNext() ) stringList.add( stringIterator.next().copy() );
			pl.done();

			LOGGER.info( "Building minimal perfect monotone hash function..." );
			lcpMinimalPerfectMonotoneHash = new SlicedMinimalPerfectMonotoneHash2<CharSequence>( stringList, transformationStrategy );
		}
		else {
			LOGGER.info( "Building minimal perfect monotone hash function..." );
			FileLinesCollection flc = new FileLinesCollection( stringFile, encoding.name(), zipped );
			lcpMinimalPerfectMonotoneHash = new SlicedMinimalPerfectMonotoneHash2<CharSequence>( flc, huTucker ? new HuTuckerTransformationStrategy( flc, true ) : transformationStrategy );
		}

		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( lcpMinimalPerfectMonotoneHash, functionName );
		LOGGER.info( "Completed." );
	}
}
