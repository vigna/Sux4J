package it.unimi.dsi.sux4j.scratch;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2014 Sebastiano Vigna 
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
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.AbstractIntComparator;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongIterator;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.sux4j.mph.AbstractHashFunction;
import it.unimi.dsi.sux4j.mph.Hashes;
import it.unimi.dsi.sux4j.mph.HypergraphSorter;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.primes.Primes;
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
 * return a distinct number for each keys in the list. For keys out of the list, the
 * resulting number is not specified. In some (rare) cases it might be possible to establish that a
 * key was not in the original list, and in that case -1 will be returned; this behaviour
 * can be made standard by <em>signing</em> the function (see below). The class can then be
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
 * occupancy by 0.625%, bringing the actual occupied space to around 2.68 bits per key.
 * 
 * <P>As a commodity, this class provides a main method that reads from standard input a (possibly
 * <samp>gzip</samp>'d) sequence of newline-separated strings, and writes a serialised minimal
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
	private static final Logger LOGGER = LoggerFactory.getLogger( MinimalPerfectHashFunction.class );
	private static final boolean ASSERTS = true;

	public static final long serialVersionUID = 4L;

	/** A builder class for {@link MinimalPerfectHashFunction}. */
	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected int signatureWidth;
		protected File tempDir;
		protected int lambda = 4;
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
		
		/** Specifies the average size of a bucket.
		 * 
		 * @param lambda the average size of a bucket.
		 * @return this builder.
		 */
		public Builder<T> lambda( final int lambda ) {
			this.lambda = lambda;
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
			return new MinimalPerfectHashFunction<T>( keys, transform, lambda, signatureWidth, tempDir, chunkedHashStore );
		}
	}
	
	/** The number of bits per block in the rank structure. */
	public static final int BITS_PER_BLOCK = 1024;

	/** The logarithm of the desired chunk size. */
	public final static int LOG2_CHUNK_SIZE = 10;

	/** The number of keys. */
	protected final long n;

	/** The shift for chunks. */
	private final int chunkShift;

	/** The seed used to generate the initial hash triple. */
	protected final long globalSeed;

	/** The seed of the underlying 3-hypergraphs. */
	protected final long[] seed;

	/** The start offset of each chunk. */
	protected final long[] offset;

	/** The number of buckets of each chunk, stored cumulatively. */
	protected final long[] numBuckets;

	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal perfect hash function. */
	protected final int[][] c0, c1;

	/** The bit vector underlying {@link #values}. */
	protected final LongArrayBitVector bitVector;

	/** The bit array supporting {@link #values}. */
	protected transient long[] array;

	/** The number of nonzero bit pairs up to a given block of {@link #BITS_PER_BLOCK} bits. */
	protected final long count[];

	/** The transformation strategy. */
	protected final TransformationStrategy<? super T> transform;
	
	/** The mask to compare signatures, or zero for no signatures. */
	protected final long signatureMask;
	
	/** The signatures. */
	protected final LongBigList signatures;
	protected final EliasFanoLongBigList coefficients; 

	/**
	 * Creates a new minimal perfect hash function for the given keys.
	 * 
	 * @param keys the keys to hash, or {@code null}.
	 * @param transform a transformation strategy for the keys.
	 * @param lambda the average bucket size
	 * @param signatureWidth a signature width, or 0 for no signature.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
	 * can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be non-{@code null}. 
	 */
	protected MinimalPerfectHashFunction( final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final int lambda, final int signatureWidth, final File tempDir, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
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

		c0 = new int[ numChunks ][];
		c1 = new int[ numChunks ][];
		
		LOGGER.info( "Number of chunks: " + numChunks );
		LOGGER.info( "Average chunk size: " + (double)n / numChunks );

		seed = new long[ numChunks ];
		offset = new long[ numChunks + 1 ];
		numBuckets = new long[ numChunks + 1 ];
		
		bitVector = LongArrayBitVector.getInstance();
		//( values = bitVector.asLongBigList( 2 ) ).size( ( (long)Math.ceil( n * HypergraphSorter.GAMMA ) + 4 * numChunks ) );
		array = bitVector.bits();

		int duplicates = 0;

		for ( ;; ) {
			LOGGER.debug( "Generating minimal perfect hash function..." );

			pl.expectedUpdates = numChunks;
			pl.itemsName = "chunks";
			pl.start( "Analysing chunks... " );

			try {
				int chunkNumber = 0;
				
				for ( ChunkedHashStore.Chunk chunk : chunkedHashStore ) {
					final int p = Primes.nextPrime( (int)( chunk.size() + 1 ) );
	
					final int numBuckets = ( chunk.size() + lambda - 1 ) / lambda;
					this.numBuckets[ chunkNumber + 1 ] = this.numBuckets[ chunkNumber ] + numBuckets;
					c0[ chunkNumber ] = new int[ numBuckets ];
					c1[ chunkNumber ] = new int[ numBuckets ];
					@SuppressWarnings("unchecked")
					final ArrayList<long[]>[] bucket = new ArrayList[ numBuckets ];
					for( int i = bucket.length; i-- != 0; ) bucket[ i ] = new ArrayList<long[]>();
					
					tryChunk: for(;;) {
						for( ArrayList<long[]> b : bucket ) b.clear();

						/* We treat a chunk as a single hash function. The number of bins is thus
						 * the first prime larger than the chunk size divided by the load factor. */
						final boolean used[] = new boolean[ p ];
						/* At each try, the allocation to keys to bucket is randomized differently. */
						final long seed = r.nextLong();
						// System.err.println( "Number of keys: " + chunk.size()  + " Number of bins: " + p + " seed: " + seed );
						/* We distribute the keys in this chunks in the buckets. */
						for( Iterator<long[]> iterator = chunk.iterator(); iterator.hasNext(); ) {
							final long[] triple = iterator.next();
							final long[] h = new long[ 3 ];
							Hashes.jenkins( triple, seed, h );
							h[ 0 ] = ( h[ 0 ] >>> 1 ) % numBuckets;
							h[ 1 ] = (int)( ( h[ 1 ] >>> 1 ) % p ); 
							h[ 2 ] = (int)( ( h[ 2 ] >>> 1 ) % ( p - 1 ) ) + 1; 

							// All elements in a bucket must have either different h[ 1 ] or different h[ 2 ]
							for( long[] t: bucket[ (int)h[ 0 ] ] ) if ( t[ 1 ] == h[ 1 ] && t[ 2 ] == h[ 2 ] ) {
								LOGGER.info( "Duplicate index" );
								continue tryChunk;
							}
							bucket[ (int)h[ 0 ] ].add( h );
						}

						final int[] perm = Util.identity( bucket.length );
						IntArrays.quickSort( perm, new AbstractIntComparator() {
							@Override
							public int compare( int a0, int a1 ) {
								return Integer.compare( bucket[ a1 ].size(), bucket[ a0 ].size() );
							}
						} );

						int c = -1;
						for( int k: perm ) {
							c++;
							final ArrayList<long[]> b = bucket[ k ];
							final ArrayList<long[]> done = new ArrayList<long[]>();
							
							//System.err.println( "Bucket size: " + b.size() );
							int c0 = 0, c1 = 0;
							main: for( c0 = 0; c0 < p; c0++ )
								for( c1 = 0; c1 < p; c1++ ) {
									//System.err.println( "Testing " + c0 + ", " + c1 );

									boolean completed = true;
									done.clear();
									for( long[] h: b ) {
										assert k == h[ 0 ];
										
										int pos = (int)( ( h[ 1 ] + c0 * h[ 2 ] + c1 ) % p );
										//System.err.println( "Testing pos " + pos + " for " + Arrays.toString( e  ));
										if ( used[ pos ] ) {
											completed = false;
											break;
										}
										else {
											used[ pos ] = true;
											done.add( h );
										}
									}

									if ( completed ) break main;

									for( long[] h: done ) used[ (int)( ( h[ 1 ] + c0 * h[ 2 ] + c1 ) % p ) ] = false;
								}

							if ( c0 != p ) {
								this.c0[ chunkNumber ][ k ] = c0;
								this.c1[ chunkNumber ][ k ] = c1;
								this.seed[ chunkNumber ] = seed;
							} 
							else {
								// System.err.println( "Failed fixing bucket " + c + " (size " + b.size() + ") of " + perm.length + " in chunk " + chunkNumber );
								continue tryChunk;
							}
						}
						
						break;
					}

					// System.err.println("DONE!");
					
					if ( ASSERTS ) {
						final IntOpenHashSet pos = new IntOpenHashSet();
						final long h[] = new long[ 3 ];
						for( Iterator<long[]> iterator = chunk.iterator(); iterator.hasNext(); ) {
							final long[] triple = iterator.next();
							Hashes.jenkins( triple, seed[ chunkNumber ], h );
							h[ 0 ] = ( h[ 0 ] >>> 1 ) % numBuckets;
							h[ 1 ] = (int)( ( h[ 1 ] >>> 1 ) % p ); 
							h[ 2 ] = (int)( ( h[ 2 ] >>> 1 ) % ( p - 1 ) ) + 1; 
							//System.err.println( Arrays.toString(  e  ) );
							assert pos.add( (int)( ( h[ 1 ] + c0[ chunkNumber ][ (int)( h[ 0 ] ) ] * h[ 2 ] + c1[ chunkNumber ][ (int)( h[ 0 ] ) ] ) % p ) );
						}
					}

					offset[ chunkNumber + 1 ] = offset[ chunkNumber ] + p;
					chunkNumber++;
					pl.update();
				}

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

		coefficients = new EliasFanoLongBigList( new AbstractLongIterator() {
			int chunkIndex, arrayIndex;
			@Override
			public boolean hasNext() {
				if ( arrayIndex == 0 ) while( chunkIndex < numChunks && c0[ chunkIndex ].length == 0 ) chunkIndex++;
				return chunkIndex < numChunks; 
			}

			public long nextLong() {
				if ( ! hasNext() ) throw new NoSuchElementException();
				final long result = c0[ chunkIndex ][ arrayIndex ] + c1[ chunkIndex ][ arrayIndex ] * ( offset[ chunkIndex + 1 ] - offset[ chunkIndex ] );
				if ( ++arrayIndex == c0[ chunkIndex ].length ) {
					chunkIndex++;
					arrayIndex = 0;
				}

				return result;
			}
		}, 0 );
	
		if ( ASSERTS ) {
			long p = 0;
			for( int i = 0; i < c0.length; i++ )
				for( int j = 0; j < c0[ i ].length; j++ ) {
					assert c0[ i ][ j ] == coefficients.getLong( p ) % ( offset[ i + 1 ] - offset[ i ] );
					assert c1[ i ][ j ] == coefficients.getLong( p ) / ( offset[ i + 1 ] - offset[ i ] );
					p++;
				}
		}

		LOGGER.info( "Completed." );
//		LOGGER.debug( "Forecast bit cost per key: " + ( 2 * HypergraphSorter.GAMMA + 2. * Long.SIZE / BITS_PER_BLOCK ) );
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
					signatures.set( getLongByTripleNoCheck( triple ), signatureMask & triple[ 0 ] );
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
		
		count = null; // ALERT temp
	}

	/**
	 * Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return seed.length * Long.SIZE + offset.length * Long.SIZE + coefficients.numBits() + numBuckets.length * Long.SIZE;
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
		this.seed = mph.seed;
		this.offset = mph.offset;
		this.bitVector = mph.bitVector;
		this.globalSeed = mph.globalSeed;
		this.coefficients = mph.coefficients;
		this.chunkShift = mph.chunkShift;
		this.c0 = mph.c0;
		this.c1 = mph.c1;
		//this.values = mph.values;
		this.numBuckets = mph.numBuckets;
		this.array = mph.array;
		this.count = mph.count;
		this.transform = mph.transform.copy();
		this.signatureMask = mph.signatureMask;
		this.signatures = mph.signatures;
	}

	@SuppressWarnings("unchecked")
	public long getLong( final Object key ) {
		if ( n == 0 ) return defRetValue;
		final long[] triple = new long[ 3 ];
		Hashes.jenkins( transform.toBitVector( (T)key ), globalSeed, triple );
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( triple[ 0 ] >>> chunkShift );
		final long[] h = new long[ 3 ];
		final long chunkOffset = offset[ chunk ];
		final int p = (int)( offset[ chunk + 1 ] - chunkOffset );
		Hashes.jenkins( triple, seed[ chunk ], h );
		h[ 0 ] = ( h[ 0 ] >>> 1 ) % ( numBuckets[ chunk + 1 ] - numBuckets[ chunk ] );
		h[ 1 ] = (int)( ( h[ 1 ] >>> 1 ) % p ); 
		h[ 2 ] = (int)( ( h[ 2 ] >>> 1 ) % ( p - 1 ) ) + 1; 
		
		final long c = coefficients.getLong( numBuckets[ chunk ] + h[ 0 ] );
		
		assert c0[ chunk ][ (int)h[ 0 ] ] == c % p : c0[ chunk ][ (int)h[ 0 ] ] + " != " + c / p; 
		assert c1[ chunk ][ (int)h[ 0 ] ] == c / p : c1[ chunk ][ (int)h[ 0 ] ] + " != " + c % p; 

		//int pos = (int)( ( h[ 1 ] + c0[ chunk ][ (int)h[ 0 ] ] * h[ 2 ] + c1[ chunk ][ (int)h[ 0 ] ] % p ) % p );

		int pos = (int)( ( h[ 1 ] + ( c % p ) * h[ 2 ] + c / p ) % p );
		
		final long result = chunkOffset + pos;
		return result;
	/*
		if ( signatureMask != 0 ) return result >= n || ( ( signatures.getLong( result ) ^ h[ 0 ] ) & signatureMask ) != 0 ? defRetValue : result;
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < n ? result : defRetValue;*/
	}

	/** A dirty function replicating the behaviour of {@link #getLongByTriple(long[])} but skipping the
	 * signature test. Used in the constructor. <strong>Must</strong> be kept in sync with {@link #getLongByTriple(long[])}. */ 
	private long getLongByTripleNoCheck( final long[] triple ) {
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( triple[ 0 ] >>> chunkShift );
		final long[] h = new long[ 3 ];
		Hashes.jenkins( triple, seed[ chunk ], h );
		final long chunkOffset = offset[ chunk ];
		final int p = (int)( offset[ chunk + 1 ] - chunkOffset );
		
		h[ 0 ] = ( h[ 0 ] >>> 1 ) % ( numBuckets[ chunk + 1 ] - numBuckets[ chunk ] );
		h[ 1 ] = (int)( ( h[ 1 ] >>> 1 ) % p ); 
		h[ 2 ] = (int)( ( h[ 2 ] >>> 1 ) % ( p - 1 ) ) + 1; 
		
		final long c = coefficients.getLong( numBuckets[ chunk ] + h[ 0 ] );

		int pos = (int)( ( h[ 1 ] + ( c / p ) * h[ 2 ] + c % p ) % p );
		
		return chunkOffset + pos;
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
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean utf32 = jsapResult.getBoolean( "utf32" );
		final int signatureWidth = jsapResult.getInt( "signatureWidth", 0 ); 

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

		BinIO.storeObject( new MinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy, 4, signatureWidth, jsapResult.getFile( "tempDir"), null ), functionName );
		LOGGER.info( "Saved." );
	}
}
