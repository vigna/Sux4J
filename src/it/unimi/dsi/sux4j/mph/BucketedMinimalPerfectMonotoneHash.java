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
import it.unimi.dsi.sux4j.io.FileLinesList;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;

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
	private final BitstreamImmutableBinaryPartialTrie<BitVector> distributor;
	/** The offset of each vector into his bucket. */
	final private MWHCFunction<BitVector> offset;
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final BitVector bv = transform.toBitVector( (T)o );
		final long bucket = distributor.getLong( bv );
		return ( bucket << log2BucketSize ) + offset.getLong( bv );
	}

	public long getByBitVector( final BitVector bv ) {
		final long bucket = distributor.getLong( bv );
		return ( bucket << log2BucketSize ) + offset.getLong( bv );
	}

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public BucketedMinimalPerfectMonotoneHash( final List<? extends T> elements, final TransformationStrategy<? super T> transform ) throws IOException {

		this.transform = transform;

		n = elements.size();
		if ( n == 0 )	{
			bucketSize = bucketSizeMask = log2BucketSize = 0;
			distributor = null;
			offset = null;
			return;
		}

		long maxLength = 0;
		long totalLength = 0;
		BitVector bv;
		for( T s: elements ) {
			bv = transform.toBitVector( s );
			maxLength = Math.max( maxLength, bv.length() );
			totalLength += bv.length();
		}
		
		long averageLength = totalLength / n;
		
		int t = Fast.mostSignificantBit( (int)Math.floor( averageLength - Math.log( n ) - Math.log( averageLength - Math.log( n ) ) - 1 ) );
		final int firstbucketSize = 1 << t;
		LOGGER.debug( "First bucket size estimate: " +  firstbucketSize );
		
		final List<BitVector> bitVectors = TransformationStrategies.wrap(  elements, transform );
		
		BitstreamImmutableBinaryPartialTrie<BitVector> firstDistributor = new BitstreamImmutableBinaryPartialTrie<BitVector>( bitVectors, firstbucketSize, TransformationStrategies.identity() );

		// Reassign bucket size based on empirical estimation
		
		log2BucketSize = t - Fast.mostSignificantBit( (int)Math.ceil( n / ( firstDistributor.numBits() * Math.log( 2 ) ) ) );
		bucketSize = 1 << log2BucketSize;
		bucketSizeMask = bucketSize - 1;

		LOGGER.debug( "Second bucket size estimate: " + bucketSize );

		if ( firstbucketSize == bucketSize ) distributor = firstDistributor;
		else {
			firstDistributor = null;
			distributor = new BitstreamImmutableBinaryPartialTrie<BitVector>( bitVectors, bucketSize, TransformationStrategies.identity() );
		}
/*
		
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize / 4, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize - 2 ) );
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize / 2, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize - 1 ) );
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize * 2, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize + 1 ) );
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize * 4, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize + 2 ) );
	*/	
		
		offset = new MWHCFunction<BitVector>( TransformationStrategies.wrap( elements, transform ), TransformationStrategies.identity(), new AbstractLongList() {
			public long getLong( int index ) {
				return index % bucketSize; 
			}
			public int size() {
				return n;
			}
		}, log2BucketSize );

		
		LOGGER.debug( "Bucket size: " + bucketSize );
		LOGGER.debug( "Forecast distributor bit cost: " + ( n / bucketSize ) * ( maxLength + log2BucketSize - Math.log( n ) ) );
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
			new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "4Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read strings." ),
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 bit encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to increase entropy (only available for offline construction)." ),
			new FlaggedOption( "stringFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'o', "offline", "Read strings from this file (without loading them into core memory) instead of standard input." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash function." )
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final int bufferSize = jsapResult.getInt( "bufferSize" );
		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );

		if ( huTucker && stringFile == null ) throw new IllegalArgumentException( "Hu-Tucker coding requires offline construction" );

		final BucketedMinimalPerfectMonotoneHash<? extends CharSequence> minimalPerfectMonotoneHash;
		final TransformationStrategy<CharSequence> transformationStrategy = iso? TransformationStrategies.prefixFreeIso() : TransformationStrategies.prefixFreeUtf16();

		LOGGER.info( "Building minimal perfect monotone hash function..." );
		FileLinesList fileLinesList = new FileLinesList( stringFile, encoding.name(), bufferSize );
		minimalPerfectMonotoneHash = new BucketedMinimalPerfectMonotoneHash<CharSequence>( fileLinesList, huTucker ? new HuTuckerTransformationStrategy( fileLinesList, true ) : transformationStrategy );

		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( minimalPerfectMonotoneHash, functionName );
		LOGGER.info( "Completed." );
	}
}
