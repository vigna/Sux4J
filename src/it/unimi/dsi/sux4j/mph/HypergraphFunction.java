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

/** A read-only function stored using hypergraph techniques.
 * 
 * @author Sebastiano Vigna
 * @since 0.2
 */

public class HypergraphFunction<T> extends AbstractHash<T> implements Serializable {
    public static final long serialVersionUID = 1L;

	private static final Logger LOGGER = Fast.getLogger( HypergraphFunction.class );
		
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
