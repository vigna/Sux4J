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
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.sux4j.util.TwoSizesLongBigList;

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

public class SlicedMinimalPerfectMonotoneHash<T> extends AbstractHashFunction<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( SlicedMinimalPerfectMonotoneHash.class );
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
	private MinimalPerfectHashFunction<BitVector> minimalPerfectHash;
	private TwoSizesLongBigList offsets;
	private int bucketShift;
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final BitVector bitVector = transform.toBitVector( (T)o ).fast();
		long bucket = Long.reverse( bitVector.getLong( 0, Math.min( Long.SIZE, bitVector.length() ) ) ) >>> bucketShift;
		return firstInBucket.getLong( bucket ) + offsets.getLong( minimalPerfectHash.getLong( bitVector ) );
	}

	@SuppressWarnings("unchecked")
	public long getByBitVector( final BitVector bitVector ) {
		long bucket = Long.reverse( bitVector.getLong( 0, Math.min( Long.SIZE, bitVector.length() ) ) ) >>> bucketShift;
		return firstInBucket.getLong( bucket ) + offsets.getLong( minimalPerfectHash.getLong( bitVector ) );
	}

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public SlicedMinimalPerfectMonotoneHash( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) {

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
		minimalPerfectHash = new MinimalPerfectHashFunction<BitVector>( TransformationStrategies.wrap( iterable, transform ), TransformationStrategies.identity() );
		int offset[] = new int[ n ];
		
		iterator = iterable.iterator();

		pl.start( "Computing offsets..." );

		for( int i = 0; i < n; i++ ) {
			curr = transform.toBitVector( iterator.next() ).fast();
			bucket = Long.reverse( curr.getLong( 0, Math.min( Long.SIZE, curr.length() ) ) ) >>> bucketShift;
			offset[ (int)minimalPerfectHash.getLong( curr ) ] = (int)( i - bucketSize[ (int)bucket ] );
			pl.lightUpdate();
		}
		
		pl.done();
		
		offsets = new TwoSizesLongBigList( IntArrayList.wrap( offset ) );
		offset = null;
		firstInBucket = new EliasFanoMonotoneLongBigList( LongArrayList.wrap( bucketSize ) );

/*		iterator = iterable.iterator();
		for( int i = 0; i < n; i++ ) {
			curr = transform.toBitVector( iterator.next() ).fast();
			bucket = Long.reverse( curr.getLong( 0, Math.min( Long.SIZE, curr.length() ) ) ) >>> bucketShift;
			if ( i != offsets.getLong( (int)minimalPerfectHash.getLong( curr  ) ) + firstInBucket.getLong( (int)bucket ) ) throw new AssertionError();
		}
*/	
		LOGGER.debug(  "Cost per element of hashing: " + (double)minimalPerfectHash.numBits() / n );
		LOGGER.debug(  "Cost per element of offsets: " + (double)offsets.numBits() / n );
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
		return minimalPerfectHash.numBits() + offsets.numBits() + firstInBucket.numBits() + transform.numBits();
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

		final SlicedMinimalPerfectMonotoneHash<CharSequence> lcpMinimalPerfectMonotoneHash;
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
			lcpMinimalPerfectMonotoneHash = new SlicedMinimalPerfectMonotoneHash<CharSequence>( stringList, transformationStrategy );
		}
		else {
			LOGGER.info( "Building minimal perfect monotone hash function..." );
			FileLinesCollection flc = new FileLinesCollection( stringFile, encoding.name(), zipped );
			lcpMinimalPerfectMonotoneHash = new SlicedMinimalPerfectMonotoneHash<CharSequence>( flc, huTucker ? new HuTuckerTransformationStrategy( flc, true ) : transformationStrategy );
		}

		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( lcpMinimalPerfectMonotoneHash, functionName );
		LOGGER.info( "Completed." );
	}
}
