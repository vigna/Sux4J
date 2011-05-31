package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2011 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.SparseRank;
import it.unimi.dsi.sux4j.bits.SparseSelect;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.util.XorShiftStarRandom;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Random;
import java.util.zip.GZIPInputStream;

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

/** A monotone minimal perfect hash implementation based on fixed-size bucketing that uses 
 * a {@linkplain PaCoTrieDistributor partial compacted binary trie (PaCo trie)} as distributor.
 */

public class VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable, Size64 {
    public static final long serialVersionUID = 2L;
	private static final Logger LOGGER = Util.getLogger( VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction.class );
	private static final boolean ASSERTS = false;
	
	/** The number of elements. */
	private final long size;
	/** The size of a bucket. */
	private final int bucketSize;
	/** {@link Fast#ceilLog2(int)} of {@link #bucketSize}. */
	private final int log2BucketSize;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A PaCo trie assigning keys to buckets. */
	private final VLPaCoTrieDistributor<BitVector> distributor;
	/** The offset of each element into his bucket. */
	private final MWHCFunction<BitVector> offset;
	private SparseSelect select;
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		if ( size == 0 ) return defRetValue;
		final BitVector bv = transform.toBitVector( (T)o ).fast();
		final long bucket = distributor.getLong( bv );
		return ( bucket == 0 ? 0 : select.select( bucket - 1 ) ) + offset.getLong( bv );
	}

	/** Creates a new PaCo-trie-based monotone minimal perfect hash function using the given
	 * elements and transformation strategy. 
	 * 
	 * @param elements the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 */
	public VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) throws IOException {

		this.transform = transform;
		defRetValue = -1; // For the very few cases in which we can decide

		long maxLength = 0;
		long totalLength = 0;
		BitVector bv;
		final Random random = new XorShiftStarRandom();
		ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayFreeMemory = true;
		pl.itemsName = "keys";
		
		pl.start( "Creating chunked hash store..." );
		final ChunkedHashStore<BitVector> chunkedHashStore = new ChunkedHashStore<BitVector>( TransformationStrategies.identity() );
		chunkedHashStore.reset( random.nextLong() );
		for( T s: elements ) {
			bv = transform.toBitVector( s );
			chunkedHashStore.add( bv );
			maxLength = Math.max( maxLength, bv.length() );
			totalLength += bv.length();
			pl.lightUpdate();
		}
		
		pl.done();
		
		size = chunkedHashStore.size();
		
		if ( size == 0 )	{
			bucketSize = log2BucketSize = 0;
			distributor = null;
			offset = null;
			return;
		}

		final long averageLength = ( totalLength + size - 1 ) / size;
		
		int t = Fast.mostSignificantBit( (int)Math.floor( averageLength - Math.log( size ) - Math.log( averageLength - Math.log( size ) ) - 1 ) );
		final int firstbucketSize = 1 << t;
		LOGGER.debug( "First bucket size estimate: " +  firstbucketSize );
		
		final Iterable<BitVector> bitVectors = TransformationStrategies.wrap(  elements, transform );
		
		VLPaCoTrieDistributor<BitVector> firstDistributor = new VLPaCoTrieDistributor<BitVector>( bitVectors, size, firstbucketSize, TransformationStrategies.identity() );

		if ( firstDistributor.numBits() == 0 || firstbucketSize >= size ) log2BucketSize = t;
		else {
			// Reassign bucket size based on empirical estimation
			log2BucketSize = t - Fast.mostSignificantBit( (int)Math.ceil( size / ( firstDistributor.numBits() * Math.log( 2 ) ) ) );
		}
		
		bucketSize = 1 << log2BucketSize;
		LOGGER.debug( "Second bucket size estimate: " + bucketSize );

		if ( firstbucketSize == bucketSize ) distributor = firstDistributor;
		else {
			firstDistributor = null;
			distributor = new VLPaCoTrieDistributor<BitVector>( bitVectors, size, bucketSize, TransformationStrategies.identity() );
		}

		/*
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize / 4, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize - 2 ) );
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize / 2, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize - 1 ) );
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize * 2, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize + 1 ) );
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize * 4, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize + 2 ) );
		*/	
		
		LOGGER.info( "Bucket size: " + bucketSize );

		final SparseRank sparseRank;
		if ( size > 2 * bucketSize ) {
			sparseRank = new SparseRank( distributor.offset.getLong( distributor.offset.size64() - 1 ) + 1, distributor.offset.size64(), distributor.offset.iterator() );
			if ( ASSERTS ) {
				long i = 0;
				for( BitVector b: bitVectors ) {
					final long d = distributor.getLong( b );
					assert sparseRank.rank( i ) == d : "At " + i + ": " + sparseRank.rank( i ) + " != " + d;
					i++;
				}
			}

			select = sparseRank.getSelect();
		}
		else {
			sparseRank = null;
			select = null;
		}
		
		if ( size > 0 ) {
			offset = new MWHCFunction<BitVector>( bitVectors, TransformationStrategies.identity(), chunkedHashStore, new AbstractLongBigList() {
				public long getLong( long index ) {
					final long rank = sparseRank == null ? 0 : sparseRank.rank( index );
					if ( ASSERTS ) {
						assert rank == 0 || distributor.offset.getLong( rank - 1 ) <= index : distributor.offset.getLong( rank - 1 )  + " >= " + index + "(rank=" + rank + ")";
						assert rank == 0 && index < bucketSize * 2 || rank > 0 && index - distributor.offset.getLong( rank - 1 ) < bucketSize * 2;
					}
					return rank == 0 ? index : index - distributor.offset.getLong( rank - 1 ); 
				}
				public long size64() {
					return size;
				}
			}, log2BucketSize + 1 );

		}
		else offset = null;

		LOGGER.debug( "Forecast distributor bit cost: " + ( size / bucketSize ) * ( maxLength + log2BucketSize - Math.log( size ) ) );
		LOGGER.debug( "Actual distributor bit cost: " + distributor.numBits() );
		LOGGER.debug( "Forecast bit cost per element: " + ( HypergraphSorter.GAMMA + Fast.log2( Math.E ) - Fast.log2( Fast.log2( Math.E ) ) + Fast.log2( maxLength - Fast.log2( size ) ) ) );
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / size );
		
	}

	public long size64() {
		return size;
	}

	public long numBits() {
		return distributor.numBits() + ( offset == null ? 0 : offset.numBits() ) + transform.numBits() + ( select == null ? 0 : select.numBits() );
	}
	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction.class.getName(), "Builds an PaCo trie-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised monotone minimal perfect hash function." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );

		final Collection<MutableString> collection;
		if ( "-".equals( stringFile ) ) {
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.start( "Loading strings..." );
			collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
			pl.done();
		}
		else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
		final TransformationStrategy<CharSequence> transformationStrategy = huTucker 
			? new HuTuckerTransformationStrategy( collection, true )
			: iso
				? TransformationStrategies.prefixFreeIso() 
				: TransformationStrategies.prefixFreeUtf16();

		BinIO.storeObject( new VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy ), functionName );
		LOGGER.info( "Completed." );
	}
}
