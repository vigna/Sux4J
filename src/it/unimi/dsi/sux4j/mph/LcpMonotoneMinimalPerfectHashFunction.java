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
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
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

public class LcpMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( LcpMonotoneMinimalPerfectHashFunction.class );
	@SuppressWarnings("unused")
	private static final boolean DEBUG = false;
	private static final boolean ASSERTS = false;
	
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
		return lcp2Bucket.getLong( bitVector.subVector( 0, prefix ) ) * bucketSize + ( value & bucketSizeMask );
	}

	@SuppressWarnings("unused")
	public LcpMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) throws IOException {

		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayFreeMemory = true;
		this.transform = transform;
		final Random r = new Random();

		if ( iterable instanceof Collection ) n = ((Collection<?>)iterable).size();
		else {
			int c = 0;
			for( T dummy: iterable ) c++;
			n = c;
		}
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
		
		final int numBuckets = ( n + bucketSize - 1 ) / bucketSize;
		
		LongArrayBitVector prev = LongArrayBitVector.getInstance();
		BitVector curr = null;
		int currLcp = 0;
		final BitVector[] lcp = new BitVector[ numBuckets ];
		int maxLcp = 0;
		long maxLength = 0;

		pl.expectedUpdates = n;
		
		final ChunkedHashStore<BitVector> triplesStore = new ChunkedHashStore<BitVector>( TransformationStrategies.identity(), pl );
		triplesStore.reset( r.nextLong() );

		pl.start( "Scanning collection..." );
		
		final Iterator<? extends T> iterator = iterable.iterator();
		for( int b = 0; b < numBuckets; b++ ) {
			prev.replace( transform.toBitVector( iterator.next() ) );
			triplesStore.add( prev );
			pl.lightUpdate();
			maxLength = Math.max( maxLength, prev.length() );
			currLcp = (int)prev.length();
			final int currBucketSize = Math.min( bucketSize, n - b * bucketSize );
			
			for( int i = 0; i < currBucketSize - 1; i++ ) {
				curr = transform.toBitVector( iterator.next() );
				triplesStore.add( curr );
				pl.lightUpdate();
				final int prefix = (int)curr.longestCommonPrefixLength( prev );
				if ( prefix == prev.length() && prefix == curr.length()  ) throw new IllegalArgumentException( "The input bit vectors are not distinct" );
				if ( prefix == prev.length() || prefix == curr.length() ) throw new IllegalArgumentException( "The input bit vectors are not prefix-free" );
				if ( prev.getBoolean( prefix ) ) throw new IllegalArgumentException( "The input bit vectors are not lexicographically sorted" );
				
				currLcp = Math.min( prefix, currLcp );
				prev.replace( curr );
				
				maxLength = Math.max( maxLength, prev.length() );
			}

			lcp[ b ] = prev.subVector( 0, currLcp  ).copy();
			maxLcp = Math.max( maxLcp, currLcp );
		}
		
		pl.done();
		
		if ( ASSERTS ) assert new ObjectOpenHashSet<BitVector>( lcp ).size() == lcp.length; // No duplicates.

		LOGGER.info( "Generating the map from LCPs to buckets..." );
		// Build function assigning each lcp to its bucket.
		lcp2Bucket = new MWHCFunction<BitVector>( Arrays.asList( lcp ), TransformationStrategies.identity(), null, Fast.ceilLog2( numBuckets ) );

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

		LOGGER.info( "Generating the map from keys to LCP lengths and offsets..." );
		// Build function assigning the lcp length and the bucketing data to each element.
		offsetLcpLength = new MWHCFunction<BitVector>( TransformationStrategies.wrap( iterable, transform), TransformationStrategies.identity(), new AbstractLongList() {
			public long getLong( int index ) {
				return lcp[ index / bucketSize ].length() << log2BucketSize | index & bucketSizeMask; 
			}
			public int size() {
				return n;
			}
		}, log2BucketSize + Fast.ceilLog2( maxLcp ) + 1, triplesStore );

		this.seed = triplesStore.seed();
		
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
		
		LOGGER.debug( "Forecast bit cost per element: " + ( HypergraphSorter.GAMMA + Fast.log2( Math.E ) - Fast.log2( Fast.log2( Math.E ) ) + Fast.log2( 1 + Fast.log2( n ) ) + Fast.log2( maxLength - Fast.log2( n ) ) ) ); 
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / n );
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

		final SimpleJSAP jsap = new SimpleJSAP( LcpMonotoneMinimalPerfectHashFunction.class.getName(), "Builds an LCP-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding (i.e., just use the lower eight bits of each character)." ),
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
