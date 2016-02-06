package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2015 Sebastiano Vigna 
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
import it.unimi.dsi.big.io.FileLinesByteArrayCollection;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.random.RandomGenerator;
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
 * <P>Given a list of keys without duplicates, the {@linkplain Builder builder} of this class finds a minimal
 * perfect hash function for the list. Subsequent calls to the {@link #getLong(Object)} method will
 * return a distinct number for each key in the list. For keys out of the list, the
 * resulting number is not specified. In some (rare) cases it might be possible to establish that a
 * key was not in the original list, and in that case -1 will be returned;
 * by <em>signing</em> the function (see below), you can guarantee with a prescribed probability
 * that -1 will be returned on keys not in the original list. The class can then be
 * saved by serialisation and reused later. 
 * 
 * <p>This class uses a {@linkplain ChunkedHashStore chunked hash store} to provide highly scalable construction. Note that at construction time
 * you can {@linkplain Builder#store(ChunkedHashStore) pass a ChunkedHashStore}
 * containing the keys (associated with any value); however, if the store is rebuilt because of a
 * {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException} it will be rebuilt associating with each key its ordinal position.
 * 
 * <P>The theoretical memory requirements for the algorithm we use are 2{@link HypergraphSorter#GAMMA &gamma;}=2.46 +
 * o(<var>n</var>) bits per key, plus the bits for the random hashes (which are usually
 * negligible). The o(<var>n</var>) part is due to an embedded ranking scheme that increases space
 * occupancy by 6.25%, bringing the actual occupied space to around 2.68 bits per key.
 * 
 * <P>As a commodity, this class provides a main method that reads from standard input a (possibly
 * <code>gzip</code>'d) sequence of newline-separated strings, and writes a serialised minimal
 * perfect hash function for the given list.
 * 
 * <h3>Signing</h3>
 * 
 * <p>Optionally, it is possible to {@linkplain Builder#signed(int) <em>sign</em>} the minimal perfect hash function. A <var>w</var>-bit signature will
 * be associated with each key, so that {@link #getLong(Object)} will return -1 on strings that are not
 * in the original key set. As usual, false positives are possible with probability 2<sup>-<var>w</var></sup>.
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
 * output value for some key. The final value of the minimal perfect hash function is the number
 * of nonzero pairs of bits that precede the perfect hash value for the key. To compute this
 * number, we use a simple table-free ranking scheme, recording the number of nonzero pairs each
 * {@link #BITS_PER_BLOCK} bits and using {@link Long#bitCount(long)} to 
 * {@linkplain #countNonzeroPairs(long) count the number of nonzero pairs of bits in a word}.
 * 
 * @author Sebastiano Vigna
 * @since 0.1
 */

public class MinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
	public static final long serialVersionUID = 5L;
	private static final Logger LOGGER = LoggerFactory.getLogger( MinimalPerfectHashFunction.class );
	private static final boolean ASSERTS = false;

	/** The local seed is generated using this step, so to be easily embeddable in {@link #edgeOffsetAndSeed}. */
	private static final long SEED_STEP = 1L << 56;
	/** The lowest 56 bits of {@link #edgeOffsetAndSeed} contain the number of keys stored up to the given chunk. */
	private static final long OFFSET_MASK = -1L >>> 8;

	/** The ratio between vertices and hyperedges. */
	private static double C = 1.09 + 0.01;
	/** Fixed-point representation of {@link #C}. */
	private static int C_TIMES_256 = (int)Math.floor( C * 256 );

	/**
	 * Counts the number of nonzero pairs of bits in a long.
	 * 
	 * @param x a long.
	 * @return the number of nonzero bit pairs in <code>x</code>.
	 */

	public final static int countNonzeroPairs( final long x ) {
		return Long.bitCount( ( x | x >>> 1 ) & 0x5555555555555555L );
	}

	/** Counts the number of nonzero pairs between two positions in the given arrays,
	 * which represents a sequence of two-bit values. 
	 * 
	 * @param start start position (inclusive).
	 * @param end end position (exclusive).
	 * @param array an array of longs containing 2-bit values.
	 * @return the number of nonzero 2-bit values between {@code start} and {@code end}.
	 */
	private final static long countNonzeroPairs( final long start, final long end, final long[] array ) {
		int block = (int)( start / 32 );
		final int endBlock = (int)( end / 32 );
		final int startOffset = (int)( start % 32 );
		final int endOffset = (int)( end % 32 ); 
		
		if ( block == endBlock ) return countNonzeroPairs( ( array[ block ] & ( 1L << endOffset * 2 ) - 1 ) >>> startOffset * 2 );

		long pairs = 0;	
		if ( startOffset != 0 ) pairs += countNonzeroPairs( array[ block++ ] >>> startOffset * 2 );		
		while( block < endBlock ) pairs += countNonzeroPairs( array[ block++ ] );
		if ( endOffset != 0 ) pairs += countNonzeroPairs( array[ block ] & ( 1L << endOffset * 2 ) - 1 );
		
		return pairs;
	}

	/** A builder class for {@link MinimalPerfectHashFunction}. */
	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected int signatureWidth;
		protected File tempDir;
		protected ChunkedHashStore<T> chunkedHashStore;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;
		
		/** Specifies the keys to hash; if you have specified a {@link #store(ChunkedHashStore) ChunkedHashStore}, it can be {@code null}.
		 * 
		 * @param keys the keys to hash.
		 * @return this builder.
		 */
		public Builder<T> keys( final Iterable<? extends T> keys ) {
			this.keys = keys;
			return this;
		}
		
		/** Specifies the transformation strategy for the {@linkplain #keys(Iterable) keys to hash}.
		 * 
		 * @param transform a transformation strategy for the {@linkplain #keys(Iterable) keys to hash}.
		 * @return this builder.
		 */
		public Builder<T> transform( final TransformationStrategy<? super T> transform ) {
			this.transform = transform;
			return this;
		}
		
		/** Specifies that the resulting {@link MinimalPerfectHashFunction} should be signed using a given number of bits per key.
		 * 
		 * @param signatureWidth a signature width, or 0 for no signature.
		 * @return this builder.
		 */
		public Builder<T> signed( final int signatureWidth ) {
			this.signatureWidth = signatureWidth;
			return this;
		}
		
		/** Specifies a temporary directory for the {@link #store(ChunkedHashStore) ChunkedHashStore}.
		 * 
		 * @param tempDir a temporary directory for the {@link #store(ChunkedHashStore) ChunkedHashStore} files, or {@code null} for the standard temporary directory.
		 * @return this builder.
		 */
		public Builder<T> tempDir( final File tempDir ) {
			this.tempDir = tempDir;
			return this;
		}
		
		/** Specifies a chunked hash store containing the keys.
		 * 
		 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
		 * can be unchecked, but in this case you must specify {@linkplain #keys(Iterable) keys} and a {@linkplain #transform(TransformationStrategy) transform}
		 * (otherwise, in case of a hash collision in the store an {@link IllegalStateException} will be thrown). 
		 * @return this builder.
		 */
		public Builder<T> store( final ChunkedHashStore<T> chunkedHashStore ) {
			this.chunkedHashStore = chunkedHashStore;
			return this;
		}
		
		/** Builds a minimal perfect hash function.
		 * 
		 * @return a {@link MinimalPerfectHashFunction} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public MinimalPerfectHashFunction<T> build() throws IOException {
			if ( built ) throw new IllegalStateException( "This builder has been already used" );
			built = true;
			if ( transform == null ) {
				if ( chunkedHashStore != null ) transform = chunkedHashStore.transform();
				else throw new IllegalArgumentException( "You must specify a TransformationStrategy, either explicitly or via a given ChunkedHashStore" );
			}
			return new MinimalPerfectHashFunction<T>( keys, transform, signatureWidth, tempDir, chunkedHashStore );
		}
	}
	
	/** The logarithm of the desired chunk size. */
	public final static int LOG2_CHUNK_SIZE = 10;

	/** The number of keys. */
	protected final long n;

	/** The shift for chunks. */
	private final int chunkShift;

	/** The seed used to generate the initial hash triple. */
	protected final long globalSeed;

	/** A long containing the cumulating function of the chunk edges in the lower 56 bits, 
	 * and the local seed of each chunk in the upper 8 bits. The method {@link #vertexOffset(long)}
	 * returns the chunk (i.e., vertex) cumulative value starting from the edge cumulative value. */
	protected final long[] edgeOffsetAndSeed;

	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal perfect hash function. */
	protected final LongBigList values;

	/** The bit vector underlying {@link #values}. */
	protected final LongArrayBitVector bitVector;

	/** The bit array supporting {@link #values}. */
	protected transient long[] array;

	/** The transformation strategy. */
	protected final TransformationStrategy<? super T> transform;
	
	/** The mask to compare signatures, or zero for no signatures. */
	protected final long signatureMask;
	
	/** The signatures. */
	protected final LongBigList signatures;

	protected static long vertexOffset( final long edgeOffsetSeed ) {
		 return ( ( edgeOffsetSeed & OFFSET_MASK ) * C_TIMES_256 >> 8 );
	}
	
	/**
	 * Creates a new minimal perfect hash function for the given keys.
	 * 
	 * @param keys the keys to hash.
	 * @param transform a transformation strategy for the keys.
	 * @deprecated Please use the new {@linkplain Builder builder}.
	 */
	@Deprecated
	public MinimalPerfectHashFunction( final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform ) throws IOException {
		this( keys, transform, null, null );
	}

	/**
	 * Creates a new minimal perfect hash function for the given keys.
	 * 
	 * @param keys the keys to hash.
	 * @param transform a transformation strategy for the keys.
	 * @param tempDir a temporary directory for the chunked hash store files, or {@code null} for the current directory.
	 * @deprecated Please use the new {@linkplain Builder builder}.
	 */
	@Deprecated
	public MinimalPerfectHashFunction( final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final File tempDir ) throws IOException {
		this( keys, transform, tempDir, null );
	}

	/**
	 * Creates a new minimal perfect hash function for keys provided by a {@link ChunkedHashStore}.
	 * 
	 * @param transform a transformation strategy for the keys.
	 * @param chunkedHashStore a checked chunked hash store containing the keys. 
	 * @deprecated Please use the new {@linkplain Builder builder}.
	 */
	@Deprecated
	public MinimalPerfectHashFunction( final TransformationStrategy<? super T> transform, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this( null, transform, null, chunkedHashStore );
	}
	
	/**
	 * Creates a new minimal perfect hash function for the given keys.
	 * 
	 * @param keys the keys to hash, or {@code null}.
	 * @param transform a transformation strategy for the keys.
	 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
	 * can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be non-{@code null}. 
	 * @deprecated Please use the new {@linkplain Builder builder}.
	 */
	@Deprecated
	public MinimalPerfectHashFunction( final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this( keys, transform, null, chunkedHashStore );
	}

	/**
	 * Creates a new minimal perfect hash function for the given keys.
	 * 
	 * @param keys the keys to hash, or {@code null}.
	 * @param transform a transformation strategy for the keys.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
	 * can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be non-{@code null}. 
	 * @deprecated Please use the new {@linkplain Builder builder}.
	 */
	@Deprecated
	public MinimalPerfectHashFunction( final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final File tempDir, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this( keys, transform, 0, tempDir, chunkedHashStore );
	}

	/**
	 * Creates a new minimal perfect hash function for the given keys.
	 * 
	 * @param keys the keys to hash, or {@code null}.
	 * @param transform a transformation strategy for the keys.
	 * @param signatureWidth a signature width, or 0 for no signature.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
	 * can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be non-{@code null}. 
	 */
	protected MinimalPerfectHashFunction( final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final int signatureWidth, final File tempDir, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this.transform = transform;

		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		final RandomGenerator r = new XorShift1024StarRandomGenerator();
		pl.itemsName = "keys";

		final boolean givenChunkedHashStore = chunkedHashStore != null;
		if ( !givenChunkedHashStore ) {
			chunkedHashStore = new ChunkedHashStore<T>( transform, tempDir, pl );
			chunkedHashStore.reset( r.nextLong() );
			chunkedHashStore.addAll( keys.iterator() );
		}
		n = chunkedHashStore.size();

		defRetValue = -1; // For the very few cases in which we can decide

		int log2NumChunks = Math.max( 0, Fast.mostSignificantBit( n >> LOG2_CHUNK_SIZE ) );
		chunkShift = chunkedHashStore.log2Chunks( log2NumChunks );
		final int numChunks = 1 << log2NumChunks;

		LOGGER.debug( "Number of chunks: " + numChunks );

		edgeOffsetAndSeed = new long[ numChunks + 1 ];

		bitVector = LongArrayBitVector.getInstance();
		( values = bitVector.asLongBigList( 2 ) ).size( n * C_TIMES_256 >> 8 );
		array = bitVector.bits();

		int duplicates = 0;

		for ( ;; ) {
			LOGGER.debug( "Generating minimal perfect hash function..." );

			pl.expectedUpdates = numChunks;
			pl.itemsName = "chunks";
			pl.start( "Analysing chunks... " );

			try {
				int q = 0;
				long undirectable = 0, unsolvable = 0;
				for ( ChunkedHashStore.Chunk chunk : chunkedHashStore ) {

					edgeOffsetAndSeed[ q + 1 ] = edgeOffsetAndSeed[ q ] + chunk.size();

					long seed = 0;
					final long off = vertexOffset( edgeOffsetAndSeed[ q ] );
					final HypergraphSolver<BitVector> solver = 
							new HypergraphSolver<BitVector>( (int)( vertexOffset( edgeOffsetAndSeed[ q + 1 ] ) - off ), chunk.size() );

					for(;;) {
						final boolean solved = solver.generateAndSolve( chunk, seed );
						undirectable += solver.undirectable;
						unsolvable += solver.unsolvable;
						if ( solved ) break;
						seed += SEED_STEP;
						if ( seed == 0 ) throw new AssertionError( "Exhausted local seeds" );
					}

					this.edgeOffsetAndSeed[ q ] |= seed;
					final long[] solution = solver.solution;
					for( int i = 0; i < solution.length; i++ ) values.set( i + off, solution[ i ] );
					q++;
					
					pl.update();

					if ( ASSERTS ) {
						final IntOpenHashSet pos = new IntOpenHashSet();
						final int[] e = new int[ 3 ];
						for ( long[] triple : chunk ) {
							HypergraphSolver.tripleToEdge( triple, seed, solver.numVertices, e );
							
							assert pos.add( e[ (int)( values.getLong( off + e[ 0 ] ) + values.getLong( off + e[ 1 ] ) + values.getLong( off + e[ 2 ] ) ) % 3 ] ) :
								"<" + e[ 0 ] + "," + e[ 1 ] + "," + e[ 2 ] + ">: " + e[ (int)( values.getLong( off + e[ 0 ] ) + values.getLong( off + e[ 1 ] ) + values.getLong( off + e[ 2 ] ) ) % 3 ];
						}
					}
				}

				LOGGER.info( "Undirectable graphs: " + undirectable + "/" + numChunks + " (" + Util.format( 100.0 * undirectable / numChunks ) + "%)");
				LOGGER.info( "Unsolvable graphs: " + unsolvable + "/" + numChunks + " (" + Util.format( 100.0 * unsolvable / numChunks ) + "%)");
				
				pl.done();
				break;
			}
			catch ( ChunkedHashStore.DuplicateException e ) {
				if ( keys == null ) throw new IllegalStateException( "You provided no keys, but the chunked hash store was not checked" );
				if ( duplicates++ > 3 ) throw new IllegalArgumentException( "The input list contains duplicates" );
				LOGGER.warn( "Found duplicate. Recomputing triples..." );
				chunkedHashStore.reset( r.nextLong() );
				chunkedHashStore.addAll( keys.iterator() );
			}
		}

		globalSeed = chunkedHashStore.seed();

		LOGGER.info( "Completed." );
		LOGGER.debug( "Forecast bit cost per key: " + 2 * C + 64. / 512 );
		LOGGER.info( "Actual bit cost per key: " + (double)numBits() / n );


		if ( signatureWidth != 0 ) {
			signatureMask = -1L >>> Long.SIZE - signatureWidth;
			( signatures = LongArrayBitVector.getInstance().asLongBigList( signatureWidth ) ).size( n );
			pl.expectedUpdates = n;
			pl.itemsName = "signatures";
			pl.start( "Signing..." );
			for ( ChunkedHashStore.Chunk chunk : chunkedHashStore ) {
				Iterator<long[]> iterator = chunk.iterator();
				for( int i = chunk.size(); i-- != 0; ) { 
					final long[] triple = iterator.next();
					final int[] e = new int[ 3 ];
					signatures.set( getLongByTripleNoCheck( triple, e ), signatureMask & triple[ 0 ] );
					pl.lightUpdate();
				}
			}
			pl.done();
		}
		else {
			signatureMask = 0;
			signatures = null;
		}

		if ( !givenChunkedHashStore ) chunkedHashStore.close();
	}

	/**
	 * Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return values.size64() * 2 + edgeOffsetAndSeed.length * (long)Long.SIZE;
	}

	/**
	 * Creates a new minimal perfect hash by copying a given one; non-transient fields are (shallow)
	 * copied.
	 * 
	 * @param mph the perfect hash to be copied.
	 * @deprecated Unused.
	 */
	@Deprecated
	protected MinimalPerfectHashFunction( final MinimalPerfectHashFunction<T> mph ) {
		this.n = mph.n;
		this.edgeOffsetAndSeed = mph.edgeOffsetAndSeed;
		this.bitVector = mph.bitVector;
		this.globalSeed = mph.globalSeed;
		this.chunkShift = mph.chunkShift;
		this.values = mph.values;
		this.array = mph.array;
		this.transform = mph.transform.copy();
		this.signatureMask = mph.signatureMask;
		this.signatures = mph.signatures;
	}

	@SuppressWarnings("unchecked")
	public long getLong( final Object key ) {
		if ( n == 0 ) return defRetValue;
		final int[] e = new int[ 3 ];
		final long[] h = new long[ 3 ];
		Hashes.spooky4( transform.toBitVector( (T)key ), globalSeed, h );
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( h[ 0 ] >>> chunkShift );
		final long edgeOffsetSeed = edgeOffsetAndSeed[ chunk ];
		final long chunkOffset = vertexOffset( edgeOffsetSeed );
		HypergraphSolver.tripleToEdge( h, edgeOffsetSeed & ~OFFSET_MASK, (int)( vertexOffset( edgeOffsetAndSeed[ chunk + 1 ] ) - chunkOffset ), e );
		if ( e[ 0 ] == -1 ) return defRetValue;
		final long result = ( edgeOffsetSeed & OFFSET_MASK ) + countNonzeroPairs( chunkOffset, chunkOffset + e[ (int)( values.getLong( e[ 0 ] + chunkOffset ) + values.getLong( e[ 1 ] + chunkOffset ) + values.getLong( e[ 2 ] + chunkOffset ) ) % 3 ], array );
		if ( signatureMask != 0 ) return result >= n || ( ( signatures.getLong( result ) ^ h[ 0 ] ) & signatureMask ) != 0 ? defRetValue : result;
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < n ? result : defRetValue;
	}

	/** Low-level access to the output of this minimal perfect hash function.
	 *
	 * <p>This method makes it possible to build several kind of functions on the same {@link ChunkedHashStore} and
	 * then retrieve the resulting values by generating a single triple of hashes. The method 
	 * {@link TwoStepsMWHCFunction#getLong(Object)} is a good example of this technique.
	 *
	 * @param triple a triple generated as documented in {@link ChunkedHashStore}.
	 * @return the output of the function.
	 */
	public long getLongByTriple( final long[] triple ) {
		if ( n == 0 ) return defRetValue;
		final int[] e = new int[ 3 ];
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( triple[ 0 ] >>> chunkShift );
		final long edgeOffsetSeed = edgeOffsetAndSeed[ chunk ];
		final long chunkOffset = vertexOffset( edgeOffsetSeed );
		HypergraphSolver.tripleToEdge( triple, edgeOffsetSeed & ~OFFSET_MASK, (int)( vertexOffset( edgeOffsetAndSeed[ chunk + 1 ] ) - chunkOffset ), e );
		if ( e[ 0 ] == -1 ) return defRetValue;
		final long result = ( edgeOffsetSeed & OFFSET_MASK ) + countNonzeroPairs( chunkOffset, chunkOffset + e[ (int)( values.getLong( e[ 0 ] + chunkOffset ) + values.getLong( e[ 1 ] + chunkOffset ) + values.getLong( e[ 2 ] + chunkOffset ) ) % 3 ], array );
		if ( signatureMask != 0 ) return result >= n || signatures.getLong( result ) != ( triple[ 0 ] & signatureMask ) ? defRetValue : result;
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < n ? result : defRetValue;
	}

	/** A dirty function replicating the behaviour of {@link #getLongByTriple(long[])} but skipping the
	 * signature test. Used in the constructor. <strong>Must</strong> be kept in sync with {@link #getLongByTriple(long[])}. */ 
	private long getLongByTripleNoCheck( final long[] triple, final int[] e ) {
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( triple[ 0 ] >>> chunkShift );
		final long edgeOffsetSeed = edgeOffsetAndSeed[ chunk ];
		final long chunkOffset = vertexOffset( edgeOffsetSeed );
		HypergraphSolver.tripleToEdge( triple, edgeOffsetSeed & ~OFFSET_MASK, (int)( vertexOffset( edgeOffsetAndSeed[ chunk + 1 ] ) - chunkOffset ), e );
		return ( edgeOffsetSeed & OFFSET_MASK ) + countNonzeroPairs( chunkOffset, chunkOffset + e[ (int)( values.getLong( e[ 0 ] + chunkOffset ) + values.getLong( e[ 1 ] + chunkOffset ) + values.getLong( e[ 2 ] + chunkOffset ) ) % 3 ], array );
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
				new Switch( "utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)." ),
				new Switch( "byteArray", 'b', "byte-array", "Create a function on byte arrays (no character encoding)." ),
				new FlaggedOption( "signatureWidth", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "signature-width", "If specified, the signature width in bits." ),
				new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
				new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash function." ),
				new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY,
						"The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ), } );

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final File tempDir = jsapResult.getFile( "tempDir" );
		final boolean byteArray = jsapResult.getBoolean( "byteArray" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean utf32 = jsapResult.getBoolean( "utf32" );
		final int signatureWidth = jsapResult.getInt( "signatureWidth", 0 ); 

		if ( byteArray ) {
			if ( "-".equals( stringFile ) ) throw new IllegalArgumentException( "Cannot read from standard input when building byte-array functions" );
			if ( iso || utf32 || jsapResult.userSpecified( "encoding" ) ) throw new IllegalArgumentException( "Encoding options are not available when building byte-array functions" );
			final Collection<byte[]> collection= new FileLinesByteArrayCollection( stringFile, zipped );
			BinIO.storeObject( new MinimalPerfectHashFunction<byte[]>( collection, TransformationStrategies.rawByteArray(), signatureWidth, tempDir, null ), functionName );
		}
		else {
			final Collection<MutableString> collection;
			if ( "-".equals( stringFile ) ) {
				final ProgressLogger pl = new ProgressLogger( LOGGER );
				pl.displayLocalSpeed = true;
				pl.displayFreeMemory = true;
				pl.start( "Loading strings..." );
				collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
				pl.done();
			}
			else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
			final TransformationStrategy<CharSequence> transformationStrategy = iso 
					? TransformationStrategies.iso() 
							: utf32 
							? TransformationStrategies.utf32()
									: TransformationStrategies.utf16();

			BinIO.storeObject( new MinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy, signatureWidth, tempDir, null ), functionName );
		}
		LOGGER.info( "Saved." );
	}
}
