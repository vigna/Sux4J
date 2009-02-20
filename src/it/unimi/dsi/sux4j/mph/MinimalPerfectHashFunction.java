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

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.LongBigList;

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

/** A minimal perfect hash function.
 *
 * <P>Given a list of elements without duplicates, the constructors of this class finds a minimal perfect hash function for
 * the list. Subsequent calls to the {@link #getLong(Object)} method will return a distinct
 * number for each elements in the list. For elements out of the list, the resulting number is not specified. In
 * some (rare) cases it might be possible to establish that an element was not in the original list, and
 * in that case -1 will be returned. The class can then be saved by serialisation and reused later.
 *
 * <P>The theoretical memory requirements are 2{@link HypergraphSorter#GAMMA &gamma;}=2.46 + o(<var>n</var>) bits per element, plus the bits
 * for the random hashes (which are usually negligible). The o(<var>n</var>) part is due to
 * an embedded ranking scheme that increases space 
 * occupancy by 0.625%, bringing the actual occupied space to around 2.65 bits per element.
 * 
 * <P>As a commodity, this class provides a main method that reads from
 * standard input a (possibly <samp>gzip</samp>'d) sequence of newline-separated strings, and
 * writes a serialised minimal perfect hash function for the given list.
 *
 * <h3>How it Works</h3>
 * 
 * <p>The technique used is very similar (but developed independently) to that described by Botelho, Pagh and Ziviani
 * in &ldquo;Simple and Efficient Minimal Perfect Hashing Functions&rdquo;, <i>Proc. WADS 2007</i>, number
 * 4619 of Lecture Notes in Computer Science, pages 139&minus;150, 2007. In turn, the mapping technique described
 * therein was actually first proposed by Chazelle, Kilian, Rubinfeld and Tal in 
 * &ldquo;The Bloomier Filter: an Efficient Data Structure for Static Support Lookup 
 * Tables&rdquo;, <i>Proc. SODA 2004</i>, pages 30&minus;39, 2004, as one of the steps to implement a mutable table.
 * 
 * <p>The basic ingredient is the Majewski-Wormald-Havas-Czech {@linkplain HypergraphSorter 3-hypergraph technique}.
 * After generating a random 3-hypergraph with suitably sorted edges,
 * we assign to each vertex a two-bit number in such a way that for each 3-hyperedge the sum of the values
 * associated to its vertices modulo 3 gives the index of the hash function generating the hinge.
 * The hinge is distinct for each 3-hyperedge, and as a result we obtain a perfect hash 
 * of the original set (one just has to compute the three hash functions, 
 * collect the three two-bit values, add them modulo 3 and take the corresponding hash function value).
 * 
 * <p>To obtain a minimal perfect hash, we simply notice that we whenever we have to assign a value to a vertex,
 * we can take care of using the number 3 instead of 0 if the vertex is actually the output value for some element. As
 * a result, the final value of the minimal perfect hash function is the number of nonzero pairs of bits that precede
 * the perfect hash value for the element. 
 * To compute this number, we use a simple table-free ranking scheme, recording the number
 * of nonzero pairs each {@link #BITS_PER_BLOCK} bits and modifying the
 * standard broadword algorithm for computing the number of ones
 * in a word into an algorithm that {@linkplain #countNonzeroPairs(long) counts
 * the number of nonzero pairs of bits in a word}.
 *
 * @author Sebastiano Vigna
 * @since 0.1
 */

public class MinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
	private static final Logger LOGGER = Util.getLogger( MinimalPerfectHashFunction.class );
	private static final boolean ASSERTS = false;
	public static final long serialVersionUID = 1L;

	/** The number of bits per block in the rank structure. */
	public static final int BITS_PER_BLOCK = 512;

	private static final long ONES_STEP_4 = 0x1111111111111111L;
	private static final long ONES_STEP_8 = 0x0101010101010101L;
	
	/** The number of elements. */
	final protected int n;
	/** The number of vertices of the intermediate hypergraph. */
	final protected int m;
	/** The seed of the underlying 3-hypergraph. */
	final protected long seed;
	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal hash function. */
	final protected LongBigList values;
	/** The bit array supporting {@link #values}. */
	final protected long[] array;
	/** The number of nonzero bit pairs up to a given block of {@link #BITS_PER_BLOCK} bits. */
	final protected int count[];
	/** The transformation strategy. */
	final protected TransformationStrategy<? super T> transform;
    

	/** Creates a new minimal perfect hash table for the given elements.
	 *
	 * @param elements the elements to hash.
	 * @param transform a transformation strategy for the elements.
	 */

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public MinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) {

		// First of all we compute the size, either by size(), if possible, or simply by iterating.
		if ( elements instanceof Collection ) n = ((Collection<? extends T>)elements).size();
		else {
			int c = 0;
			// Do not add a suppression annotation--it breaks javac
			for( T o: elements ) c++;
			n = c;
		}
		
		defRetValue = -1; // For the very few cases in which we can decide
		this.transform = transform;

		HypergraphSorter<T> sorter = new HypergraphSorter<T>( n );
		m = sorter.numVertices;
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance( m * 2 );
		values = bitVector.asLongBigList( 2 );
		values.size( m );
		array = bitVector.bits();

		final Random r = new Random();

		long seed;
		do {
			LOGGER.info( "Generating random hypergraph..." );
			seed = r.nextLong();
		} while ( sorter.generateAndSort( elements.iterator(), transform, seed ) != HypergraphSorter.Result.OK );
		
		this.seed = seed;
		
		/* We assign values. */
		
		/** Whether a specific node has already been used as perfect hash value for an item. */
		final BitVector used = LongArrayBitVector.getInstance().length( m );
		int value;
		
		final int[] stack = sorter.stack;
		final int[][] edge = sorter.edge;
		int top = n, k, s, v = 0;
		while( top > 0 ) {
			k = stack[ --top ];

			s = 0;

			for ( int j = 0; j < 3; j++ ) {
				if ( ! used.getBoolean( edge[ j ][ k ] ) ) v = j;
				else s += values.getLong( edge[ j ][ k ] );
				used.set( edge[ j ][ k ] );
			}
			
			value = ( v - s + 9 ) % 3;
			values.set( edge[ v ][ k ], value == 0 ? 3 : value );
		}

		count = new int[ ( 2 * m + BITS_PER_BLOCK - 1 ) / BITS_PER_BLOCK ];
		int c = 0;
		
		for( int i = 0; i <= ( 2 * m ) / Long.SIZE; i++ ) {
			if ( ( i & 7 ) == 0 ) count[ i / 8 ] = c;
			c += countNonzeroPairs( array[ i ] );
		}
		
		if ( ASSERTS ) {
			k = 0;
			for( int i = 0; i < m; i++ ) {
				assert rank( i ) == k : "(" + i + ") " + k + " != " + rank( i ); 
				if ( values.getLong( i ) != 0 ) k++;
				assert k <= n;
			}
			
			final Iterator<? extends T> iterator = elements.iterator();
			for( int i = 0; i < n; i++ ) assert getLong( iterator.next() ) < n;
		}

		LOGGER.info( "Completed." );
		LOGGER.debug( "Forecast bit cost per element: " + ( 2 * HypergraphSorter.GAMMA + 2 * (double)Integer.SIZE / BITS_PER_BLOCK ) );
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / n );
	}

	/** Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return values.length() * 2 + count.length * Integer.SIZE;
	}



	/** Creates a new minimal perfect hash by copying a given one; non-transient fields are (shallow) copied.
	 * 
	 * @param mph the perfect hash to be copied.
	 */
	protected MinimalPerfectHashFunction( final MinimalPerfectHashFunction<T> mph ) {
		this.n = mph.n;
		this.m = mph.m;
		this.seed = mph.seed;
		this.values = mph.values;
		this.array = mph.array;
		this.count = mph.count;
		this.transform = mph.transform.copy();
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
		x *= 2;
		final int word = (int)( x / Long.SIZE );
		int rank = count[ word / 8 ];
		int wordInBlock = word & ~7;
		while( wordInBlock < word ) rank += countNonzeroPairs( array[ wordInBlock++ ] );
		
		return rank + countNonzeroPairs( array[ word ] & ( 1L << x % Long.SIZE ) - 1 );
	}

	@SuppressWarnings("unchecked")
	public long getLong( Object key ) {
		final int[] e = new int[ 3 ];
		HypergraphSorter.bitVectorToEdge( transform.toBitVector( (T)key ), seed, m, e );
		final long result = rank( e[ (int)( values.getLong( e[ 0 ] ) + values.getLong( e[ 1 ] ) + values.getLong( e[ 2 ] ) ) % 3 ] );
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < n ? result : defRetValue;
	}

	public int size() {
		return n;
	}

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MWHCFunction.class.getName(),  "Builds a minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding (i.e., just use the lower eight bits of each character)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash function." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );

		final Collection<MutableString> collection;
		if ( "-".equals( stringFile ) ) {
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.start( "Loading strings..." );
			collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
			pl.done();
		}
		else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
		final TransformationStrategy<CharSequence> transformationStrategy = iso
				? TransformationStrategies.iso() 
				: TransformationStrategies.utf16();

		BinIO.storeObject( new MinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy ), functionName );
		LOGGER.info( "Completed." );
	}
}
