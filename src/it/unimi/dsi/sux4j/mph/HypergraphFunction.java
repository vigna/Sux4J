package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2008 Sebastiano Vigna 
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

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectFunction;
import it.unimi.dsi.mg4j.io.FastBufferedReader;
import it.unimi.dsi.mg4j.io.FileLinesCollection;
import it.unimi.dsi.mg4j.io.LineIterator;
import it.unimi.dsi.mg4j.util.MutableString;
import it.unimi.dsi.mg4j.util.ProgressLogger;
import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.Fast;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.LongBigList;
import it.unimi.dsi.sux4j.bits.Utf16TransformationStrategy;
import it.unimi.dsi.sux4j.bits.BitVector.TransformationStrategy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
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

import test.it.unimi.dsi.sux4j.mph.HypergraphVisit;
import cern.jet.random.engine.MersenneTwister;

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
 * Tables&rdquo;, <i>Proc. SODA 2004</i>, pages 30&minus;39, 2004, as one of the steps to implement a mutable table.
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

public class HypergraphFunction<T> extends AbstractHash<T> implements Serializable {
    public static final long serialVersionUID = 1L;

	/** The number of bits per block in the rank structure. */
	public static final int BITS_PER_BLOCK = 512;

	private static final Logger LOGGER = Fast.getLogger( HypergraphFunction.class );
	
	private static final long ONES_STEP_4 = 0x1111111111111111L;
	private static final long ONES_STEP_8 = 0x0101010101010101L;

	private static final boolean DEBUG = true;
	
	/** A special value denoting that the weight length is unknown, and should be computed using
	 * the maximum length of a string. */
	public static final int WEIGHT_UNKNOWN = -1;
	/** A special value denoting that the weight length is unknown, and should be computed assuming
	 * that the strings appear in lexicographic order. */
	public static final int WEIGHT_UNKNOWN_SORTED_STRINGS = -2;

	/** The number of buckets. */
	final protected int n;
	/** The number of vertices of the intermediate hypergraph. */
	final protected int m;
	/** {@link #m} &minus; 1. */
	final protected int mMinus1;
	/** {@link #m} &minus; 2. */
	final protected int mMinus2;
	/** Initialisation values for the intermediate hash functions. */
	final protected long init[];
	/** Vector of weights to compute the first intermediate hash function. */
	final protected int[] weight0;
	/** Vector of weights to compute the second intermediate hash function. */
	final protected int[] weight1;
	/** Vector of weights to compute the third intermediate hash function. */
	final protected int[] weight2;
	/** The length of the components of the weight vectors (it's faster than asking the length of the vectors). */
	final protected int weightLength;
	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal hash function. */
	final protected LongBigList bits;
	/** The bit array supporting {@link #bits}. */
	final protected long[] array;
	/** The transformation strategy to turn objects of type <code>T</code> into bit vectors. */
	private TransformationStrategy<? super T> transform;
    
	/*
	 * The following four methods MUST be kept synchronised. The reason why we duplicate code is
	 * that we do not want the overhead of allocating an array when searching for a string.
	 * 
	 * Note that we don't use shift-add-xor hash functions (as in BloomFilter). They are
	 * significantly slower, and in this case (in which we certainly have enough weights to
	 * disambiguate any pair of strings) they are not particularly advantageous.
	 */

	/** Hashes a given strings using the intermediate hash functions.
	 *
	 * @param key a term to hash.
	 * @param h a three-element vector that will be filled with the three intermediate hash values.
	 */

	protected void hash( final T key, final int[] h ) {
		BitVector bv = transform.toBitVector( key );
		long h0 = init[ 0 ], h1 = init[ 1 ], h2 = init[ 2 ]; // We need three these values to map correctly the empty string
		long c;
		int end = (int)( bv.length() / Long.SIZE );
		if ( weightLength < end ) end = weightLength;
		int i;

		for( i = 0; i < end; i++ ) {
			c = bv.getLong( i * Long.SIZE, i * Long.SIZE + Long.SIZE );
			h0 ^= ( h0 << 5 ) + weight0[ i ] + c + ( h0 >>> 2 );
			h1 ^= ( h1 << 5 ) + weight1[ i ] + c + ( h1 >>> 2 );
			h2 ^= ( h2 << 5 ) + weight2[ i ] + c + ( h2 >>> 2 );
		}
		
		final int residual = (int)( bv.length() % Long.SIZE ); 
		
		if ( residual != 0 && i < weightLength ) {
			c = bv.getLong( bv.length() - residual, bv.length() );

			h0 ^= ( h0 << 5 ) + weight0[ i ] + c + ( h0 >>> 2 );
			h1 ^= ( h1 << 5 ) + weight1[ i ] + c + ( h1 >>> 2 );
			h2 ^= ( h2 << 5 ) + weight2[ i ] + c + ( h2 >>> 2 );
		}
		
		h0 ^= ( h0 << 5 ) + ( h0 >>> 2 );
		h1 ^= ( h1 << 5 ) + ( h1 >>> 2 );
		h2 ^= ( h2 << 5 ) + ( h2 >>> 2 );

		h0 = ( h0 & 0x7FFFFFFFFFFFFFFFL ) % m;
		h1 = h0 + ( h1 & 0x7FFFFFFFFFFFFFFFL ) % mMinus1 + 1;
		h2 = h0 + ( h2 & 0x7FFFFFFFFFFFFFFFL ) % mMinus2 + 1;
		if ( h2 >= h1 ) h2++;
		h1 %= m;
		h2 %= m;
					
		h[ 0 ] = (int)h0;
		h[ 1 ] = (int)h1;
		h[ 2 ] = (int)h2;
	}


	@SuppressWarnings("unchecked")
	public int getInt( final Object o ) {
		final long[] h = new long[ 3 ];
		final int[] e = new int[ 3 ];
		Hashes.jenkins( transform.toBitVector( (T)o ), init[ 0 ], h );
		HypergraphVisit.hashesToEdge( h, e, m );
		return (int)( bits.getLong( e[ 0 ] ) ^ bits.getLong( e[ 1 ] ) ^ bits.getLong( e[ 2 ] ) );
	}
	

	/** Returns the length of the weight vectors.
	 *
	 * @return the length of weight vectors used in this table.
	 */

	public int weightLength() {
		return weightLength;
	}

	/** Returns the number of terms hashed.
	 *
	 * @return the number of terms hashed.
	 */

	public int size() {
		return n;
	}


	/** Creates a new order-preserving minimal perfect hash table for the given
	 * terms, using as many weights as the longest term in the collection.
	 *
	 * <P><strong>Caution:</strong> if the collection contains twice the same
	 * term, this constructor will never return.
	 *
	 * @param terms some terms to hash; it is assumed that there are no duplicates.
	 */
	public HypergraphFunction( final Iterable<? extends T> terms, final BitVector.TransformationStrategy<? super T> transform, int[] value, int width ) {
		this( terms, transform, value, width, WEIGHT_UNKNOWN );
	}

	/** Creates a new order-preserving minimal perfect hash table for the 
	 * given terms using the given number of weights.
	 *
	 * <P>This constructor can be used to force the use of a reduced number of weights if you are sure that
	 * the first <code>weightLength</code> characters of each term in <code>terms</code> are
	 * distinct.
	 *
	 * <P>If you do not know your weight length in advance, you can pass two
	 * special values as <code>weightLength</code>: {@link #WEIGHT_UNKNOWN}
	 * will force the computation of <code>weightLength</code> as the maximum
	 * length of a term, whereas {@link #WEIGHT_UNKNOWN_SORTED_STRINGS} forces
	 * the assumption that terms are sorted: in this case, we search for
	 * the minimum prefix that will disambiguate all terms in the collection
	 * (a shorter prefix yields faster lookups). 
	 * 
	 * <P><strong>Caution:</strong> if two terms in the collection have a
	 * common prefix of length <code>weightLength</code> this constructor will
	 * never return.
	 * 
	 * @param terms some terms to hash; if <code>weightLength</code>
	 * is specified explicitly, it is assumed that there are no terms with a common prefix of
	 * <code>weightLength</code> characters.
	 * @param weightLength the number of weights used generating the
	 * intermediate hash functions, {@link #WEIGHT_UNKNOWN} or {@link #WEIGHT_UNKNOWN_SORTED_STRINGS}.
	 * @see #MinimalPerfectHash(Iterable)
	 */

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public HypergraphFunction( final Iterable<? extends T> terms, final BitVector.TransformationStrategy<? super T> transform, int[] value, int width, int weightLength ) {
		if ( weightLength != WEIGHT_UNKNOWN && weightLength != WEIGHT_UNKNOWN_SORTED_STRINGS && weightLength <= 0 ) throw new IllegalArgumentException( "Non-positive weight length: " + weightLength );
		this.transform = transform;
		
		// First of all we compute the size, either by size(), if possible, or simply by iterating.
		if ( terms instanceof Collection ) n = ((Collection<? extends T>)terms).size();
		else {
			int c = 0;
			// Do not add a suppression annotation--it breaks javac
			for( T o: terms ) c++;
			n = c;
		}
		

		if ( weightLength < 0 ) {
			LOGGER.info( "Computing weight length..." );

			Iterator<? extends T> i = terms.iterator();
			long maxLength = Long.MIN_VALUE, minPrefixLength = Long.MIN_VALUE;
			T curr;
			
			if ( i.hasNext() ) {
				BitVector currTerm;
				BitVector prevTerm = LongArrayBitVector.copy( transform.toBitVector( i.next() ) );  // We cannot assume that the string will persist after a call to next().
				long prevLength = prevTerm.length(), currLength;	

				maxLength = prevLength;

				while( i.hasNext() ) {
					currTerm = transform.toBitVector( curr = i.next() );
					currLength = currTerm.length();

					if ( weightLength == WEIGHT_UNKNOWN_SORTED_STRINGS ) {
						final int cmp = prevTerm.compareTo( currTerm );
						if ( cmp > 0 ) throw new IllegalArgumentException( "The list is not sorted" );
						if ( cmp == 0 ) throw new IllegalArgumentException( "The list contains duplicates" );
						final long mcp = currTerm.maximumCommonPrefixLength( prevTerm );
		
						if ( mcp == currLength && mcp == prevLength ) throw new IllegalArgumentException( "The list contains a duplicate (" + curr + ")" );
						minPrefixLength = Math.max( minPrefixLength, mcp + 1 );

						prevTerm.replace( currTerm ); // We cannot assume that the string will persist after a call to next().
						prevLength = currLength;
					}

					maxLength = Math.max( maxLength, currLength );
				}
			}

			weightLength = (int)( ( ( weightLength == WEIGHT_UNKNOWN_SORTED_STRINGS ? minPrefixLength : maxLength ) + Long.SIZE - 1 ) / Long.SIZE );

			LOGGER.info( "Completed [max term length=" + maxLength + "; weight length=" + weightLength + "]." );
		}

		if ( weightLength < 0 ) weightLength = 0;
		weight0 = new int[ weightLength ];
		weight1 = new int[ weightLength ];
		weight2 = new int[ weightLength ];
		init = new long[ 3 ];
		this.weightLength = weightLength;


		final MersenneTwister r = new MersenneTwister( new Date() );
		HypergraphVisit<T> visit = new HypergraphVisit<T>( n );

		m = visit.numberOfVertices;
		mMinus1 = m - 1;
		mMinus2 = m - 2;
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance( m * 2 );
		bits = bitVector.asLongBigList( width );
		bits.size( m );
		array = bitVector.bits();

		final Object2ObjectFunction<T,long[]> hyperedge = new AbstractObject2ObjectFunction<T,long[]>() {
			private static final long serialVersionUID = 1L;
			final long[] h = new long[3];
			@SuppressWarnings("unchecked")
			public long[] get( Object key ) {
				Hashes.jenkins( transform.toBitVector( (T)key ), init[ 0 ], h );
				return h;
			}

			public boolean containsKey( Object key ) { return true;	}
			public int size() { return -1; }
		};
		
		do {
			init[ 1 ] = r.nextInt();
			init[ 2 ] = r.nextInt();
			init[ 0 ] = r.nextLong();
			LOGGER.info( "Generating random hypergraph..." + init[ 0 ]);
			
			
			/* We choose the random weights for the three intermediate hash functions. */
			for ( int i = 0; i < weightLength; i++ ) {
				weight0[ i ] = r.nextInt();
				weight1[ i ] = r.nextInt();
				weight2[ i ] = r.nextInt();
			}
		} while ( ! visit.visit( terms.iterator(), hyperedge ) );
		
		/* We assign values. */
		/** Whether a specific node has already been used as perfect hash value for an item. */
		final BitVector used = LongArrayBitVector.getInstance( m ).length( m );
		
		final int[] stack = visit.stack;
		final int[][] edge = visit.edge;
		int top = n, k, s, v = 0;
		while( top > 0 ) {
			k = stack[ --top ];

			s = 0;

			for ( int j = 0; j < 3; j++ ) {
				if ( ! used.getBoolean( edge[ j ][ k ] ) ) v = j;
				else s ^= bits.getLong( edge[ j ][ k ] );
				used.set( edge[ j ][ k ] );
			}
			
			bits.set( edge[ v ][ k ], value[ k ] ^= s );
		}

		LOGGER.info( "Completed." );
	}

	
	/** Creates a new minimal perfect hash by copying a given one; non-transient fields are (shallow) copied.
	 * 
	 * @param mph the perfect hash to be copied.
	 */
	protected HypergraphFunction( final HypergraphFunction<T> mph ) {
		this.n = mph.n;
		this.m = mph.m;
		this.mMinus1 = mph.mMinus1;
		this.mMinus2 = mph.mMinus2;
		this.weightLength = mph.weightLength;
		this.weight0 = mph.weight0;
		this.weight1 = mph.weight1;
		this.weight2 = mph.weight2;
		this.init = mph.init;
		this.bits = mph.bits;
		this.array = mph.array;
	}
	

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MinimalPerfectHash.class.getName(), "Builds a minimal perfect hash table reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read strings." ),
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
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

		final HypergraphFunction<CharSequence> minimalPerfectHash;

		LOGGER.info( "Building minimal perfect hash table..." );
		FileLinesCollection flc = new FileLinesCollection( stringFile, encoding.toString() );
		final int size = flc.size();
		minimalPerfectHash = new HypergraphFunction<CharSequence>( flc, new Utf16TransformationStrategy(), new int[ size ], 10 );

		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( minimalPerfectHash, tableName );
		LOGGER.info( "Completed." );
	}


}
