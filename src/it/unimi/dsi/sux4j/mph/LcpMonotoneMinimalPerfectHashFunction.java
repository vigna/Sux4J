package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2010 Sebastiano Vigna 
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

import static it.unimi.dsi.bits.Fast.log2;
import static it.unimi.dsi.sux4j.mph.HypergraphSorter.GAMMA;
import static java.lang.Math.E;
import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.io.OfflineIterable;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
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
 * longest common prefixes as distributors.
 */

public class LcpMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Size64, Serializable {
    public static final long serialVersionUID = 2L;
	private static final Logger LOGGER = Util.getLogger( LcpMonotoneMinimalPerfectHashFunction.class );
	private static final boolean DEBUG = false;
	private static final boolean ASSERTS = false;
	
	/** The number of elements. */
	final protected long n;
	/** The size of a bucket. */
	final protected int bucketSize;
	/** {@link Fast#ceilLog2(int)} of {@link #bucketSize}. */
	final protected int log2BucketSize;
	/** The mask for {@link #log2BucketSize} bits. */
	final protected int bucketSizeMask;
	/** A function mapping each element to the offset inside its bucket (lowest {@link #log2BucketSize} bits) and
	 * to the length of the longest common prefix of its bucket (remaining bits). */
	final protected MWHCFunction<BitVector> offsetLcpLength;
	/** A function mapping each longest common prefix to its bucket. */
	final protected MWHCFunction<BitVector> lcp2Bucket;
	/** The transformation strategy. */
	final protected TransformationStrategy<? super T> transform;
	private long seed;
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final BitVector bitVector = transform.toBitVector( (T)o ).fast();
		final long[] triple = new long[ 3 ];
		Hashes.jenkins( bitVector, seed, triple );
		final long value = offsetLcpLength.getLongByTriple( triple );
		final long prefix = value >>> log2BucketSize; 
		if ( prefix > bitVector.length() ) return defRetValue;
		return ( lcp2Bucket.getLong( bitVector.subVector( 0, prefix ) ) << log2BucketSize ) + ( value & bucketSizeMask );
	}

	public LcpMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) throws IOException {
		this( iterable, -1, transform );
	}

		@SuppressWarnings("unused")
	public LcpMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> iterable, final int numElements, final TransformationStrategy<? super T> transform ) throws IOException {
		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayFreeMemory = true;
		this.transform = transform;
		final Random r = new Random();

		if ( numElements == -1 ) {
			if ( iterable instanceof Size64 ) n = ((Size64)iterable).size64();
			else if ( iterable instanceof Collection ) n = ((Collection<?>)iterable).size();
			else {
				int c = 0;
				for( T dummy: iterable ) c++;
				n = c;
			}
		}
		else n = numElements;
		
		defRetValue = -1; // For the very few cases in which we can decide

		if ( n == 0 ) {
			bucketSize = bucketSizeMask = log2BucketSize = 0;
			lcp2Bucket = null;
			offsetLcpLength = null;
			return;
		}
		
		final int theoreticalBucketSize = (int)Math.ceil( 1 + HypergraphSorter.GAMMA * Math.log( 2 ) + Math.log( n ) - Math.log( 1 + Math.log( n ) ) );
		log2BucketSize = Fast.ceilLog2( theoreticalBucketSize );
		bucketSize = 1 << log2BucketSize;
		bucketSizeMask = bucketSize - 1;
		LOGGER.debug( "Bucket size: " + bucketSize );

		final long numBuckets = ( n + bucketSize - 1 ) / bucketSize;
		
		LongArrayBitVector prev = LongArrayBitVector.getInstance();
		LongArrayBitVector curr = LongArrayBitVector.getInstance();
		int currLcp = 0;
		final OfflineIterable<BitVector, LongArrayBitVector> lcps = new OfflineIterable<BitVector, LongArrayBitVector>( BitVectors.OFFLINE_SERIALIZER, LongArrayBitVector.getInstance() );
		final int[][] lcpLengths = IntBigArrays.newBigArray( numBuckets );
		int maxLcp = 0;
		long maxLength = 0;

		pl.expectedUpdates = n;
		
		final ChunkedHashStore<BitVector> chunkedHashStore = new ChunkedHashStore<BitVector>( TransformationStrategies.identity(), pl );
		chunkedHashStore.reset( r.nextLong() );

		pl.start( "Scanning collection..." );
		
		final Iterator<? extends T> iterator = iterable.iterator();
		for( int b = 0; b < numBuckets; b++ ) {
			prev.replace( transform.toBitVector( iterator.next() ) );
			chunkedHashStore.add( prev );
			pl.lightUpdate();
			maxLength = Math.max( maxLength, prev.length() );
			currLcp = (int)prev.length();
			final int currBucketSize = (int)Math.min( bucketSize, n - b * bucketSize );
			
			for( int i = 0; i < currBucketSize - 1; i++ ) {
				curr.replace( transform.toBitVector( iterator.next() ) );
				chunkedHashStore.add( curr );
				pl.lightUpdate();
				final int prefix = (int)curr.longestCommonPrefixLength( prev );
				if ( prefix == prev.length() && prefix == curr.length()  ) throw new IllegalArgumentException( "The input bit vectors are not distinct" );
				if ( prefix == prev.length() || prefix == curr.length() ) throw new IllegalArgumentException( "The input bit vectors are not prefix-free" );
				if ( prev.getBoolean( prefix ) ) throw new IllegalArgumentException( "The input bit vectors are not lexicographically sorted" );
				
				currLcp = Math.min( prefix, currLcp );
				prev.replace( curr );
				
				maxLength = Math.max( maxLength, prev.length() );
			}

			lcps.add( prev.subVector( 0, currLcp  ) );
			IntBigArrays.set( lcpLengths, b, currLcp );
			maxLcp = Math.max( maxLcp, currLcp );
		}
		
		pl.done();
		
		if ( ASSERTS ) {
			ObjectOpenHashSet<BitVector> s = new ObjectOpenHashSet<BitVector>();
			for( LongArrayBitVector bv: lcps ) s.add( bv.copy() );
			assert s.size() == lcps.size() : s.size() + " != " + lcps.size(); // No duplicates.
		}

		LOGGER.info( "Generating the map from keys to LCP lengths and offsets..." );
		// Build function assigning the lcp length and the bucketing data to each element.
		offsetLcpLength = new MWHCFunction<BitVector>( TransformationStrategies.wrap( iterable, transform ), TransformationStrategies.identity(), chunkedHashStore, new AbstractLongBigList() {
			public long getLong( long index ) {
				return IntBigArrays.get( lcpLengths, index >>> log2BucketSize ) << log2BucketSize | index & bucketSizeMask; 
			}
			public long size64() {
				return n;
			}
		}, log2BucketSize + Fast.length( maxLcp ) );
		
		LOGGER.info( "Generating the map from LCPs to buckets..." );
		// Build function assigning each lcp to its bucket.
		lcp2Bucket = new MWHCFunction<BitVector>( lcps, TransformationStrategies.identity() );

		if ( DEBUG ) {
			int p = 0;
			for( BitVector v: lcps ) System.err.println( v  + " " + v.length() );
			for( BitVector v: lcps ) {
				final long value = lcp2Bucket.getLong( v );
				if ( p++ != value ) {
					System.err.println( "p: " + (p-1) + "  value: " + value + " key:" + v );
					throw new AssertionError();
				}
			}
		}

		lcps.close();
		this.seed = chunkedHashStore.seed();
		chunkedHashStore.close();
		
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
		
		LOGGER.debug( "Forecast bit cost per element: " + ( log2( E ) + GAMMA - log2( log2( E ) ) + log2( 1 + log2( n ) ) + log2( maxLength - log2( 1 + log2( n ) ) ) ) ); 
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / n );
	}


	public long size64() {
		return n;
	}

	@Deprecated
	public int size() {
		return (int)Math.min( Integer.MAX_VALUE, n );
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

		final SimpleJSAP jsap = new SimpleJSAP( LcpMonotoneMinimalPerfectHashFunction.class.getName(), "Builds an LCP-based monotone minimal perfect hash function reading a newline-separated list of strings.",
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

		BinIO.storeObject( new LcpMonotoneMinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy ), functionName );
		LOGGER.info( "Completed." );
	}
}
