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

import it.unimi.dsi.fastutil.booleans.BooleanArrays;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntFunction;
import it.unimi.dsi.mg4j.io.FastBufferedReader;
import it.unimi.dsi.mg4j.io.FileLinesCollection;
import it.unimi.dsi.mg4j.io.LineIterator;
import it.unimi.dsi.mg4j.util.MutableString;
import it.unimi.dsi.mg4j.util.ProgressLogger;
import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.Fast;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.LongBigList;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

import org.apache.log4j.Logger;

import cern.colt.GenericPermuting;
import cern.colt.GenericSorting;
import cern.colt.Swapper;
import cern.colt.function.IntComparator;
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

public class MinimalPerfectMonotoneHash extends AbstractObject2IntFunction<CharSequence> implements Serializable {
    public static final long serialVersionUID = 1L;

	/** The number of bits per block in the rank structure. */
	public static final int BITS_PER_BLOCK = 512;

	private static final Logger LOGGER = Fast.getLogger( MinimalPerfectMonotoneHash.class );
	
	private static final long ONES_STEP_4 = 0x1111111111111111L;
	private static final long ONES_STEP_8 = 0x0101010101010101L;

	private static final double GAMMA = 1.23;
	private static final double CSI = Math.E * Math.pow( 2, GAMMA );
	
	private static final boolean DEBUG = true;
	
	/** The number of nodes the hypergraph will actually have. This value guarantees that the hypergraph will be acyclic with positive probability. */
	public static final double ENLARGEMENT_FACTOR = 1.23;
	/** The minimum number of strings that will trigger the construction of a minimal perfect hash;
	 * otherwise, strings are simply stored in a vector. */
	public static final int STRING_THRESHOLD = 16;
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
	final protected int init[];
	/** Vector of weights to compute the first intermediate hash function. */
	final protected int[] weight0;
	/** Vector of weights to compute the second intermediate hash function. */
	final protected int[] weight1;
	/** Vector of weights to compute the third intermediate hash function. */
	final protected int[] weight2;
	/** The length of the components of the weight vectors (it's faster than asking the length of the vectors). */
	final protected int weightLength;
	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal hash function. */
	protected transient LongBigList bits;
	/** The bit array supporting {@link #bits}. */
	final protected long[] array;
	/** The number of nonzero bit pairs up to a given block. */
	final protected int count[];
	/** Four times the number of buckets. */
	protected transient long n4;
	/** If {@link #n} is smaller than {@link #STRING_THRESHOLD}, a vector containing the strings. */
	protected transient CharSequence[] t;

	private final int bucketSize;
    
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
	 * @param term a term to hash.
	 * @param h a three-element vector that will be filled with the three intermediate hash values.
	 */

	protected void hash( final CharSequence term, final int[] h ) {
		int h0 = init[ 0 ], h1 = init[ 1 ], h2 = init[ 2 ]; // We need three these values to map correctly the empty string
		char c;
		int i = term.length();
		if ( weightLength < i ) i = weightLength;
		while( i-- != 0 ) {
			c = term.charAt( i );
			h0 ^= weight0[ i ] * c;
			h1 ^= weight1[ i ] * c;
			h2 ^= weight2[ i ] * c;
		}
		
		h0 = ( h0 & 0x7FFFFFFF ) % m;
		h1 = h0 + ( h1 & 0x7FFFFFFF ) % mMinus1 + 1;
		h2 = h0 + ( h2 & 0x7FFFFFFF ) % mMinus2 + 1;
		if ( h2 >= h1 ) h2++;
		h1 %= m;
		h2 %= m;
		
		h[ 0 ] = h0;
		h[ 1 ] = h1;
		h[ 2 ] = h2;
	}


	public int getInt( final Object o ) {
		return getInt( (CharSequence)o );
	}
	
	/** Hashes a given term.
	 *
	 * @param term a term to be hashed.
	 * @return the position of the given term in the generating collection, starting from 0. If the
	 * term was not in the original collection, the result is a random position.
	 *
	 */
	public int getInt( final CharSequence term ) {
		
		if ( t != null ) return getFromT( term );
		
		int h0 = init[ 0 ], h1 = init[ 1 ], h2 = init[ 2 ]; // We need three these values to map correctly the empty string
		char c;
		int i = term.length();
		if ( weightLength < i ) i = weightLength;
		while( i-- != 0 ) {
			c = term.charAt( i );
			h0 ^= weight0[ i ] * c;
			h1 ^= weight1[ i ] * c;
			h2 ^= weight2[ i ] * c;
		}

		h0 = ( h0 & 0x7FFFFFFF ) % m;
		h1 = h0 + ( h1 & 0x7FFFFFFF ) % mMinus1 + 1;
		h2 = h0 + ( h2 & 0x7FFFFFFF ) % mMinus2 + 1;
		if ( h2 >= h1 ) h2++;
		h1 %= m;
		h2 %= m;
			
		i = (int)( ( bits.getLong( h0 ) + bits.getLong( h1 ) + bits.getLong( h2 ) ) % 3 );
		
		return rank( i == 0 ? h0 : i == 1 ? h1 : h2 );
	}


	/**
	 * Hashes a given term.
	 * 
	 * @param term
	 *            a term to be hashed.
	 * @return the position of the given term in the generating collection,
	 *         starting from 0. If the term was not in the original collection,
	 *         the result is a random position.
	 */

	public int getInt( final MutableString term ) {

		if ( t != null ) return getFromT( term );

		int h0 = init[ 0 ], h1 = init[ 1 ], h2 = init[ 2 ]; // We need three these values to map correctly the empty string
		char c;
		int i = term.length();
		final char[] a = term.array();
		if ( weightLength < i ) i = weightLength;
		while( i-- != 0 ) {
			c = a[ i ];
			h0 ^= weight0[ i ] * c;
			h1 ^= weight1[ i ] * c;
			h2 ^= weight2[ i ] * c;
		}

		h0 = ( h0 & 0x7FFFFFFF ) % m;
		h1 = h0 + ( h1 & 0x7FFFFFFF ) % mMinus1 + 1;
		h2 = h0 + ( h2 & 0x7FFFFFFF ) % mMinus2 + 1;
		if ( h2 >= h1 ) h2++;
		h1 %= m;
		h2 %= m;

		i = (int)( ( bits.getLong( h0 ) + bits.getLong( h1 ) + bits.getLong( h2 ) ) % 3 );
		
		return rank( i == 0 ? h0 : i == 1 ? h1 : h2 );
	}

	/** Hashes a term given as a byte-array fragment interpreted in the ISO-8859-1 charset encoding.
	 *
	 * @param a a byte array.
	 * @param off the first valid byte in <code>a</code>.
	 * @param len the number of bytes composing the term, starting at <code>off</code>.
	 * @return the position of term defined by <code>len</code> bytes starting at <code>off</code> (interpreted
	 * as ISO-8859-1 characters) in the generating collection, starting from 0. If the
	 * term was not in the original collection, the result is a random position.
	 */

	public int getInt( final byte[] a, final int off, final int len ) {

		if ( t != null )
			try {
				return getFromT( new String( a, off, len, "ISO-8859-1" ) );
			}
			catch ( UnsupportedEncodingException cantHappen ) {
				throw new RuntimeException( cantHappen );
			}

		int h0 = init[ 0 ], h1 = init[ 1 ], h2 = init[ 2 ]; // We need three these values to map correctly the empty string
		int c;
		int i = len;
		if ( weightLength < i ) i = weightLength;
		while( i-- != 0 ) {
			c = a[ off + i ] & 0xFF;
			h0 ^= weight0[ i ] * c;
			h1 ^= weight1[ i ] * c;
			h2 ^= weight2[ i ] * c;
		}

		h0 = ( h0 & 0x7FFFFFFF ) % m;
		h1 = h0 + ( h1 & 0x7FFFFFFF ) % mMinus1 + 1;
		h2 = h0 + ( h2 & 0x7FFFFFFF ) % mMinus2 + 1;
		if ( h2 >= h1 ) h2++;
		h1 %= m;
		h2 %= m;

		i = (int)( ( bits.getLong( h0 ) + bits.getLong( h1 ) + bits.getLong( h2 ) ) % 3 );
		
		return rank( i == 0 ? h0 : i == 1 ? h1 : h2 );
	}

	/** Hashes a term given as a byte array interpreted in the ISO-8859-1 charset encoding.
	 *
	 * @param a a byte array.
	 * @return the position of term defined by the bytes in a <code>a</code> (interpreted
	 * as ISO-8859-1 characters) in the generating collection, starting from 0. If the
	 * term was not in the original collection, the result is a random position.
	 */

	public int getInt( final byte[] a ) {
		return getInt( a, 0, a.length );
	}
		
	/** Gets a term out of the stored array {@link #t}. 
	 * 
	 * <P>Note: This method does not check for {@link #t} being non-<code>null</code>.
	 * 
	 * @param term a term.
	 * @return the position of the given term in the generating collection, starting from 0. If the
	 * term was not in the original collection, the result is 0.
	 */
	protected int getFromT( final CharSequence term ) {
		int i = n;
		/* Note that we invoke equality *on the stored MutableString*. This
		 * is essential due to the known weaknesses in CharSequence's contract. */
		while( i-- != 0 ) if ( t[ i ].equals( term ) ) return i; 
		return 0;
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

	public boolean hasTerms() {
		return false;
	}

	/** Creates a new order-preserving minimal perfect hash table for the given
	 * terms, using as many weights as the longest term in the collection.
	 *
	 * <P><strong>Caution:</strong> if the collection contains twice the same
	 * term, this constructor will never return.
	 *
	 * @param terms some terms to hash; it is assumed that there are no duplicates.
	 */
	public MinimalPerfectMonotoneHash( final Iterable<? extends CharSequence> terms ) {
		this( terms, WEIGHT_UNKNOWN );
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
	 * @see #j(Iterable)
	 */

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public MinimalPerfectMonotoneHash( final Iterable<? extends CharSequence> terms, int weightLength ) {
		if ( weightLength != WEIGHT_UNKNOWN && weightLength != WEIGHT_UNKNOWN_SORTED_STRINGS && weightLength <= 0 ) throw new IllegalArgumentException( "Non-positive weight length: " + weightLength );

		// First of all we compute the size, either by size(), if possible, or simply by iterating.
		if ( terms instanceof Collection) n = ((Collection<? extends CharSequence>)terms).size();
		else {
			int c = 0;
			// Do not add a suppression annotation--it breaks javac
			for( CharSequence o: terms ) c++;
			n = c;
		}

		bucketSize = (int)Math.ceil( Math.log( CSI * n ) - Math.log( Math.log( CSI * n ) ) );
		n4 = n * 4;
		m = ( (int)Math.ceil( n * ENLARGEMENT_FACTOR ) + BITS_PER_BLOCK - 1 ) & -BITS_PER_BLOCK;
		mMinus1 = m - 1;
		mMinus2 = m - 2;
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance( m * 2 );
		bits = bitVector.asLongBigList( 2 );
		bits.size( m );
		array = bitVector.bits();

		if ( weightLength < 0 ) {
			LOGGER.info( "Computing weight length..." );

			Iterator<? extends CharSequence> i = terms.iterator();
			int maxLength = Integer.MIN_VALUE, minPrefixLength = Integer.MIN_VALUE;
			
			if ( i.hasNext() ) {
				CharSequence currTerm;
				MutableString prevTerm = new MutableString( i.next() );  // We cannot assume that the string will persist after a call to next().
				int k, prevLength = prevTerm.length(), currLength;	

				maxLength = prevLength;

				while( i.hasNext() ) {
					currTerm = i.next();
					currLength = currTerm.length();

					if ( weightLength == WEIGHT_UNKNOWN_SORTED_STRINGS ) {
						for( k = 0; k < prevLength && k < currLength && currTerm.charAt( k ) == prevTerm.charAt( k ); k++ );
		
						if ( k == currLength && k == prevLength ) throw new IllegalArgumentException( "The term list contains a duplicate (" + currTerm + ")" );
						minPrefixLength = Math.max( minPrefixLength, k + 1 );

						prevTerm.replace( currTerm ); // We cannot assume that the string will persist after a call to next().
						prevLength = currLength;
					}

					maxLength = Math.max( maxLength, currLength );
				}
			}

			weightLength = weightLength == WEIGHT_UNKNOWN_SORTED_STRINGS ? minPrefixLength : maxLength;

			LOGGER.info( "Completed [max term length=" + maxLength + "; weight length=" + weightLength + "]." );
		}

		if ( weightLength < 0 ) weightLength = 0;
		weight0 = new int[ weightLength ];
		weight1 = new int[ weightLength ];
		weight2 = new int[ weightLength ];
		init = new int[ 3 ];
		this.weightLength = weightLength;

		if ( n < STRING_THRESHOLD ) {
			int j = 0;
			t = new MutableString[ n ];
			for( Iterator<? extends CharSequence> i = terms.iterator(); i.hasNext(); ) t[ j++ ] = new MutableString( i.next() ); 
		}
		else {		
			t = null;
			new Visit( terms );
		}
		
		count = new int[ 2 * m / BITS_PER_BLOCK ];
		int c = 0;
		
		for( int i = 0; i < 2 * m / Long.SIZE; i++ ) {
			if ( ( i & 7 ) == 0 ) count[ i / 8 ] = c;
			c += countNonzeroPairs( array[ i ] );
		}
		
		if ( DEBUG ) {
			int k = 0;
			for( int i = 0; i < m; i++ ) {
				assert rank( i ) == k : "(" + i + ") " + k + " != " + rank( 2 * i ); 
				if ( bits.getLong( i ) != 0 ) k++;
			}
			
		}
	}

	/** Counts the number of nonzero pairs of bits in a long.
	 * 
	 * <p>This method uses a very simple modification of the classical
	 * {@linkplain Fast#count(long) broadword algorithm to count the number of ones in a word}.
	 * Usually, in the first step of the algorithm one computes the number of ones in each pair of bits.
	 * Instead, we compute one iff at least one of the bits in the pair is nonzero.
	 * 
	 * @param x a long.
	 * @return the number of nonzero bit pairs in <code>x</code>.
	 */
	
	public static int countNonzeroPairs( final long x ) {
		long byteSums = ( x | x >>> 1 ) & 0x5 * ONES_STEP_4;
        byteSums = ( byteSums & 3 * ONES_STEP_4 ) + ( ( byteSums >>> 2 ) & 3 * ONES_STEP_4 );
        byteSums = ( byteSums + ( byteSums >>> 4 ) ) & 0x0f * ONES_STEP_8;
        return (int)( byteSums * ONES_STEP_8 >>> 56 );
	}

	public int rank( long x ) {
		x = 2 * x;
		final int word = (int)( x / Long.SIZE );
		int rank = count[ word / 8 ];
		int wordInBlock = word & ~7;
		while( wordInBlock < word ) rank += countNonzeroPairs( array[ wordInBlock++ ] );
		
		return rank + countNonzeroPairs( array[ word ] & ( 1L << x % Long.SIZE ) - 1 );
	}

	/** Creates a new order-preserving minimal perfect hash table for the (possibly <samp>gzip</samp>'d) given file 
	 * of terms using the given number of weights.
	 *
	 * @param termFile an file containing one term on each line; it is assumed that 
	 * it does not contain terms with a common prefix of
	 * <code>weightLength</code> characters.
	 * @param encoding the encoding of <code>termFile</code>; if <code>null</code>, it
	 * is assumed to be the platform default encoding.
	 * @param weightLength the number of weights used generating the
	 * intermediate hash functions, {@link #WEIGHT_UNKNOWN} or {@link #WEIGHT_UNKNOWN_SORTED_STRINGS}.
	 * @param zipped if true, the provided file is zipped and will be opened using a {@link GZIPInputStream}.
	 * @see #MinimalPerfectHash(Iterable, int)
	 */


	public MinimalPerfectMonotoneHash( final String termFile, final String encoding, int weightLength, boolean zipped ) {
		this( new FileLinesCollection( termFile, encoding, zipped ), weightLength );
	}

	/** Creates a new order-preserving minimal perfect hash table for the (possibly <samp>gzip</samp>'d) given file 
	 * of terms.
	 *
	 * @param termFile an file containing one term on each line; it is assumed that 
	 * it does not contain terms with a common prefix of
	 * <code>weightLength</code> characters.
	 * @param encoding the encoding of <code>termFile</code>; if <code>null</code>, it
	 * is assumed to be the platform default encoding.
	 * @param zipped if true, the provided file is zipped and will be opened using a {@link GZIPInputStream}.
	 * @see #MinimalPerfectHash(Iterable, int)
	 */


	public MinimalPerfectMonotoneHash( final String termFile, final String encoding, boolean zipped ) {
		this( termFile, encoding, WEIGHT_UNKNOWN, zipped );
	}


	/** Creates a new order-preserving minimal perfect hash table for the given file 
	 * of terms using the given number of weights.
	 *
	 * @param termFile an file containing one term on each line; it is assumed that 
	 * it does not contain terms with a common prefix of
	 * <code>weightLength</code> characters.
	 * @param encoding the encoding of <code>termFile</code>; if <code>null</code>, it
	 * is assumed to be the platform default encoding.
	 * @param weightLength the number of weights used generating the
	 * intermediate hash functions, {@link #WEIGHT_UNKNOWN} or {@link #WEIGHT_UNKNOWN_SORTED_STRINGS}.
	 * @see #MinimalPerfectHash(Iterable, int)
	 */


	public MinimalPerfectMonotoneHash( final String termFile, final String encoding, int weightLength ) {
		this( new FileLinesCollection( termFile, encoding ), weightLength );
	}

	/** Creates a new order-preserving minimal perfect hash table for the given file 
	 * of terms.
	 *
	 * @param termFile an file containing one term on each line; it is assumed that 
	 * it does not contain terms with a common prefix of
	 * <code>weightLength</code> characters.
	 * @param encoding the encoding of <code>termFile</code>; if <code>null</code>, it
	 * is assumed to be the platform default encoding.
	 * @see #MinimalPerfectHash(Iterable, int)
	 */


	public MinimalPerfectMonotoneHash( final String termFile, final String encoding ) {
		this( termFile, encoding, WEIGHT_UNKNOWN, false );
	}


	
	/** Creates a new minimal perfect hash by copying a given one; non-transient fields are (shallow) copied.
	 * 
	 * @param mph the perfect hash to be copied.
	 */
	protected MinimalPerfectMonotoneHash( final MinimalPerfectMonotoneHash mph ) {
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
		this.count = mph.count;
		this.n4 = mph.n4;
		this.t = mph.t;
		this.bucketSize = mph.bucketSize;
	}

	/** The internal state of a visit. */
	
	private class Visit {
		/** An 3&times;n array recording the triple of vertices involved in each hyperedge. It is *reversed*
		   w.r.t. what you would expect to reduce object creation. */
		final int[][] edge = new int[ 3 ][ n ];
		/** Whether a hyperedge has been already removed. */
		final boolean[] removed = new boolean[ n ];
		/** For each vertex of the intermediate hypergraph, the vector of incident hyperedges. */
		final int[] inc = new int[ m * 3 ];
		/** The next position to fill in the respective incidence vector. 
		 * Used also to store the hyperedge permutation and speed up permute(). */
		final int[] last = new int[ m ]; 
		/** For each vertex of the intermediate hypergraph, the offset into 
		 * the vector of incident hyperedges. Used also to speed up permute(). */
		final int[] incOffset = new int[ m ];
		/** The hyperedge stack. Used also to invert the hyperedge permutation. */
		final int[] stack = new int[ n ];
		/** The degree of each vertex of the intermediate hypergraph. */
		final int[] d = new int[ m ];
		/** Initial top of the hyperedge stack. */
		int top;

		public Visit( final Iterable<? extends CharSequence> terms ) {
			// We cache all variables for faster access
			final int[][] edge = this.edge;
			final int[] last = this.last;
			final int[] inc = this.inc;
			final int[] incOffset = this.incOffset;
			final int[] stack = this.stack;
			final int[] d = this.d;

			final MersenneTwister r = new MersenneTwister( new Date() );

			int i, j, k, s, v = -1;
			final int[] h = new int[ 3 ];
			
			do {
				
				LOGGER.info( "Generating random hypergraph..." );
				top = 0;
				
				init[ 0 ] = r.nextInt();
				init[ 1 ] = r.nextInt();
				init[ 2 ] = r.nextInt();
				
				/* We choose the random weights for the three intermediate hash functions. */
				for ( i = 0; i < weightLength; i++ ) {
					weight0[ i ] = r.nextInt();
					weight1[ i ] = r.nextInt();
					weight2[ i ] = r.nextInt();
				}
					 
					 
				/* We build the hyperedge list, checking that we do not create a degenerate hyperedge. */
				i = 0;
				CharSequence cs = null;
				IntArrays.fill( d, 0 );

				for( Iterator<? extends CharSequence> w = terms.iterator(); w.hasNext(); ) {
					hash( cs = w.next(), h );
					if ( h[ 0 ] == h[ 1 ] || h[ 1 ] == h[ 2 ] || h[ 2 ] == h[ 0 ] ) break;

					edge[ 0 ][ i ] = h[ 0 ];
					edge[ 1 ][ i ] = h[ 1 ];
					edge[ 2 ][ i ] = h[ 2 ];

					i++;
				}
					 
				if ( i < n ) {
					LOGGER.info( "Hypergraph generation interrupted by degenerate hyperedge " + i + " (string: \"" + cs + "\")." );
					continue;
				} 

				/* We compute the degree of each vertex. */
				for( j = 0; j < 3; j++ ) {
					i = n;
					while( i-- != 0 ) d[ edge[ j ][ i ] ]++; 
				}

				LOGGER.info( "Checking for duplicate hyperedges..." );
		
				/* Now we quicksort hyperedges lexicographically, keeping into last their permutation. */
				i = n;
				while( i-- != 0 ) last[ i ] = i;
				
				GenericSorting.quickSort( 0, n, new IntComparator() {
					public int compare( final int x, final int y ) {
						int r;
						if ( ( r = edge[ 0 ][ x ] - edge[ 0 ][ y ] ) != 0 ) return r;
						if ( ( r = edge[ 1 ][ x ] - edge[ 1 ][ y ] ) != 0 ) return r;
						return edge[ 2 ][ x ] - edge[ 2 ][ y ];
					}
				},
				new Swapper() {
					public void swap( final int x, final int y ) {
						int e0 = edge[ 0 ][ x ], e1 = edge[ 1 ][ x ], e2 = edge[ 2 ][ x ], p = last[ x ];
						edge[ 0 ][ x ] = edge[ 0 ][ y ];
						edge[ 1 ][ x ] = edge[ 1 ][ y ];
						edge[ 2 ][ x ] = edge[ 2 ][ y ];
						edge[ 0 ][ y ] = e0;
						edge[ 1 ][ y ] = e1;
						edge[ 2 ][ y ] = e2;
						last[ x ] = last[ y ];
						last[ y ] = p;
					}
				}
				);
				
				i = n - 1;
				while( i-- != 0 ) if ( edge[ 0 ][ i + 1 ] == edge[ 0 ][ i ] && edge[ 1 ][ i + 1 ] == edge[ 1 ][ i ] && edge[ 2 ][ i + 1 ] == edge[ 2 ][ i ]) break;

				if ( i != -1 ) {
					LOGGER.info( "Found double hyperedge for terms " + last[ i + 1 ] + " and " + last[ i ] + "." );
					continue;
				}
				
				/* We now invert last and permute all hyperedges back into their place. Note that
				 * we use last and incOffset to speed up the process. */
				i = n;
				while( i-- != 0 ) stack[ last[ i ] ] = i;
				
				GenericPermuting.permute( stack, new Swapper() {
					public void swap( final int x, final int y ) {
						int e0 = edge[ 0 ][ x ], e1 = edge[ 1 ][ x ], e2 = edge[ 2 ][ x ];
						edge[ 0 ][ x ] = edge[ 0 ][ y ];
						edge[ 1 ][ x ] = edge[ 1 ][ y ];
						edge[ 2 ][ x ] = edge[ 2 ][ y ];
						edge[ 0 ][ y ] = e0;
						edge[ 1 ][ y ] = e1;
						edge[ 2 ][ y ] = e2;
					}
				}, last, incOffset
				);
				
				LOGGER.info( "Visiting hypergraph..." );
					 
				/* We set up the offset of each vertex in the incidence
				   vector. This is necessary to avoid creating m incidence vectors at
				   each round. */
				IntArrays.fill( last, 0 );
				incOffset[ 0 ] = 0;

				for( i = 1; i < m; i++ ) incOffset[ i ] = incOffset[ i - 1 ] + d[ i - 1 ];
					 
				/* We fill the vector. */
				for( i = 0; i < n; i++ ) 
					for( j = 0; j < 3; j++ ) {
						v = edge[ j ][ i ];
						inc[ incOffset[ v ] + last[ v ]++ ] = i;
					}
					 
				/* We visit the hypergraph. */
				BooleanArrays.fill( removed, false );

				for( i = 0; i < m; i++ ) if ( d[ i ] == 1 ) visit( i );
					 
				if ( top == n ) LOGGER.info( "Visit completed." );
				else LOGGER.info( "Visit failed: stripped " + top + " hyperedges out of " + n + "." );
			} while ( top != n );
				
			LOGGER.info( "Assigning values..." );

			/* We assign values. */
			/** Whether a specific node has already been used as perfect hash value for an item. */
			final BitVector used = LongArrayBitVector.getInstance( m );
			used.length( m );
			int value;
			
			while( top > 0 ) {
				k = stack[ --top ];

				s = 0;

				for ( j = 0; j < 3; j++ ) {
					if ( ! used.getBoolean( edge[ j ][ k ] ) ) v = j;
					else s += bits.getLong( edge[ j ][ k ] );
					used.set( edge[ j ][ k ] );
				}
				
				value = ( v - s + 9 ) % 3;
				bits.set( edge[ v ][ k ], value == 0 ? 3 : value );
			}

			LOGGER.info( "Completed." );
		}

		final int[] recStackI = new int[ n ]; // The stack for i.
		final int[] recStackK = new int[ n ]; // The stack for k.

		private void visit( int x ) {
			// We cache all variables for faster access
			final int[] recStackI = this.recStackI;
			final int[] recStackK = this.recStackK;
			final int[][] edge = this.edge;
			final int[] last = this.last;
			final int[] inc = this.inc;
			final int[] incOffset = this.incOffset;
			final int[] stack = this.stack;
			final int[] d = this.d;
			final boolean[] removed = this.removed;			

			int i, k = -1;
			boolean inside;

			// Stack initialization
			int recTop = 0; // Initial top of the recursion stack.
			inside = false;
			
			while ( true ) {
				if ( ! inside ) {
					for ( i = 0; i < last[ x ]; i++ ) 
						if ( !removed[ k = inc[ incOffset[ x ] + i ] ] ) break; // The only hyperedge incident on x in the current configuration.

					// TODO: k could be wrong if the graph is regular and cyclic.
					stack[ top++ ] = k;
					
					/* We update the degrees and the incidence lists. */
					removed[ k ] = true;
					for ( i = 0; i < 3; i++ ) d[ edge[ i ][ k ] ]--;
				}
				
				/* We follow recursively the other vertices of the hyperedge, if they have degree one in the current configuration. */
				for( i = 0; i < 3; i++ ) 
					if ( edge[ i ][ k ] != x && d[ edge[ i ][ k ] ] == 1 ) {
						recStackI[ recTop ] = i + 1;
						recStackK[ recTop ] = k;
						recTop++;
						x = edge[ i ][ k ];
						inside = false;
						break;
					}
				if ( i < 3 ) continue;
				
				if ( --recTop < 0 ) return;
				i = recStackI[ recTop ];
				k = recStackK[ recTop ];
				inside = true;
			}
		}
	}



	private void writeObject( final ObjectOutputStream s ) throws IOException {
		s.defaultWriteObject();
		if ( n < STRING_THRESHOLD ) s.writeObject( t );
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException, IllegalArgumentException, SecurityException {
		s.defaultReadObject();
		n4 = n * 4;
		bits = LongArrayBitVector.wrap( array, m * 2 ).asLongBigList( 2 );
		if ( n < STRING_THRESHOLD ) t = (CharSequence[])s.readObject();
	}

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MinimalPerfectMonotoneHash.class.getName(), "Builds a minimal perfect hash table reading a newline-separated list of strings.",
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

		final MinimalPerfectMonotoneHash minimalPerfectHash;

		if ( stringFile == null ) {
			ArrayList<MutableString> stringList = new ArrayList<MutableString>();
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.itemsName = "strings";
			final LineIterator stringIterator = new LineIterator( new FastBufferedReader( new InputStreamReader( System.in, encoding ), bufferSize ), pl );

			pl.start( "Reading strings..." );
			while( stringIterator.hasNext() ) stringList.add( stringIterator.next().copy() );
			pl.done();

			LOGGER.info( "Building minimal perfect hash table..." );
			minimalPerfectHash = new MinimalPerfectMonotoneHash( stringList, sorted ? WEIGHT_UNKNOWN_SORTED_STRINGS : WEIGHT_UNKNOWN );
		}
		else {
			LOGGER.info( "Building minimal perfect hash table..." );
			minimalPerfectHash = new MinimalPerfectMonotoneHash( stringFile, encoding.toString(), sorted ? WEIGHT_UNKNOWN_SORTED_STRINGS : WEIGHT_UNKNOWN, zipped );
		}

		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( minimalPerfectHash, tableName );
		LOGGER.info( "Completed." );
	}


	public boolean containsKey( Object key ) {
		return true;
	}

}
