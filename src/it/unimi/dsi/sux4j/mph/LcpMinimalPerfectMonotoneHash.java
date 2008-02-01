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

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntFunction;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectFunction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.mg4j.io.FileLinesCollection;
import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.BitVectors;
import it.unimi.dsi.sux4j.bits.Fast;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.LongBigList;
import it.unimi.dsi.sux4j.bits.BitVector.TransformationStrategy;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;

import org.apache.log4j.Logger;

import test.it.unimi.dsi.sux4j.mph.HypergraphVisit;

import cern.jet.random.engine.MersenneTwister;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/** Minimal perfect hash.
 *
 * <P>Given a list of strings without duplicates, 
 * the constructors of this class finds a minimal perfect hash function for
 * the list. Subsequent calls to the {@link #getInt(CharSequence)} method will return a distinct
 * number for each string in the list (for strings out of the list, the resulting number is undefined). The class
 * can then be saved by serialisation and reused later.
 *
 * <P>The theoretical memory requirements are 2.46 + o(<var>n</var>) bits per string, plus the bits
 * for the random hashes (which are usually negligible). The o(<var>n</var>) part is due to
 * an embedded ranking scheme that increases space 
 * occupancy by 0.625%, bringing the actual occupied space to around 2.65 bits per string.
 * At construction time, however, about 15<var>n</var> integers (i.e., 60<var>n</var> bytes) are necessary. 
 * 
 * <P>This class is very scalable, and if you have enough memory it will handle
 * efficiently hundreds of millions of strings: in particular, the 
 * {@linkplain #MinimalPerfectHash(Iterable, int) offline constructor}
 * can build a map without loading the strings in memory.
 * 
 * <P>To do its job, the class must store three vectors of weights that are used to compute
 * certain hash functions. By default, the vectors are long as the longest string, but if
 * your collection is sorted you can ask (passing {@link #WEIGHT_UNKNOWN_SORTED_STRINGS} to a constructor)
 * for the shortest possible vector length. This optimisation does not change the
 * memory footprint, but it can improve performance.
 * 
 * <P>As a commodity, this class provides a main method that reads from
 * standard input a (possibly <samp>gzip</samp>'d) sequence of newline-separated strings, and
 * writes a serialised minimal perfect hash for the given list.
 * 
 * <P>For efficiency, there are also method that access a minimal perfect hash
 * {@linkplain #getInt(byte[], int, int) using byte arrays interpreted as ISO-8859-1} characters.
 *
 * <h3>How it Works</h3>
 * 
 * <p>The technique used is very similar (but developed independently) to that described by Botelho, Pagh and Ziviani
 * in &ldquo;Simple and Efficient Minimal Perfect Hashing Functions&rdquo;, <i>Proc. WADS 2007</i>, number
 * 4619 of Lecture Notes in Computer Science, pages 139&minus;150, 2007. In turn, the mapping technique describe
 * therein was actually first proposed by Chazelle, Kilian, Rubinfeld and Tal in 
 * &ldquo;The Bloomier Filter: an Efficient Data Structure for Static Support Lookup 
 * Tables&rdquo;, <i>Proc. SODA 2004</i>, pages 30&minus;39, 2004. 
 * 
 * <p>The basic ingredient is a stripping procedure to be applied to an acyclic 3-hypergraph. The  
 * idea is due to Havas, Majewski, Wormald and Czech (&ldquo;A Family of Perfect Hashing Methods&rdquo;,
 * <i>Computer J.</i>, 39(6):547&minus;554, 1996).
 * First, a triple of intermediate hash functions (generated via universal hashing) define for each
 * string a 3-hyperedge in a hypergraph with 1.23<var>n</var> vertices. Each intermediate hash function
 * uses a vector of random weights; the length of the vector is by default the length of the longest
 * string, but if the collection is sorted it is possible to compute the minimum length of a prefix that will
 * distinguish any pair of strings. 
 * 
 * <P>Then, by successively stripping 3-hyperedges with one vertex of 
 * degree one, we create an ordering on the hyperedges.
 * Finally, we assign (in reverse order) to each vertex of a 3-hyperedge a two-bit number 
 * in such a way that the sum of the three numbers modulo 3 gives, for each hyperedge, the index of the hash
 * function generating the vertex of degree one found during the stripping procedure. This vertex is distinct for
 * each 3-hyperedge, and as a result we obtain a perfect hash of the original string set (one just has to compute
 * the three hash functions, collect the three two-bit values, add them modulo 3 and take the corresponding hash function value).
 * 
 * <p>To obtain a minimal perfect hash, we simply notice that we whenever we have to assign a value to a vertex,
 * we can take care of using the number 3 instead of 0 if the vertex is actually the output value for some string. As
 * a result, the final value of the minimal perfect hash function is the number of nonzero pairs of bits that precede
 * the perfect hash value for the string. 
 * To compute this number, we use a simple table-free ranking scheme, recording the number
 * of nonzero pairs each {@link #BITS_PER_BLOCK} bits and modifying the
 * standard broadword algorithm for computing the number of ones
 * in a word into an algorithm that {@linkplain #countNonzeroPairs(long) counts
 * the number of nonzero pairs of bits in a word}.
 * 
 * <P>Note that the mathematical results guaranteeing that it is possible to find a
 * function in expected constant time are <em>asymptotic</em>. 
 * Thus, when the number of strings is less than {@link #STRING_THRESHOLD}, an instance
 * of this class just stores the strings in a vector and scans them linearly. This behaviour,
 * however, is completely transparent to the user.
 *
 * <h3>Rounds and Logging</h3>
 * 
 * <P>Building a minimal perfect hash map may take hours. As it happens with all probabilistic algorithms,
 * one can just give estimates of the expected time.
 * 
 * <P>There are two probabilistic sources of problems: duplicate hyperedges and non-acyclic hypergraphs.
 * However, the probability of duplicate hyperedges is vanishing when <var>n</var> approaches infinity,
 * and once the hypergraph has been generated, the stripping procedure succeeds in an expected number
 * of trials that tends to 1 as <var>n</var> approaches infinity.
 *  
 * <P>To help diagnosing problem with the generation process
 * class, this class will log at {@link org.apache.log4j.Level#INFO INFO} level
 * what's happening. In particular, it will signal whenever a degenerate
 * hyperedge is generated, and the various construction phases.
 *
 * <P>Note that if during the generation process the log warns more than once about duplicate hyperedges, you should
 * suspect that there are duplicates in the string list, as duplicate hyperedges are <em>extremely</em> unlikely.
 *
 * @author Sebastiano Vigna
 * @since 0.1
 */

public class LcpMinimalPerfectMonotoneHash<T> extends AbstractHash<T> implements Serializable {
    public static final long serialVersionUID = 1L;

	/** The number of bits per block in the rank structure. */
	public static final int BITS_PER_BLOCK = 512;

	private static final Logger LOGGER = Fast.getLogger( LcpMinimalPerfectMonotoneHash.class );
	
	private static final boolean DEBUG = true;
	
	/** A special value denoting that the weight length is unknown, and should be computed using
	 * the maximum length of a string. */
	public static final int WEIGHT_UNKNOWN = -1;
	/** A special value denoting that the weight length is unknown, and should be computed assuming
	 * that the strings appear in lexicographic order. */
	public static final int WEIGHT_UNKNOWN_SORTED_STRINGS = -2;

	private TransformationStrategy<? super T> transform;

	private int bucketSize;

	private int weightLength;

	
	/** The number of buckets. */
	final protected int n;
	/** The number of vertices of the intermediate hypergraph. */
	/** Initialisation values for the intermediate hash functions. */
	final protected int init[];
	/** Vector of weights to compute the first intermediate hash function. */
	final protected int[] weight0;
	/** Vector of weights to compute the second intermediate hash function. */
	final protected int[] weight1;
	/** Vector of weights to compute the third intermediate hash function. */
	final protected int[] weight2;
	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal hash function. */

	private HypergraphFunction<T> lcpLength;

	private HypergraphFunction<BitVector> lcp2Bucket;

	private LongBigList rehash;

	private int m;

	/** Returns the number of terms hashed.
	 *
	 * @return the number of terms hashed.
	 */

	public int size() {
		return n;
	}

	public boolean hasTerms() {
		return false;
	}
	
	/** Hashes a given strings using the intermediate hash functions.
	 *
	 * @param key a term to hash.
	 * @param h a three-element vector that will be filled with the three intermediate hash values.
	 */

	protected void hash( final BitVector bv, final int rehash, final int[] h ) {
		long h0 = init[ 0 ], h1 = init[ 1 ], h2 = init[ 2 ]; // We need three these values to map correctly the empty string
		long c;
		int end = (int)( bv.length() / Long.SIZE );
		if ( weightLength < end ) end = weightLength;
		int i;

		for( i = 0; i < end; i++ ) {
			c = bv.getLong( i * Long.SIZE, i * Long.SIZE + Long.SIZE );
			h0 ^= ( h0 << 5 ) + weight0[ i ] + rehash + c + ( h0 >>> 2 );
			h1 ^= ( h1 << 5 ) + weight1[ i ] + rehash + c + ( h1 >>> 2 );
			h2 ^= ( h2 << 5 ) + weight2[ i ] + rehash + c + ( h2 >>> 2 );
		}
		
		final int residual = (int)( bv.length() % Long.SIZE ); 
		System.err.println( i  + " " + weightLength );
		if ( residual != 0 && i < weightLength ) {
			c = bv.getLong( bv.length() - residual, bv.length() );

			h0 ^= ( h0 << 5 ) + weight0[ i ] + rehash + c + ( h0 >>> 2 );
			h1 ^= ( h1 << 5 ) + weight1[ i ] + rehash + c + ( h1 >>> 2 );
			h2 ^= ( h2 << 5 ) + weight2[ i ] + rehash + c + ( h2 >>> 2 );
		}
		
		h0 ^= ( h0 << 5 ) + ( h0 >>> 2 );
		h1 ^= ( h1 << 5 ) + ( h1 >>> 2 );
		h2 ^= ( h2 << 5 ) + ( h2 >>> 2 );

		h0 = ( h0 & 0x7FFFFFFFFFFFFFFFL ) % m;
		h1 = h0 + ( h1 & 0x7FFFFFFFFFFFFFFFL ) % ( m - 1 ) + 1;
		h2 = h0 + ( h2 & 0x7FFFFFFFFFFFFFFFL ) % ( m - 2 ) + 1;
		if ( h2 >= h1 ) h2++;
		h1 %= m;
		h2 %= m;
					
		h[ 0 ] = (int)h0;
		h[ 1 ] = (int)h1;
		h[ 2 ] = (int)h2;
	}



	private static int count( final Iterator<?> i ) {
		int c = 0;
		while( i.hasNext() ) {
			c++;
			i.next();
		}
		return c;
	}
	
	@SuppressWarnings("unchecked")
	public int getInt( final Object o ) {
		final BitVector bv = transform.toBitVector( (T)o );
		return lcp2Bucket.getInt( bv.subVector( 0, lcpLength.getInt( bv ) ) );
	}

	public LcpMinimalPerfectMonotoneHash( final Iterable<? extends T> iterable, final BitVector.TransformationStrategy<? super T> transform ) {
		this( iterable, count( iterable.iterator() ), transform );
	}
		
	public LcpMinimalPerfectMonotoneHash( final Iterable<? extends T> iterable, final int n, final BitVector.TransformationStrategy<? super T> transform ) {
		this.n = n;
		this.transform = transform;

		Iterator<? extends T> iterator = iterable.iterator();
		
		if ( ! iterator.hasNext() ) {
			init = weight0 = weight1 = weight2 = IntArrays.EMPTY_ARRAY;
			return;
		}
		bucketSize = (int)Math.ceil( 1 + HypergraphVisit.GAMMA * Math.log( 2 ) + Math.log( n ) - Math.log( 1 + Math.log( n ) ) );
		final int numBuckets = ( n + bucketSize - 1 ) / bucketSize;
		
		LongArrayBitVector prev = LongArrayBitVector.getInstance();
		BitVector curr = null;
		int prefix, currLcp = 0;
		BitVector[] lcp = new BitVector[ numBuckets ];
		int[] lcpLengthTemp = new int[ n ];
		int logu = 0;
		
		for( int b = 0; b < numBuckets; b++ ) {
			prev.replace( transform.toBitVector( iterator.next() ) );
			logu = (int)Math.max( logu, prev.length() );
			currLcp = (int)prev.length() - 1;
			final int currBucketSize = Math.min( bucketSize, n - b * bucketSize );
			
			for( int i = 0; i < currBucketSize - 1; i++ ) {
				curr = transform.toBitVector( iterator.next() );
				final int cmp = prev.compareTo( curr );
				if ( cmp > 0 ) throw new IllegalArgumentException( "The list is not sorted" );
				if ( cmp == 0 ) throw new IllegalArgumentException( "The list contains duplicates" );
				
				prefix = (int)curr.maximumCommonPrefixLength( prev );
				currLcp = Math.min( prefix, currLcp );
				logu = Math.max( logu, prefix + 1 );
				prev.replace( curr );
			}

			lcp[ b ] = prev.subVector( 0, currLcp + 1 ).copy();
			for( int j = currBucketSize; j-- != 0; ) lcpLengthTemp[ b * bucketSize + j ] = currLcp;
		}
		
		
		int[] temp = new int[ lcp.length ];
		for( int i = temp.length; i-- != 0; ) temp[ i ] = i;
		
		lcp2Bucket = new HypergraphFunction<BitVector>( Arrays.asList( lcp ), BitVectors.identity(), temp, logu, HypergraphFunction.WEIGHT_UNKNOWN_SORTED_STRINGS );

		lcpLength = new HypergraphFunction<T>( iterable, transform, lcpLengthTemp, logu, HypergraphFunction.WEIGHT_UNKNOWN_SORTED_STRINGS );

		weightLength = lcpLength.weightLength;
		weight0 = lcpLength.weight0;
		weight1 = lcpLength.weight1;
		weight2 = lcpLength.weight2;
		init = null;//lcpLength.init;
		m = lcpLength.m;
		
		iterator = iterable.iterator();
		
		int c = 0;
		ObjectArrayList<LongArrayBitVector> buckets = new ObjectArrayList<LongArrayBitVector>( bucketSize );
		HypergraphVisit<LongArrayBitVector> visit = new HypergraphVisit<LongArrayBitVector>( bucketSize );
		
		final int[] rehash = new int[ 1 ];
		
		final Object2ObjectFunction<LongArrayBitVector,int[]> hyperedge = new AbstractObject2ObjectFunction<LongArrayBitVector,int[]>() {
			private static final long serialVersionUID = 1L;
			final int[] h = new int[3];
			@SuppressWarnings("unchecked")
			public int[] get( Object key ) {
				LcpMinimalPerfectMonotoneHash.this.hash( (BitVector)key, rehash[ 0 ], h );
				return h;
			}

			public boolean containsKey( Object key ) { return true;	}
			public int size() { return -1; }
		};

		MersenneTwister mt = new MersenneTwister( new Date() );
		int rehashSize =  Fast.ceilLog2( bucketSize );
		this.rehash = LongArrayBitVector.getInstance().asLongBigList(rehashSize ).length( numBuckets );
		
		for( int b = 0; b < numBuckets; b++ ) {
			final int currBucketSize = Math.min( bucketSize, n - b * bucketSize );
			buckets.clear();
			
			for( int i = 0; i < currBucketSize - 1; i++ ) {
				buckets.add( LongArrayBitVector.copy( transform.toBitVector( iterator.next() ) ) );
			//	do {
				//	rehash[ 0 ] = mt.nextInt() & ( 1 << rehashSize ) - 1;
				//} while( ! visit.visit( buckets.iterator(), hyperedge ) );
				
				this.rehash.add( rehash[ 0 ] );
			}
			
			buckets.clear();
		}
		
		
		LOGGER.debug( "Bucket size: " + bucketSize );
		LOGGER.debug( "log(U): " + logu );
		LOGGER.debug( "Forecast bit cost per element: " + ( 2 * HypergraphVisit.GAMMA + Fast.log2( 1 + Fast.log2( n ) ) + Fast.log2( logu - Fast.log2( 1 + Fast.log2( n ) ) ) ) );
		LOGGER.debug( "Actual bit cost per element: " + HypergraphVisit.GAMMA * n + lcp.length * Fast.ceilLog2( logu ) );
	}


	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( LcpMinimalPerfectMonotoneHash.class.getName(), "Builds a minimal perfect hash table reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read strings." ),
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to increase entropy (only available for offline construction)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new Switch( "sorted", 's', "sorted", "The string list is sorted--optimise weight length." ),
			new FlaggedOption( "stringFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read strings from this file (without loading them into core memory) instead of standard input." ),
			new UnflaggedOption( "table", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash table." )
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final int bufferSize = jsapResult.getInt( "bufferSize" );
		final String tableName = jsapResult.getString( "table" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean sorted = jsapResult.getBoolean( "sorted" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );

		final LcpMinimalPerfectMonotoneHash minimalPerfectHash;

		if ( huTucker && stringFile == null ) throw new IllegalArgumentException( "Hu-Tucker coding requires offline construction" );


		if ( stringFile == null ) {
			minimalPerfectHash = null;
			//minimalPerfectHash = new UniformMinimalPerfectMonotoneHash<CharSequence>( new LineIterator( new FastBufferedReader( new InputStreamReader( System.in, encoding ), bufferSize ) ), BitVectors.utf16() );
		}
		else {
			FileLinesCollection collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
			minimalPerfectHash = new LcpMinimalPerfectMonotoneHash<CharSequence>( collection, huTucker ? new HuTuckerTransformationStrategy( collection ) : new Utf16TransformationStrategy() );
		}
		

		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( minimalPerfectHash, tableName );
		LOGGER.info( "Completed." );
	}

}
