package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2011 Sebastiano Vigna 
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
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongBigLists;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.io.OfflineIterable;
import it.unimi.dsi.io.OfflineIterable.OfflineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.Rank;
import it.unimi.dsi.sux4j.bits.Rank16;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.util.XorShiftStarRandom;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
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
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/** An immutable function stored using the Majewski-Wormald-Havas-Czech {@linkplain HypergraphSorter 3-hypergraph technique}.
 * 
 * <p><strong>Warning:</strong> With Sux4J 2.1, the semantics of a few constructors has changed significantly.
 * 
 * <p>Instance of this class store a function from keys to values. Keys are provided by an {@linkplain Iterable iterable object} (whose iterators
 * must return elements in a consistent order), whereas values are provided by a {@link LongIterable}. 
 * A convenient {@linkplain #MWHCFunction(Iterable, TransformationStrategy) constructor} 
 * automatically assigns to each key its rank (e.g., its position in iteration order). 
 * 
 * <P>As a commodity, this class provides a main method that reads from
 * standard input a (possibly <samp>gzip</samp>'d) sequence of newline-separated strings, and
 * writes a serialised function mapping each element of the list to its position.
 *
 * <h2>Building a function</h2>
 * 
 * <p>This class provides a great amount of flexibility when creating a new function. This is reflected in the large number of constructors,
 * which can be quite confusing. To exploit the various possibilities, you must understand some details of the construction.
 * 
 * <p>In a first phase, we build a {@link ChunkedHashStore} containing hashes of the keys and associated values. Depending
 * on the constructor chosen, the store will associate each hash either with the ordinal position of the key, or with the
 * value that must be associated with the key. It is easy to tell the difference because in constructors associating the ordinal
 * position the store appears just after the keys and the transformation strategy as, for example, in 
 * {@link #MWHCFunction(Iterable, TransformationStrategy, ChunkedHashStore, LongIterable, int)} or
 * {@link #MWHCFunction(Iterable, TransformationStrategy, ChunkedHashStore)}. If the store appears after keys and values
 * (e.g., {@link #MWHCFunction(Iterable, TransformationStrategy, LongIterable, int, ChunkedHashStore)}) the store associates
 * each key with its value instead.
 * 
 * <p>It is important to understand this difference because many constructors makes it possible to pass a chunked hash store.
 * This class will try to use the store before building a new one (possibly because of a {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException}),
 * with obvious benefits in terms of performance. If the store is not checked, and a {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException} is
 * thrown, the constructors will try to rebuild the store, but this requires, of course, that the keys, and possibly the values, are available. Constructors
 * without keys (e.g., {@link #MWHCFunction(TransformationStrategy, ChunkedHashStore, int)}) assume that the store is checked.
 * Note that it is your responsibility to pass a correct store.
 * 
 * <p>The reason for this proliferation of constructors is that they are useful in different situations. The constructors
 * that store associations with values are extremely scalable because the values must just be a {@link LongIterable} that
 * will be scanned sequentially during the store construction. On the other hand, if you have already a store that
 * associates ordinal positions, and you want to build a new function for which a {@link LongList} of values needs little space (e.g.,
 * because it is described implicitly), you can use a constructor that accepts a {@link LongList} but uses the already built store. 
 * 
 * 
 * <h2>Implementation Details</h2>
 * 
 * After generating a random 3-hypergraph, we {@linkplain HypergraphSorter sort} its 3-hyperedges
 * to that a distinguished vertex in each 3-hyperedge, the <em>hinge</em>,
 * never appeared before. We then assign to each vertex a value in such a way that for each 3-hyperedge the 
 * XOR of the three values associated to its vertices is the required value for the corresponding
 * element of the function domain (this is the standard Majewski-Wormald-Havas-Czech construction).
 * 
 * <p>Then, we measure whether it is favourable to <em>compact</em> the function, that is, to store nonzero values
 * in a separate array, using a {@linkplain Rank ranked} marker array to record the positions of nonzero values.
 * 
 * <p>A non-compacted, <var>r</var>-bit {@link MWHCFunction} on <var>n</var> elements requires {@linkplain HypergraphSorter#GAMMA &gamma;}<var>rn</var>
 * bits, whereas the compacted version takes just ({@linkplain HypergraphSorter#GAMMA &gamma;} + <var>r</var>)<var>n</var> bits (plus the bits that are necessary for the
 * {@link Rank ranking structure}; the current implementation uses {@link Rank16}). This class will transparently chose
 * the most space-efficient method.
 * 
 * @author Sebastiano Vigna
 * @since 0.2
 */

public class MWHCFunction<T> extends AbstractObject2LongFunction<T> implements Serializable, Size64 {
	private static final long serialVersionUID = 4L;
	private static final Logger LOGGER = Util.getLogger( MWHCFunction.class );
	private static final boolean ASSERTS = false;
	private static final boolean DEBUG = false;
		
	/** The logarithm of the desired chunk size. */
	public final static int LOG2_CHUNK_SIZE = 10;
	/** The shift for chunks. */
	private final int chunkShift;
	/** The number of elements. */
	protected final long n;
	/** The number of vertices of the intermediate hypergraph. */
	protected final long m;
	/** The data width. */
	protected final int width;
	/** The seed used to generate the initial hash triple. */
	protected final long globalSeed;
	/** The seed of the underlying 3-hypergraphs. */
	protected final long[] seed;
	/** The start offset of each block. */
	protected final long[] offset;
	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal hash function. */
	protected final LongBigList data;
	/** Optionally, a {@link #rank} structure built on this bit array is used to mark positions containing non-zero value; indexing in {@link #data} is
	 * made by ranking if this field is non-<code>null</code>. */
	protected final LongArrayBitVector marker;
	/** The ranking structure on {@link #marker}. */
	protected final Rank16 rank;
	/** The transformation strategy to turn objects of type <code>T</code> into bit vectors. */
	protected final TransformationStrategy<? super T> transform;

	/** Creates a new function for the given elements that will map them to their ordinal position.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param chunkedHashStore a (not necessarily checked) chunked hash store containing the elements, or <code>null</code>. 
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this( elements, transform, null, -1, null, chunkedHashStore, false );
	}

	/** Creates a new function for the given elements that will map them to their ordinal position.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param tempDir a temporary directory for the store files, or <code>null</code> for the standard temporary directory.
	 * @param chunkedHashStore a (not necessarily checked) chunked hash store containing the elements, or <code>null</code>. 
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final File tempDir, final ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this( elements, transform, null, -1, tempDir, chunkedHashStore, false );
	}

	/** Creates a new function for the given elements that will map them to their ordinal position.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) throws IOException {
		this( elements, transform, null, -1, null, null, false );
	}

	/** Creates a new function for the given elements that will map them to their ordinal position.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param tempDir a temporary directory for the store files, or <code>null</code> for the standard temporary directory.
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final File tempDir ) throws IOException {
		this( elements, transform, tempDir, null );
	}

	/** Creates a new function for the given elements and values.
	 * 
	 * <p><strong>Warning</strong>: if you were using this constructor before Sux4J 2.1, you should switch
	 * to {@link #MWHCFunction(Iterable, TransformationStrategy, ChunkedHashStore, LongIterable, int)}, as the
	 * semantics has changed (see the {@linkplain MWHCFunction class documentation}).
	 * 
	 * @param elements the elements in the domain of the function, or <code>null</code>.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @param width the bit width of the <code>values</code>, or -1 if <code>values</code> is <code>null</code>.
	 * @param chunkedHashStore a chunked hash store containing the elements associated with their value, or <code>null</code>; the store
	 * can be unchecked, but in this case <code>elements</code> and <code>transform</code> must be non-<code>null</code>. 
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final LongIterable values, final int width, final ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this( elements, transform, values, width, null, chunkedHashStore, false );
	}

	/** Creates a new function for the given elements and values.
	 * 
	 * @param elements the elements in the domain of the function, or <code>null</code>.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @param width the bit width of the <code>values</code>, or -1 if <code>values</code> is <code>null</code>.
	 * @param tempDir a temporary directory for the store files, or <code>null</code> for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the elements associated with their value, or <code>null</code>; the store
	 * can be unchecked, but in this case <code>elements</code> and <code>transform</code> must be non-<code>null</code>. 
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final LongIterable values, final int width, final File tempDir, final ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this( elements, transform, values, width, tempDir, chunkedHashStore, false );
	}

	/** Creates a new function for the given elements and values.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @param width the bit width of the <code>values</code>.
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final LongIterable values, final int width ) throws IOException {
		this( elements, transform, values, width, null, null, false );
	}

	/** Creates a new function for the given elements and values.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @param width the bit width of the <code>values</code>.
	 * @param tempDir a temporary directory for the store files, or <code>null</code> for the standard temporary directory.
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final LongIterable values, final int width, final File tempDir ) throws IOException {
		this( elements, transform, values, width, tempDir, null, false );
	}

	/** Creates a new function using a given checked chunked hash store.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @param chunkedHashStore a checked chunked hash store containing the elements associated with their value. 
	 * @param width the bit width of the values contained in <code>chunkedHashStore</code>.
	 */
	public MWHCFunction( final TransformationStrategy<? super T> transform, final ChunkedHashStore<T> chunkedHashStore, int width ) throws IOException {
		this( null, transform, null, width, null, chunkedHashStore, false );
	}


	/** Creates a new function for the given elements using a chunked hash store containing ordinal positions.
	 * 
	 * @param elements the elements in the domain of the function, or <code>null</code>.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @param width the bit width of the <code>values</code>.
	 * @param chunkedHashStore a chunked hash store containing the elements associated with their ordinal position, or <code>null</code>; the store
	 * can be unchecked, but in this case <code>elements</code> and <code>transform</code> must be non-<code>null</code>. 
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final ChunkedHashStore<T> chunkedHashStore, final LongIterable values, final int width ) throws IOException {
		this( elements, transform, values, width, null, chunkedHashStore, true );
	}

	/** Creates a new function for the given elements using a chunked hash store containing ordinal positions.
	 * 
	 * @param elements the elements in the domain of the function, or <code>null</code>.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @param width the bit width of the <code>values</code>.
	 * @param tempDir a temporary directory for the store files, or <code>null</code> for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the elements associated with their ordinal position, or <code>null</code>; the store
	 * can be unchecked, but in this case <code>elements</code> and <code>transform</code> must be non-<code>null</code>. 
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final File tempDir, final ChunkedHashStore<T> chunkedHashStore, final LongIterable values, final int width ) throws IOException {
		this( elements, transform, values, width, tempDir, chunkedHashStore, true );
	}


	
	/** Creates a new function using a given checked chunked hash store containing ordinal positions and a list of values.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @param chunkedHashStore a checked chunked hash store containing the elements associated with their ordinal position. 
	 * @param values values to be assigned to each element.
	 * @param width the bit width of the <code>values</code>.
	 */
	public MWHCFunction( final TransformationStrategy<? super T> transform, final ChunkedHashStore<T> chunkedHashStore, final LongIterable values, final int width ) throws IOException {
		this( null, transform, values, width, null, chunkedHashStore, true );
	}

	/** Creates a new function for the given elements and values.
	 * 
	 * @param elements the elements in the domain of the function, or <code>null</code>.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @param width the bit width of the <code>values</code>, or -1 if <code>values</code> is <code>null</code>.
	 * @param chunkedHashStore a chunked hash store containing the elements associated with their ordinal position, or <code>null</code>; the store
	 * can be unchecked, but in this case <code>elements</code> and <code>transform</code> must be non-<code>null</code>. 
	 * @param override if true, <code>chunkedHashStore</code> contains ordinal positions, and <code>values</code> is a {@link LongIterable} that
	 * must be accessed to retrieve the actual values. 
	 */
	protected MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final LongIterable values, final int width, final File tempDir, ChunkedHashStore<T> chunkedHashStore, boolean override ) throws IOException {
		this.transform = transform;
		
		// If we have no elements, values must be a random-access list of longs.
		final LongBigList valueList = override ? ( values instanceof LongList ? LongBigLists.asBigList( (LongList)values ) : (LongBigList)values ) : null;
		
		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayFreeMemory = true;
		final Random r = new XorShiftStarRandom();
		pl.itemsName = "keys";

		final boolean givenChunkedHashStore = chunkedHashStore != null;
		if ( ! givenChunkedHashStore ) {
			if ( elements == null ) throw new IllegalArgumentException( "If you do not provide a chunked hash store, you must provide the elements" );
			chunkedHashStore = new ChunkedHashStore<T>( transform, tempDir, pl );
			chunkedHashStore.reset( r.nextLong() );
			if ( values == null || override ) chunkedHashStore.addAll( elements.iterator() );
			else chunkedHashStore.addAll( elements.iterator(), values != null ? values.iterator() : null );
		}
		n = chunkedHashStore.size();
		defRetValue = -1; // For the very few cases in which we can decide

		if ( n == 0 ) {
			m = this.globalSeed = chunkShift = this.width = 0;
			data = null;
			marker = null;
			rank = null;
			seed = null;
			offset = null;
			return;
		}

		int log2NumChunks = Math.max( 0, Fast.mostSignificantBit( n >> LOG2_CHUNK_SIZE ) );
		chunkShift = chunkedHashStore.log2Chunks( log2NumChunks );
		final int numChunks = 1 << log2NumChunks;
		
		LOGGER.debug( "Number of chunks: " + numChunks );
		
		seed = new long[ numChunks ];
		offset = new long[ numChunks + 1 ];
		
		this.width = width == -1 ? Fast.ceilLog2( n ) : width;

		// Candidate data; might be discarded for compaction.
		final OfflineIterable<BitVector,LongArrayBitVector> offlineData = new OfflineIterable<BitVector, LongArrayBitVector>( BitVectors.OFFLINE_SERIALIZER, LongArrayBitVector.getInstance() );

		int duplicates = 0;
		
		for(;;) {
			LOGGER.debug( "Generating MWHC function with " + this.width + " output bits..." );

			long seed = 0;
			pl.expectedUpdates = numChunks;
			pl.itemsName = "chunks";
			pl.start( "Analysing chunks... " );

			try {
				int q = 0;
				final LongArrayBitVector dataBitVector = LongArrayBitVector.getInstance();
				final LongBigList data = dataBitVector.asLongBigList( this.width );
				for( ChunkedHashStore.Chunk chunk: chunkedHashStore ) {
					HypergraphSorter<BitVector> sorter = new HypergraphSorter<BitVector>( chunk.size() );
					do {
						seed = r.nextLong();
					} while ( ! sorter.generateAndSort( chunk.iterator(), seed ) );

					this.seed[ q ] = seed;
					dataBitVector.fill( false );
					data.size( sorter.numVertices );
					offset[ q + 1 ] = offset[ q ] + sorter.numVertices; 

					/* We assign values. */

					int top = chunk.size(), x, k;
					final int[] stack = sorter.stack;
					final int[] vertex1 = sorter.vertex1;
					final int[] vertex2 = sorter.vertex2;
					final int[] edge = sorter.edge;

					while( top > 0 ) {
						x = stack[ --top ];
						k = edge[ x ];
						final long s = data.getLong( vertex1[ x ] ) ^ data.getLong( vertex2[ x ] );
						final long value = override ? valueList.getLong( chunk.data( k ) ) : chunk.data( k );
						data.set( x, value ^ s );
						
						if ( ASSERTS ) assert ( value == ( data.getLong( x ) ^ data.getLong( vertex1[ x ] ) ^ data.getLong( vertex2[ x ] ) ) ) :
							"<" + x + "," + vertex1[ x ] + "," + vertex2[ x ] + ">: " + value + " != " + ( data.getLong( x ) ^ data.getLong( vertex1[ x ] ) ^ data.getLong( vertex2[ x ] ) );
					}

					q++;
					offlineData.add( dataBitVector );
					pl.update();
				}

				pl.done();
				break;
			}
			catch( ChunkedHashStore.DuplicateException e ) {
				if ( elements == null ) throw new IllegalStateException( "You provided no elements, but the chunked hash store was not checked" );
				if ( duplicates++ > 3 ) throw new IllegalArgumentException( "The input list contains duplicates" );
				LOGGER.warn( "Found duplicate. Recomputing triples..." );
				chunkedHashStore.reset( r.nextLong() );
				if ( values == null || override ) chunkedHashStore.addAll( elements.iterator() );
				else chunkedHashStore.addAll( elements.iterator(), values != null ? values.iterator() : null );
			}
		}

		if ( DEBUG ) System.out.println( "Offsets: " + Arrays.toString( offset ) );

		globalSeed = chunkedHashStore.seed();

		if ( ! givenChunkedHashStore ) chunkedHashStore.close();
		
		// Check for compaction
		long nonZero = 0;
		m = offset[ offset.length - 1 ];

		{
			final OfflineIterator<BitVector, LongArrayBitVector> iterator = offlineData.iterator();
			while( iterator.hasNext() ) {
				final LongBigList data = iterator.next().asLongBigList( this.width );
				for( long i = 0; i < data.size64(); i++ ) if ( data.getLong( i ) != 0 ) nonZero++;
			}
			iterator.close();
		}
		// We estimate size using Rank16
		if ( nonZero * this.width + m * 1.126 < m * this.width ) {
			LOGGER.info( "Compacting..." );
			marker = LongArrayBitVector.ofLength( m );
			final LongBigList newData = LongArrayBitVector.getInstance().asLongBigList( this.width );
			newData.size( nonZero );
			nonZero = 0;

			final OfflineIterator<BitVector, LongArrayBitVector> iterator = offlineData.iterator();
			long j = 0;
			while( iterator.hasNext() ) {
				final LongBigList data = iterator.next().asLongBigList( this.width );
				for( long i = 0; i < data.size64(); i++, j++ ) {
					final long value = data.getLong( i ); 
					if ( value != 0 ) {
						marker.set( j );
						newData.set( nonZero++, value );
					}
				}
			}
			iterator.close();

			rank = new Rank16( marker );

			if ( ASSERTS ) {
				final OfflineIterator<BitVector, LongArrayBitVector> iterator2 = offlineData.iterator();
				long k = 0;
				while( iterator2.hasNext() ) {
					final LongBigList data = iterator2.next().asLongBigList( this.width );
					for( long i = 0; i < data.size64(); i++, k++ ) {
						final long value = data.getLong( i ); 
						assert ( value != 0 ) == marker.getBoolean( k );
						if ( value != 0 ) assert value == newData.getLong( rank.rank( k ) ) : value + " != " + newData.getLong( rank.rank( k ) );
					}
				}
				iterator2.close();
			}
			this.data = newData;
		}
		else {
			final LongArrayBitVector dataBitVector = LongArrayBitVector.getInstance( m * this.width );
			this.data = dataBitVector.asLongBigList( this.width );
			
			OfflineIterator<BitVector, LongArrayBitVector> iterator = offlineData.iterator();
			while( iterator.hasNext() ) dataBitVector.append( iterator.next() );
			iterator.close();

			marker = null;
			rank = null;
		}
		
		offlineData.close();

		LOGGER.info( "Completed." );
		LOGGER.debug( "Forecast bit cost per element: " + ( marker == null ?
				HypergraphSorter.GAMMA * this.width :
					HypergraphSorter.GAMMA + this.width + 0.126 ) );
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / n );
	}

	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		if ( n == 0 ) return defRetValue;
		final int[] e = new int[ 3 ];
		final long[] h = new long[ 3 ];
		Hashes.jenkins( transform.toBitVector( (T)o ), globalSeed, h );
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( h[ 0 ] >>> chunkShift );
		final long chunkOffset = offset[ chunk ];
		HypergraphSorter.tripleToEdge( h, seed[ chunk ], (int)( offset[ chunk + 1 ] - chunkOffset ), e );
		if ( e[ 0 ] == -1 ) return defRetValue;
		final long e0 = e[ 0 ] + chunkOffset, e1 = e[ 1 ] + chunkOffset, e2 = e[ 2 ] + chunkOffset;
		return rank == null ?
				data.getLong( e0 ) ^ data.getLong( e1 ) ^ data.getLong( e2 ) :
				( marker.getBoolean( e0 ) ? data.getLong( rank.rank( e0 ) ) : 0 ) ^
				( marker.getBoolean( e1 ) ? data.getLong( rank.rank( e1 ) ) : 0 ) ^
				( marker.getBoolean( e2 ) ? data.getLong( rank.rank( e2 ) ) : 0 );
	}
	
	public long getLongByTriple( final long[] triple ) {
		if ( n == 0 ) return defRetValue;
		final int[] e = new int[ 3 ];
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( triple[ 0 ] >>> chunkShift );
		final long chunkOffset = offset[ chunk ];
		HypergraphSorter.tripleToEdge( triple, seed[ chunk ], (int)( offset[ chunk + 1 ] - chunkOffset ), e );
		final long e0 = e[ 0 ] + chunkOffset, e1 = e[ 1 ] + chunkOffset, e2 = e[ 2 ] + chunkOffset;
		if ( e0 == -1 ) return defRetValue;
		return rank == null ?
				data.getLong( e0 ) ^ data.getLong( e1 ) ^ data.getLong( e2 ) :
				( marker.getBoolean( e0 ) ? data.getLong( rank.rank( e0 ) ) : 0 ) ^
				( marker.getBoolean( e1 ) ? data.getLong( rank.rank( e1 ) ) : 0 ) ^
				( marker.getBoolean( e2 ) ? data.getLong( rank.rank( e2 ) ) : 0 );
	}
	

	
	/** Returns the number of elements in the function domain.
	 *
	 * @return the number of the elements in the function domain.
	 */
	public long size64() {
		return n;
	}

	@Deprecated
	public int size() {
		return n > Integer.MAX_VALUE ? -1 : (int)n;
	}

	/** Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		if ( n == 0 ) return 0;
		return ( marker != null ? rank.numBits() + marker.length() : 0 ) + ( data != null ? data.size64() : 0 ) * width + seed.length * (long)Long.SIZE + offset.length * (long)Integer.SIZE;
	}

	/** Creates a new function by copying a given one; non-transient fields are (shallow) copied.
	 * 
	 * @param function the function to be copied.
	 */
	protected MWHCFunction( final MWHCFunction<T> function ) {
		this.n = function.n;
		this.m = function.m;
		this.chunkShift = function.chunkShift;
		this.globalSeed = function.globalSeed;
		this.offset = function.offset;
		this.width = function.width;
		this.seed = function.seed;
		this.data = function.data;
		this.rank = function.rank;
		this.marker = function.marker;
		this.transform = function.transform.copy();
	}
	
	public boolean containsKey( final Object o ) {
		return true;
	}

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MWHCFunction.class.getName(), "Builds an MWHC function mapping a newline-separated list of strings to their ordinal position.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new FlaggedOption( "tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised MWHC function." ),
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

		BinIO.storeObject( new MWHCFunction<CharSequence>( collection, transformationStrategy, jsapResult.getFile( "tempDir" ) ), functionName );
		LOGGER.info( "Completed." );
	}
}
