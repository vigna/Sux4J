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
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.scratch.BitStreamImmutableBinaryTrie;

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

public class BucketedMinimalPerfectMonotoneHash<T> extends AbstractHash<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( BucketedMinimalPerfectMonotoneHash.class );
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
	/** A distributor assigning keys to buckets. */
	private BitStreamImmutableBinaryTrie<BitVector> distributor;
	/** The offset of each vector into his bucket. */
	final private MWHCFunction<BitVector> offset;
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final BitVector bv = transform.toBitVector( (T)o );
		final long bucket = distributor.getLong( bv );
		return ( bucket << log2BucketSize ) + offset.getLong( bv );
	}

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public BucketedMinimalPerfectMonotoneHash( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) throws IOException {

		// First of all we compute the size, either by size(), if possible, or simply by iterating.
		this.transform = transform;

		Iterator<? extends T> iterator = iterable.iterator();
		
		if ( ! iterator.hasNext() )	{
			n = bucketSize = bucketSizeMask = log2BucketSize = 0;
			distributor = null;
			offset = null;
			return;
		}

		ObjectArrayList<BitVector> vectors = new ObjectArrayList<BitVector>();
		
		long maxLength = 0;
		long totalLength = 0;
		BitVector bv;
		while( iterator.hasNext() ) {
			bv = transform.toBitVector( iterator.next() ).copy();
			maxLength = Math.max( maxLength, bv.length() );
			totalLength += bv.length();
			vectors.add( bv );
		}
		
		n = vectors.size();
		long averageLength = totalLength / n;
		
		int t = Fast.mostSignificantBit( (int)Math.floor( averageLength - Math.log( n ) - Math.log( averageLength - Math.log( n ) ) - 1 ) );
		log2BucketSize = t <= 6 ? t : t - 3;
		bucketSize = 1 << log2BucketSize;
		bucketSizeMask = bucketSize - 1;
		
		distributor = new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize, TransformationStrategies.identity() );

		vectors = null;
		
		offset = new MWHCFunction<BitVector>( TransformationStrategies.wrap( iterable, transform ), TransformationStrategies.identity(), new AbstractLongList() {
			public long getLong( int index ) {
				return index % bucketSize; 
			}
			public int size() {
				return n;
			}
		}, log2BucketSize );

		
		LOGGER.debug( "Bucket size: " + bucketSize );
		LOGGER.debug( "Forecast distributor bit cost: " + ( n / bucketSize ) * ( maxLength + log2BucketSize - Math.log( n ) ) );
		LOGGER.debug( "Empirical forecast distributor bit cost: " + ( n / bucketSize ) * ( averageLength + log2BucketSize - Math.log( n ) ) );
		LOGGER.debug( "Actual distributor bit cost: " + distributor.numBits() );
		LOGGER.debug( "Forecast bit cost per element: " + ( HypergraphSorter.GAMMA + Fast.log2( Math.E ) + 2 * Fast.log2( maxLength - Fast.log2( n ) ) ) );
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
		return distributor.numBits() + offset.numBits() + transform.numBits();
	}

	public boolean hasTerms() {
		return false;
	}
	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MinimalPerfectHash.class.getName(), "Builds a minimal perfect monotone hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read strings." ),
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to increase entropy (only available for offline construction)." ),
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
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );

		if ( huTucker && stringFile == null ) throw new IllegalArgumentException( "Hu-Tucker coding requires offline construction" );

		final BucketedMinimalPerfectMonotoneHash<CharSequence> minimalPerfectMonotoneHash;

		if ( stringFile == null ) {
			ArrayList<MutableString> stringList = new ArrayList<MutableString>();
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.itemsName = "strings";
			final LineIterator stringIterator = new LineIterator( new FastBufferedReader( new InputStreamReader( System.in, encoding ), bufferSize ), pl );

			pl.start( "Reading strings..." );
			while( stringIterator.hasNext() ) stringList.add( stringIterator.next().copy() );
			pl.done();

			LOGGER.info( "Building minimal perfect monotone hash function..." );
			minimalPerfectMonotoneHash = new BucketedMinimalPerfectMonotoneHash<CharSequence>( stringList, TransformationStrategies.prefixFreeUtf16() );
		}
		else {
			LOGGER.info( "Building minimal perfect monotone hash function..." );
			FileLinesCollection flc = new FileLinesCollection( stringFile, "UTF-8", zipped );
			minimalPerfectMonotoneHash = new BucketedMinimalPerfectMonotoneHash<CharSequence>( flc, huTucker ? new HuTuckerTransformationStrategy( flc, true ) : TransformationStrategies.prefixFreeUtf16() );
		}

		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( minimalPerfectMonotoneHash, functionName );
		LOGGER.info( "Completed." );
	}
}
