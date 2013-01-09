package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2013 Sebastiano Vigna 
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

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.util.XorShiftStarRandom;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/**
 * A minimal perfect hash function.
 * 
 * <P>Given a list of elements without duplicates, the constructors of this class finds a minimal
 * perfect hash function for the list. Subsequent calls to the {@link #getLong(Object)} method will
 * return a distinct number for each elements in the list. For elements out of the list, the
 * resulting number is not specified. In some (rare) cases it might be possible to establish that an
 * element was not in the original list, and in that case -1 will be returned. The class can then be
 * saved by serialisation and reused later. 
 * 
 * <p>This class uses a {@linkplain ChunkedHashStore chunked hash store} to provide highly scalable constructors. Note that at construction time
 * you can {@linkplain #MinimalPerfectHashFunction(Iterable, TransformationStrategy, File, ChunkedHashStore) pass}
 * a {@link ChunkedHashStore} containing the elements associated with any value; note that, however, that if the store is rebuilt because of a
 * {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException} it will be rebuilt associating with each key its ordinal position.
 * 
 * <P>The theoretical memory requirements for the algorithm we use are 2{@link HypergraphSorter#GAMMA &gamma;}=2.46 +
 * o(<var>n</var>) bits per element, plus the bits for the random hashes (which are usually
 * negligible). The o(<var>n</var>) part is due to an embedded ranking scheme that increases space
 * occupancy by 0.625%, bringing the actual occupied space to around 2.68 bits per element.
 * 
 * <P>As a commodity, this class provides a main method that reads from standard input a (possibly
 * <samp>gzip</samp>'d) sequence of newline-separated strings, and writes a serialised minimal
 * perfect hash function for the given list.
 * 
 * <h3>How it Works</h3>
 * 
 * <p>The technique used is very similar (but developed independently) to that described by Fabiano
 * C. Botelho, Rasmus Pagh and Nivio Ziviani in &ldquo;Simple and Efficient Minimal Perfect Hashing
 * Functions&rdquo;, <i>Algorithms and data structures: 10th international workshop, WADS 2007</i>,
 * number 4619 of Lecture Notes in Computer Science, pages 139&minus;150, 2007. In turn, the mapping
 * technique described therein was actually first proposed by Bernard Chazelle, Joe Kilian, Ronitt
 * Rubinfeld and Ayellet Tal in &ldquo;The Bloomier Filter: an Efficient Data Structure for Static
 * Support Lookup Tables&rdquo;, <i>Proc. SODA 2004</i>, pages 30&minus;39, 2004, as one of the
 * steps to implement a mutable table.
 * 
 * <p>The basic ingredient is the Majewski-Wormald-Havas-Czech
 * {@linkplain HypergraphSorter 3-hypergraph technique}. After generating a random 3-hypergraph, we
 * {@linkplain HypergraphSorter sort} its 3-hyperedges to that a distinguished vertex in each
 * 3-hyperedge, the <em>hinge</em>, never appeared before. We then assign to each vertex a
 * two-bit number in such a way that for each 3-hyperedge the sum of the values associated to its
 * vertices modulo 3 gives the index of the hash function generating the hinge. As as a result we
 * obtain a perfect hash of the original set (one just has to compute the three hash functions,
 * collect the three two-bit values, add them modulo 3 and take the corresponding hash function
 * value).
 * 
 * <p>To obtain a minimal perfect hash, we simply notice that we whenever we have to assign a value
 * to a vertex, we can take care of using the number 3 instead of 0 if the vertex is actually the
 * output value for some element. The final value of the minimal perfect hash function is the number
 * of nonzero pairs of bits that precede the perfect hash value for the element. To compute this
 * number, we use a simple table-free ranking scheme, recording the number of nonzero pairs each
 * {@link #BITS_PER_BLOCK} bits and modifying the standard broadword algorithm for computing the
 * number of ones in a word into an algorithm that {@linkplain #countNonzeroPairs(long) counts the
 * number of nonzero pairs of bits in a word}.
 * 
 * @author Sebastiano Vigna
 * @since 0.1
 */

public class MinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
	private static final Logger LOGGER = LoggerFactory.getLogger( MinimalPerfectHashFunction.class );

	private static final boolean ASSERTS = false;

	public static final long serialVersionUID = 4L;

	/** The number of bits per block in the rank structure. */
	public static final int BITS_PER_BLOCK = 1024;

	/** The logarithm of the desired chunk size. */
	public final static int LOG2_CHUNK_SIZE = 10;

	private static final long ONES_STEP_4 = 0x1111111111111111L;

	private static final long ONES_STEP_8 = 0x0101010101010101L;

	/** The number of elements. */
	protected final long n;

	/** The shift for chunks. */
	private final int chunkShift;

	/** The seed used to generate the initial hash triple. */
	protected final long globalSeed;

	/** The seed of the underlying 3-hypergraphs. */
	protected final long[] seed;

	/** The start offset of each block. */
	protected final long[] offset;

	/**
	 * The final magick&mdash;the list of modulo-3 values that define the output of the minimal hash
	 * function.
	 */
	protected final LongBigList values;

	/** The bit vector underlying {@link #values}. */
	protected final LongArrayBitVector bitVector;

	/** The bit array supporting {@link #values}. */
	protected transient long[] array;

	/** The number of nonzero bit pairs up to a given block of {@link #BITS_PER_BLOCK} bits. */
	protected final long count[];

	/** The transformation strategy. */
	protected final TransformationStrategy<? super T> transform;

	/**
	 * Creates a new minimal perfect hash table for the given elements.
	 * 
	 * @param elements the elements to hash.
	 * @param transform a transformation strategy for the elements.
	 */

	public MinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) throws IOException {
		this( elements, transform, null, null );
	}

	/**
	 * Creates a new minimal perfect hash table for the given elements.
	 * 
	 * @param elements the elements to hash.
	 * @param transform a transformation strategy for the elements.
	 * @param tempDir a temporary directory for the chunked hash store files, or <code>null</code> for the current directory.
	 */

	public MinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final File tempDir ) throws IOException {
		this( elements, transform, tempDir, null );
	}

	/**
	 * Creates a new minimal perfect hash table for elements provided by a {@link ChunkedHashStore}.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @param chunkedHashStore a checked chunked hash store containing the elements. 
	 */

	public MinimalPerfectHashFunction( final TransformationStrategy<? super T> transform, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this( null, transform, null, chunkedHashStore );
	}
	
	/**
	 * Creates a new minimal perfect hash table for the given elements.
	 * 
	 * @param elements the elements to hash, or <code>null</code>.
	 * @param transform a transformation strategy for the elements.
	 * @param chunkedHashStore a chunked hash store containing the elements, or <code>null</code>; the store
	 * can be unchecked, but in this case <code>elements</code> and <code>transform</code> must be non-<code>null</code>. 
	 */

	public MinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this( elements, transform, null, chunkedHashStore );
	}
	
	/**
	 * Creates a new minimal perfect hash table for the given elements.
	 * 
	 * @param elements the elements to hash, or <code>null</code>.
	 * @param transform a transformation strategy for the elements.
	 * @param tempDir a temporary directory for the store files, or <code>null</code> for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the elements, or <code>null</code>; the store
	 * can be unchecked, but in this case <code>elements</code> and <code>transform</code> must be non-<code>null</code>. 
	 */

	public MinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final File tempDir, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this.transform = transform;

		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayFreeMemory = true;
		final Random r = new XorShiftStarRandom();
		pl.itemsName = "keys";

		final boolean givenChunkedHashStore = chunkedHashStore != null;
		if ( !givenChunkedHashStore ) {
			chunkedHashStore = new ChunkedHashStore<T>( transform, tempDir, pl );
			chunkedHashStore.reset( r.nextLong() );
			chunkedHashStore.addAll( elements.iterator() );
		}
		n = chunkedHashStore.size();

		defRetValue = -1; // For the very few cases in which we can decide

		int log2NumChunks = Math.max( 0, Fast.mostSignificantBit( n >> LOG2_CHUNK_SIZE ) );
		chunkShift = chunkedHashStore.log2Chunks( log2NumChunks );
		final int numChunks = 1 << log2NumChunks;

		LOGGER.debug( "Number of chunks: " + numChunks );

		seed = new long[ numChunks ];
		offset = new long[ numChunks + 1 ];

		bitVector = LongArrayBitVector.getInstance();
		( values = bitVector.asLongBigList( 2 ) ).size( ( (long)Math.ceil( n * HypergraphSorter.GAMMA ) + 4 * numChunks ) );
		array = bitVector.bits();

		int duplicates = 0;

		for ( ;; ) {
			LOGGER.debug( "Generating minimal perfect hash function..." );

			long seed = 0;
			pl.expectedUpdates = numChunks;
			pl.itemsName = "chunks";
			pl.start( "Analysing chunks... " );

			try {
				int q = 0;
				for ( ChunkedHashStore.Chunk chunk : chunkedHashStore ) {
					final HypergraphSorter<BitVector> sorter = new HypergraphSorter<BitVector>( chunk.size(), false );
					do {
						seed = r.nextLong();
					} while ( !sorter.generateAndSort( chunk.iterator(), seed ) );

					this.seed[ q ] = seed;
					offset[ q + 1 ] = offset[ q ] + sorter.numVertices;

					/* We assign values. */
					int top = chunk.size(), k, v = 0;
					final int[] stack = sorter.stack;
					final int[] vertex1 = sorter.vertex1;
					final int[] vertex2 = sorter.vertex2;
					final long off = offset[ q ];

					while ( top > 0 ) {
						v = stack[ --top ];
						k = ( v > vertex1[ v ] ? 1 : 0 ) + ( v > vertex2[ v ] ? 1 : 0 ); 
						if ( ASSERTS ) assert k >= 0 && k < 3 : Integer.toString( k );
						//System.err.println( "<" + v + ", " + vertex1[v] + ", " + vertex2[ v ]+ "> (" + k + ")" );
						final long s = values.getLong( off + vertex1[ v ] ) + values.getLong( off + vertex2[ v ] );
						final long value = ( k - s + 9 ) % 3;
						if ( ASSERTS ) assert values.getLong( off + v ) == 0;
						values.set( off + v, value == 0 ? 3 : value );
					}

					q++;
					pl.update();

					if ( ASSERTS ) {
						final IntOpenHashSet pos = new IntOpenHashSet();
						final int[] e = new int[ 3 ];
						for ( long[] triple : chunk ) {
							HypergraphSorter.tripleToEdge( triple, seed, sorter.numVertices, sorter.partSize, e );
							assert pos.add( e[ (int)( values.getLong( off + e[ 0 ] ) + values.getLong( off + e[ 1 ] ) + values.getLong( off + e[ 2 ] ) ) % 3 ] );
						}
					}
				}

				pl.done();
				break;
			}
			catch ( ChunkedHashStore.DuplicateException e ) {
				if ( elements == null ) throw new IllegalStateException( "You provided no elements, but the chunked hash store was not checked" );
				if ( duplicates++ > 3 ) throw new IllegalArgumentException( "The input list contains duplicates" );
				LOGGER.warn( "Found duplicate. Recomputing triples..." );
				chunkedHashStore.reset( r.nextLong() );
				chunkedHashStore.addAll( elements.iterator() );
			}
		}

		globalSeed = chunkedHashStore.seed();

		if ( !givenChunkedHashStore ) chunkedHashStore.close();

		if ( n > 0 ) {
			long m = values.size64();
			count = new long[ (int)( ( 2L * m + BITS_PER_BLOCK - 1 ) / BITS_PER_BLOCK ) ];
			long c = 0;

			final int numWords = (int)( ( 2L * m + Long.SIZE - 1 ) / Long.SIZE );
			for ( int i = 0; i < numWords; i++ ) {
				if ( ( i & ( BITS_PER_BLOCK / Long.SIZE - 1 ) ) == 0 ) count[ i / ( BITS_PER_BLOCK / Long.SIZE ) ] = c;
				c += countNonzeroPairs( array[ i ] );
			}

			if ( ASSERTS ) {
				int k = 0;
				for ( int i = 0; i < m; i++ ) {
					assert rank( i ) == k : "(" + i + ") " + k + " != " + rank( i );
					if ( values.getLong( i ) != 0 ) k++;
					assert k <= n;
				}

				final Iterator<? extends T> iterator = elements.iterator();
				for ( int i = 0; i < n; i++ )
					assert getLong( iterator.next() ) < n;
			}
		}
		else count = LongArrays.EMPTY_ARRAY;

		LOGGER.info( "Completed." );
		LOGGER.debug( "Forecast bit cost per element: " + ( 2 * HypergraphSorter.GAMMA + 2. * Long.SIZE / BITS_PER_BLOCK ) );
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / n );
	}

	/**
	 * Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return values.size64() * 2 + count.length * (long)Long.SIZE + offset.length * (long)Long.SIZE + seed.length * (long)Long.SIZE;
	}



	/**
	 * Creates a new minimal perfect hash by copying a given one; non-transient fields are (shallow)
	 * copied.
	 * 
	 * @param mph the perfect hash to be copied.
	 */
	protected MinimalPerfectHashFunction( final MinimalPerfectHashFunction<T> mph ) {
		this.n = mph.n;
		this.seed = mph.seed;
		this.offset = mph.offset;
		this.bitVector = mph.bitVector;
		this.globalSeed = mph.globalSeed;
		this.chunkShift = mph.chunkShift;
		this.values = mph.values;
		this.array = mph.array;
		this.count = mph.count;
		this.transform = mph.transform.copy();
	}

	/**
	 * Counts the number of nonzero pairs of bits in a long.
	 * 
	 * <p>This method uses a very simple modification of the classical
	 * broadword algorithm to count the number of ones in a word.
	 * Usually, in the first step of the algorithm one computes the number of ones in each pair of
	 * bits. Instead, we compute one iff at least one of the bits in the pair is nonzero.
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

	private long rank( long x ) {
		x *= 2;
		final int word = (int)( x / Long.SIZE );
		long rank = count[ word / ( BITS_PER_BLOCK / Long.SIZE ) ];
		int wordInBlock = word & ~( ( BITS_PER_BLOCK / Long.SIZE ) - 1 );
		while ( wordInBlock < word )
			rank += countNonzeroPairs( array[ wordInBlock++ ] );

		return rank + countNonzeroPairs( array[ word ] & ( 1L << x % Long.SIZE ) - 1 );
	}

	@SuppressWarnings("unchecked")
	public long getLong( final Object key ) {
		if ( n == 0 ) return defRetValue;
		final int[] e = new int[ 3 ];
		final long[] h = new long[ 3 ];
		Hashes.jenkins( transform.toBitVector( (T)key ), globalSeed, h );
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( h[ 0 ] >>> chunkShift );
		final long chunkOffset = offset[ chunk ];
		HypergraphSorter.tripleToEdge( h, seed[ chunk ], (int)( offset[ chunk + 1 ] - chunkOffset ), e );
		if ( e[ 0 ] == -1 ) return defRetValue;
		final long result = rank( chunkOffset + e[ (int)( values.getLong( e[ 0 ] + chunkOffset ) + values.getLong( e[ 1 ] + chunkOffset ) + values.getLong( e[ 2 ] + chunkOffset ) ) % 3 ] );
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < n ? result : defRetValue;
	}

	public long getLongByTriple( final long[] triple ) {
		if ( n == 0 ) return defRetValue;
		final int[] e = new int[ 3 ];
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( triple[ 0 ] >>> chunkShift );
		final long chunkOffset = offset[ chunk ];
		HypergraphSorter.tripleToEdge( triple, seed[ chunk ], (int)( offset[ chunk + 1 ] - chunkOffset ), e );
		if ( e[ 0 ] == -1 ) return defRetValue;
		final long result = rank( chunkOffset + e[ (int)( values.getLong( e[ 0 ] + chunkOffset ) + values.getLong( e[ 1 ] + chunkOffset ) + values.getLong( e[ 2 ] + chunkOffset ) ) % 3 ] );
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < n ? result : defRetValue;
	}

	public long size64() {
		return n;
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		array = bitVector.bits();
	}


	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MinimalPerfectHashFunction.class.getName(), "Builds a minimal perfect hash function reading a newline-separated list of strings.", new Parameter[] {
				new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
				new FlaggedOption( "tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files." ),
				new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
				new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
				new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash function." ),
				new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY,
						"The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ), } );

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
		final TransformationStrategy<CharSequence> transformationStrategy = iso ? TransformationStrategies.iso() : TransformationStrategies.utf16();

		BinIO.storeObject( new MinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy, jsapResult.getFile( "tempDir") ), functionName );
		LOGGER.info( "Completed." );
	}
}
