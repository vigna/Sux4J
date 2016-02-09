package it.unimi.dsi.sux4j.io;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2015 Sebastiano Vigna 
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

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.AbstractIntComparator;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.io.SafelyCloseable;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.GOV3Function;
import it.unimi.dsi.sux4j.mph.GOV3Function.Builder;
import it.unimi.dsi.sux4j.mph.Hashes;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.iterators.IteratorEnumeration;
import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A temporary store of hash triples virtually divided into chunks.
 * 
 * <p>A chunked hash store accumulates elements (objects of type {@code T}) 
 * by turning them into bit vectors (using a provided {@link TransformationStrategy})
 * and then hashing such vectors into a triple of longs (i.e., overall we get a hash of 192 bits). 
 * Elements can be added {@linkplain #add(Object, long) one by one}
 * or {@linkplain #addAll(Iterator, LongIterator) in batches}. 
 * Elements must be distinct, or, more precisely, they must be transformed into distinct bit vectors.
 * 
 * <p>Besides the hashes, we store some data associated with each element:
 * if {@linkplain #add(Object) no data is specified}, we store the <em>rank</em> of each element added (the first element added has rank 0,
 * the second one has rank 1, and so on), unless you specified at {@linkplain #ChunkedHashStore(TransformationStrategy, File, int, ProgressLogger) construction time}
 * a nonzero <em>hash width</em>: in that case, the value stored by {@link #add(Object)} will be given the lowest bits of the first hash of the triple
 * associated with the object (the hash width is the number of bits stored). This feature makes it possible, for example, to implement a static
 * {@linkplain Builder#dictionary(int) dictionary} using a {@link GOV3Function}.
 * 
 * <p>Once all elements have been added, they can be gathered into <em>chunks</em> whose 
 * tentative size can be set by calling {@link #log2Chunks(int)}. More precisely,
 * if the latter method is called with argument <var>k</var>, then each chunk
 * will be formed by grouping triples by the <var>k</var> most significant bits of their first hash.
 * 
 * <p>To obtain triples, one calls {@link #iterator()}, which returns chunks one at a time (in their
 * natural order); triples within each chunk are returned by increasing hash. Actually, the iterator
 * provided by a chunk returns a <em>quadruple</em> whose last element is the data associated with the element
 * that generated the triple.
 * 
 * <p>It is possible (albeit <em>very</em> unlikely) that different elements generate the same hash. This event is detected
 * during chunk iteration (not while accumulating hashes), and it will throw a {@link ChunkedHashStore.DuplicateException}.
 * At that point, the caller must handle the exception by {@linkplain #reset(long) resetting the store} ant trying again
 * from scratch. Note that after a few (say, three) exceptions you can safely assume that there are duplicate elements. If you 
 * need to force a check on the whole store you can call {@link #check()}. If all your elements come from an {@link Iterable}, 
 * {@link #checkAndRetry(Iterable, LongIterable)} will try three times to build a checked chunked hash store.
 * 
 * <p>Every {@link #reset(long)} changes the seed used by the store to generate triples. So, if this seed has to be
 * stored this must happen <em>after</em> the last call to {@link #reset(long)}. To help tracking this fact, a call to 
 * {@link #seed()} will <em>lock</em> the store; any further call to {@link #reset(long)} will throw an {@link IllegalStateException}.
 * In case the store needs to be reused, you can call {@link #clear()}, that will bring back the store to after-creation state.
 * 
 * <p>When you have finished using a chunked hash store, you should {@link #close()} it. This class implements
 * {@link SafelyCloseable}, and thus provides a safety-net finalizer.
 * 
 * <h2>Filtering</h2>
 * 
 * <p>You can at any time {@linkplain #filter(Predicate) set a predicate} that will filter the triples returned by the store. 
 * 
 * <h2>Implementation details</h2>
 * 
 * <p>Internally, a chunked hash store has a notion of disk chunk: triples are stored on disk using a fixed number of bits.
 * Once the user chooses a chunk size, the store exhibits the data on disk by grouping disk chunks or splitting them
 * in a suitable way. This process is transparent to the user.
 * 
 * <p>An instance of this class will save triples into {@link #DISK_CHUNKS} disk chunks. Triples have to
 * be loaded into memory only chunk by chunk, so to be sorted and tested for uniqueness. As long as
 * {@link #DISK_CHUNKS} is larger than eight, the store will need less than one bit per element of main
 * memory. {@link #DISK_CHUNKS} can be increased arbitrarily at compile time, but each store
 * will open {@link #DISK_CHUNKS} files at the same time. (For the same reason, it is
 * <strong>strongly</strong> suggested that you close your stores as soon as you do not need them).
 * 
 * <h2>Intended usage</h2>
 * 
 * <p>Chunked hash stores should be built by classes that need to manipulate elements in chunks of approximate given 
 * size without needing access to the elements themselves, but just to their triples, a typical
 * example being {@link GOV3Function}, which uses the triples to compute a 3-hyperedge. Once a chunked hash
 * store is built, it can be passed on to further substructures, reducing greatly the computation time (as the original
 * collection need not to be scanned again).
 * 
 * <p>To compute the chunk corresponding to given element, use
 * <pre>
 * final long[] h = new long[ 3 ];
 * Hashes.spooky4( transform.toBitVector( key ), seed, h );
 * final int chunk = chunkShift == Long.SIZE ? 0 : (int)( h[ 0 ] &gt;&gt;&gt; chunkShift );
 * </pre>
 * where <code>seed</code> is the store seed, and <code>chunkShift</code> 
 * is the return value of {@link #log2Chunks(int)} and should be stored by the caller. Note
 * that you do not need the chunked hash store to compute these data, but just its seed and the chunk shift. 
 * 
 * @author Sebastiano Vigna
 * @since 1.0.4
 */

public class ChunkedHashStore<T> implements Serializable, SafelyCloseable, Iterable<ChunkedHashStore.Chunk> {
    public static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger( ChunkedHashStore.class );
	private static final boolean DEBUG = false;

	/** Denotes that the chunked hash store contains a duplicate hash triple. */
	public static class DuplicateException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	/** The size of the output buffers. */
	public final static int OUTPUT_BUFFER_SIZE = 16 * 1024;	
	/** The logarithm of the number of physical disk chunks. */
	public final static int LOG2_DISK_CHUNKS = 8;
	/** The number of physical disk chunks. */
	public final static int DISK_CHUNKS = 1 << LOG2_DISK_CHUNKS;
	/** The shift for physical disk chunks. */
	public final static int DISK_CHUNKS_SHIFT = Long.SIZE - LOG2_DISK_CHUNKS;
	/** The number of elements ever {@linkplain #add(Object) added}. */
	protected long size;
	/** The number of elements that pass the current filter, or -1 we it must be recomputed. */
	protected long filteredSize;
	/** The seed used to generate the hash triples. */
	protected long seed;
	/** The number of triples in each disk chunk. */
	private int[] count;
	/** The number of chunks. */
	private long chunks;
	/** The files containing disk chunks. */
	private File file[];
	/** The number of disk chunks making up a chunk, or 1 if a chunk is smaller than or equal to a disk chunk. */
	private int diskChunkStep;
	/** The shift to be applied to the first hash to obtain the chunk index, set by {@link #log2Chunks(int)} (watch out: it can be {@link Long#SIZE}). */
	private int chunkShift;
	/** If true, this store has been checked for duplicates. */
	private boolean checkedForDuplicates;
	/** The transformation strategy provided at construction time. */
	private final TransformationStrategy<? super T> transform;
	/** A progress logger. */
	private final ProgressLogger pl;
	/** If nonzero, no associated data is saved in the store: {@link Chunk#data(long)} will return the first of the three hashes associated with the key, masked by this value. */
	private final long hashMask;
	/** The temporary directory for this chunked hash store, or {@code null}. */
	private final File tempDir;
	/** The data output streams for the disk chunks. */
	private DataOutputStream[] dos;
	/** The number of disk chunks divided by {@link #diskChunkStep}. */
	private int virtualDiskChunks;
	/** If not {@code null}, a filter that will be used to select triples. */
	private Predicate filter;
	/** Whether this store is locked. Any attempt to {@link #reset(long)} the store will cause an {@link IllegalStateException} if this variable is true.*/
	private boolean locked;
	/** Whether this store has already been closed. */
	private boolean closed;

	/** Creates a chunked hash store with given transformation strategy.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @throws IOException 
	 */	
	public ChunkedHashStore( final TransformationStrategy<? super T> transform ) throws IOException {
		this( transform, null, null );
	}
		
	/** Creates a chunked hash store with given transformation strategy and temporary file directory.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the current directory.
	 */	
	public ChunkedHashStore( final TransformationStrategy<? super T> transform, final File tempDir ) throws IOException {
		this( transform, tempDir, null );
	}

	/** Creates a chunked hash store with given transformation strategy.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @param pl a progress logger, or {@code null}.
	 */
	public ChunkedHashStore( final TransformationStrategy<? super T> transform, final ProgressLogger pl ) throws IOException {
		this( transform, null, pl );
	}

	/** Creates a chunked hash store with given transformation strategy and progress logger.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the current directory.
	 * @param pl a progress logger, or {@code null}.
	 */

	public ChunkedHashStore( final TransformationStrategy<? super T> transform, final File tempDir, final ProgressLogger pl ) throws IOException {
		this( transform, tempDir, 0, pl );
	}
	
	/** Creates a chunked hash store with given transformation strategy and progress logger.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the current directory.
	 * @param hashWidth if nonzero, no associated data is saved in the store: {@link Chunk#data(long)} will return this many lower bits
	 * of the first of the three hashes associated with the key. 
	 * @param pl a progress logger, or {@code null}.
	 */

	public ChunkedHashStore( final TransformationStrategy<? super T> transform, final File tempDir, final int hashWidth, final ProgressLogger pl ) throws IOException {
		this.transform = transform;
		this.pl = pl;
		this.tempDir = tempDir;
		this.hashMask = hashWidth == 0 ? 0 : -1L >>> Long.SIZE - hashWidth;
		
		file = new File[ DISK_CHUNKS ];
		dos = new DataOutputStream[ DISK_CHUNKS ];
		// Create disk chunks
		for( int i = 0; i < DISK_CHUNKS; i++ ) {
			dos[ i ] = new DataOutputStream( new FastBufferedOutputStream( new FileOutputStream( file[ i ] = File.createTempFile( ChunkedHashStore.class.getSimpleName(), String.valueOf( i ), tempDir ) ), OUTPUT_BUFFER_SIZE ) );
			file[ i ].deleteOnExit();
		}

		count = new int[ DISK_CHUNKS ];
	}

	/** Return the current seed of this chunked hash store. After calling this method, no {@link #reset(long)} will be allowed (unless the store
	 * is {@linkplain #clear() cleared}).
	 * 
	 * @return the current seed of this chunked hash store.
	 */
	
	public long seed() {
		locked = true;
		return seed;
	}

	/** Return the temporary directory of this chunked hash store, or {@code null}.
	 *
	 * @return the temporary directory of this chunked hash store, or {@code null}.
	 */
	public File tempDir() {
		return tempDir;
	}
	
	/** Return the transformation strategy provided at construction time. 
	 * @return the transformation strategy provided at construction time. */
	public TransformationStrategy<? super T> transform() {
		return transform;
	}
	
	/** Adds an element to this store, associating it with a specified value.
	 * 
	 * @param o the element to be added.
	 * @param value the associated value.
	 */
	public void add( final T o, final long value ) throws IOException {
		final long[] triple = new long[ 3 ];
		Hashes.spooky4( transform.toBitVector( o ), seed, triple );
		add( triple, value );
	}
	
	/** Adds an element to this store, associating it with its ordinal position.
	 * 
	 * @param o the element to be added.
	 */
	public void add( final T o ) throws IOException {
		add( o, filteredSize );
	}
	
	/** Adds a triple to this store.
	 * 
	 * @param triple the triple to be added.
	 * @param value the associated value.
	 */
	private void add( final long[] triple, final long value ) throws IOException {
		final int chunk = (int)( triple[ 0 ] >>> DISK_CHUNKS_SHIFT );
		count[ chunk ]++;
		checkedForDuplicates = false;
		if ( DEBUG ) System.err.println( "Adding " + Arrays.toString( triple ));
		dos[ chunk ].writeLong( triple[ 0 ] );
		dos[ chunk ].writeLong( triple[ 1 ] );
		dos[ chunk ].writeLong( triple[ 2 ] );
		if ( hashMask == 0 ) dos[ chunk ].writeLong( value );
		if ( filteredSize != -1 && ( filter == null || filter.evaluate( triple ) ) ) filteredSize++;
		size++;
	}
	
	/** Adds the elements returned by an iterator to this store, associating them with specified values.
	 * 
	 * @param elements an iterator returning elements.
	 * @param values an iterator on values parallel to {@code elements}.
	 */
	public void addAll( final Iterator<? extends T> elements, final LongIterator values ) throws IOException {
		if ( pl != null ) {
			pl.expectedUpdates = -1;
			pl.start( "Adding elements..." );
		}
		final long[] triple = new long[ 3 ];
		while( elements.hasNext() ) {
			Hashes.spooky4( transform.toBitVector( elements.next() ), seed, triple );
			add( triple, values != null ? values.nextLong() : filteredSize );
			if ( pl != null ) pl.lightUpdate();
		}
		if ( values != null && values.hasNext() ) throw new IllegalStateException( "The iterator on values contains more entries than the iterator on keys" );
		if ( pl != null ) pl.done();
	}

	/** Adds the elements returned by an iterator to this store, associating them with their ordinal position.
	 * 
	 * @param elements an iterator returning elements.
	 */
	public void addAll( final Iterator<? extends T> elements ) throws IOException {
		addAll( elements, null );
	}

	/** Returns the size of this store. Note that if you set up 
	 * a {@linkplain #filter(Predicate) filter}, the first call to
	 * this method will require a scan to the whole store. 
	 * 
	 * @return the number of (possibly filtered) triples of this store.
	 */

	public long size() throws IOException {
		if ( filter == null ) return size;
		if ( filteredSize == - 1 ) {
			long c = 0;
			final long[] triple = new long[ 3 ];
			for( int i = 0; i < DISK_CHUNKS; i++ ) {
				if ( filter == null ) c += count[ i ];
				else {
					for( DataOutputStream d: dos ) d.flush();
					final DataInputStream dis = new DataInputStream( new FastBufferedInputStream( new FileInputStream( file[ i ] ) ) );
					for( int j = 0; j < count[ i ]; j++ ) {
						triple[ 0 ] = dis.readLong();
						triple[ 1 ] = dis.readLong();
						triple[ 2 ] = dis.readLong();
						if ( hashMask == 0 ) dis.readLong();
						if ( filter.evaluate( triple ) ) c++;
					}
					dis.close();
				}
			}

			filteredSize = c;
		}
		return filteredSize;
	}
	
	/** Clears this store. After a call to this method, the store can be reused.
	 */
	
	public void clear() {
		locked = false;
		reset( 0 );
	}
	
	
	protected void finalize() throws Throwable {
		try {
			if ( ! closed ) {
				LOGGER.warn( "This " + this.getClass().getName() + " [" + toString() + "] should have been closed." );
				close();
			}
		}
		finally {
			super.finalize();
		}
	}

	/** Closes this store, disposing all associated resources.
	 * 
	 */
	
	public void close() {
		if ( ! closed ) {
			closed = true;
			for( DataOutputStream d: dos )
				try {
					d.close();
				}
			catch ( IOException e ) {
				throw new RuntimeException( e );
			}
			for( File f: file ) f.delete();
		}
	}
	
	/** Resets this store using a new seed. All accumulated data are cleared, and a new seed is reinstated.
	 * 
	 * @param seed the new seed.
	 * @throws IllegalStateException if this store was locked by a call to {@link #seed()}, and never {@linkplain #clear() cleared} thereafter.
	 */
	
	public void reset( final long seed ) {
		if ( locked ) throw new IllegalStateException();
		if ( DEBUG ) System.err.println( "RESET(" + seed + ")" );
		filteredSize = 0;
		this.seed = seed;
		checkedForDuplicates = false;
		Arrays.fill( count, 0 );
		try {
			for( DataOutputStream d: dos ) d.close();
			for( int i = 0; i < DISK_CHUNKS; i++ ) dos[ i ] = new DataOutputStream( new FastBufferedOutputStream( new FileOutputStream( file[ i ] ), OUTPUT_BUFFER_SIZE ) );
		}
		catch ( IOException e ) {
			throw new RuntimeException( e );
		}
	}

	/** Checks that this store has no duplicate triples, throwing an exception if this fails to happen.
	 * 
	 * @throws DuplicateException if this store contains duplicate triples.
	 */

	
	public void check() throws DuplicateException {
		for( ChunkedHashStore.Chunk b: this ) b.iterator();
	}
	
	/** Checks that this store has no duplicate triples, and try to rebuild if this fails to happen.
	 * 
	 * @param iterable the elements with which the store will be refilled if there are duplicate triples.
	 * @param values the values that will be associated with the elements returned by <code>iterable</code>. 
	 * @throws IllegalArgumentException if after a few trials the store still contains duplicate triples.
	 */
	public void checkAndRetry( final Iterable<? extends T> iterable, final LongIterable values ) throws IOException {
		final RandomGenerator random = new XorShift1024StarRandomGenerator();
		int duplicates = 0;

		for(;;)
			try {
				check();
				break;
			}
			catch ( DuplicateException e ) {
				if ( duplicates++ > 3 ) throw new IllegalArgumentException( "The input list contains duplicates" );
				LOGGER.warn( "Found duplicate. Recomputing triples..." );
				reset( random.nextLong() );
				addAll( iterable.iterator(), values.iterator() );
			}
			
		checkedForDuplicates = true;
	}
	
	/** Checks that this store has no duplicate triples, and try to rebuild if this fails to happen.
	 * 
	 * <p><strong>Warning</strong>: the actions are executed exactly in the specified order&mdash;<em>first</em>
	 * check, <em>then</em> retry. If you invoke this method on an empty store you'll get a checked empty store.
	 * 
	 * @param iterable the elements with which the store will be refilled if there are duplicate triples. 
	 * @throws IllegalArgumentException if after a few trials the store still contains duplicate triples.
	 */
	public void checkAndRetry( final Iterable<? extends T> iterable ) throws IOException {
		checkAndRetry( iterable, null );
	}

	/** Generate a list of signatures using the lowest bits of the first hash in this store.
	 * 
	 * <p>For this method to work, this store must contain ranks.
	 * 
	 * @param signatureWidth the width in bits of the signatures.
	 * @param pl a progress logger.
	 */
	
	public LongBigList signatures( final int signatureWidth, final ProgressLogger pl ) throws IOException { 
		final LongBigList signatures = LongArrayBitVector.getInstance().asLongBigList( signatureWidth );
		final long signatureMask = -1L >>> Long.SIZE - signatureWidth;
		signatures.size( size() );
		pl.expectedUpdates = size();
		pl.itemsName = "signatures";
		pl.start( "Signing..." );
		for ( ChunkedHashStore.Chunk chunk : this ) {
			final Iterator<long[]> chunkIterator = chunk.iterator();
			for( int i = chunk.size(); i-- != 0; ) { 
				final long[] quadruple = chunkIterator.next();
				signatures.set( quadruple[ 3 ], signatureMask & quadruple[ 0 ] );
				pl.lightUpdate();
			}
		}
		pl.done();
		return signatures;
	}
	
	/** Sets the number of chunks.
	 * 
	 * <p>Once the store is filled, you must call this method to set the number of chunks. The store will take
	 * care of merging or fragmenting disk chunks to get exactly the desired chunks.
	 * 
	 * @param log2chunks the base-2 logarithm of the number of chunks.
	 * @return the shift to be applied to the first hash of a triple to get the chunk number (see the {@linkplain ChunkedHashStore introduction}).
	 */
	
	public int log2Chunks( final int log2chunks ) {
		this.chunks = 1 << log2chunks;
		diskChunkStep = (int)Math.max( DISK_CHUNKS / chunks, 1 );
		virtualDiskChunks = DISK_CHUNKS / diskChunkStep;

		if ( DEBUG ) {
			System.err.print( "Chunk sizes: " );
			double avg = filteredSize / (double)DISK_CHUNKS;
			double var = 0;
			for( int i = 0; i < DISK_CHUNKS; i++ ) {
				System.err.print( i + ":" + count[ i ] + " " );
				var += ( count[ i ] - avg  ) * ( count[ i ] - avg );
			}
			System.err.println();
			System.err.println( "Average: " + avg );
			System.err.println( "Variance: " + var / filteredSize );

		}

		chunkShift = Long.SIZE - log2chunks;

		LOGGER.debug( "Number of chunks: " + chunks );
		LOGGER.debug( "Number of disk chunks: " + DISK_CHUNKS );
		LOGGER.debug( "Number of virtual disk chunks: " + virtualDiskChunks );

		return chunkShift;
	}

	/** A chunk returned by a {@link ChunkedHashStore}. */
	public final static class Chunk implements Iterable<long[]> {
		/** The start position of this chunk in the parallel arrays {@link #buffer0}, {@link #buffer1}, {@link #buffer2}, and {@link #data}. */
		private final int start;
		/** The final position (excluded) of this chunk in the parallel arrays {@link #buffer0}, {@link #buffer1}, {@link #buffer2}, and {@link #data}. */
		private final int end;
		private final long[] buffer0;
		private final long[] buffer1;
		private final long[] buffer2;
		private final long[] data;
		private final long hashMask;
		
		private Chunk( final long[] buffer0, final long[] buffer1, final long[] buffer2, final long[] data, final long hashMask, final int start, final int end ) {
			this.start = start;
			this.end = end;
			this.data = data;
			this.hashMask = hashMask;
			this.buffer0 = buffer0;
			this.buffer1 = buffer1;
			this.buffer2 = buffer2;
		}
		
		/** The number of triples in this chunk.
		 * 
		 * @return the number of triples in this chunk.
		 */
		public int size() {
			return end - start;
		}
		
		/** Returns the data of the <code>k</code>-th triple returned by this chunk.
		 * 
		 * <p>This method provides an alternative random access to data (w.r.t. indexing the fourth element of the
		 * quadruples returned by {@link #iterator()}). 
		 * 
		 * @param k the index (in iteration order) of a triple.
		 * @return the corresponding data.
		 */
		
		public long data( final long k ) {
			return data != null ? data[ (int)( start + k ) ] : ( buffer0[ (int)( start + k ) ] & hashMask );
		}
		
		
		/** Returns an iterator over the quadruples associated with this chunk; the returned array of longs is reused at each call.
		 * 
		 * @return an iterator over quadruples formed by a triple (indices 0, 1, 2) and the associated data (index 3).
		 */
		
		public Iterator<long[]> iterator() {
			return new AbstractObjectIterator<long[]>() {
				private int pos = start;
				private long[] quadruple = new long[ 4 ];

				public boolean hasNext() {
					return pos < end;
				}

				public long[] next() {
					if ( ! hasNext() ) throw new NoSuchElementException();
					final long[] quadruple = this.quadruple;
					quadruple[ 0 ] = buffer0[ pos ];
					quadruple[ 1 ] = buffer1[ pos ];
					quadruple[ 2 ] = buffer2[ pos ];
					quadruple[ 3 ] = data != null ? data[ pos ] : buffer0[ pos ] & hashMask;
					pos++;
					return quadruple;
				}

			};
		}
	}
	
	/** Sets a filter for this store.
	 * 
	 * @param filter a predicate that will be used to filter triples.
	 */
	public void filter( final Predicate filter ) {
		this.filter = filter;
		filteredSize = -1;
	}

	/** Returns an iterator over the chunks of this chunked hash store.
	 *
	 * @return an iterator over the chunks of this chunked hash store.
	 */
	
	public Iterator<Chunk> iterator() {
		if ( closed ) throw new IllegalStateException( "This " + getClass().getSimpleName() + " has been closed " );
		for( DataOutputStream d: dos )
			try {
				d.flush();
			}
			catch ( IOException e ) {
				throw new RuntimeException( e );
			}

		int m = 0;
		for( int i = 0; i < virtualDiskChunks; i++ ) {
			int s = 0;
			for( int j = 0; j < diskChunkStep; j++ ) s += count[ i * diskChunkStep + j ];
			if ( s > m ) m = s;
		}

		final int maxCount = m;
		
		return new AbstractObjectIterator<Chunk>() {
			private int chunk;
			private FastBufferedInputStream fbis;
			private int last;
			private int chunkSize;
			private final long[] buffer0 = new long[ maxCount ];
			private final long[] buffer1 = new long[ maxCount ];
			private final long[] buffer2 = new long[ maxCount ];
			private final long[] data = hashMask != 0 ? null : new long[ maxCount ];
			
			public boolean hasNext() {
				return chunk < chunks;
			}
			
			@SuppressWarnings("unchecked")
			public Chunk next() {
				if ( ! hasNext() ) throw new NoSuchElementException();
				final long[] buffer0 = this.buffer0;

				if ( chunk % ( chunks / virtualDiskChunks ) == 0 ) {
					final int diskChunk = (int)( chunk / ( chunks / virtualDiskChunks ) );
					final long[] buffer1 = this.buffer1, buffer2 = this.buffer2;

					chunkSize = 0;
					try {
						if ( diskChunkStep == 1 ) {
							fbis = new FastBufferedInputStream( new FileInputStream( file[ diskChunk ] ) );
							chunkSize = count[ diskChunk ];
						}
						else {
							final FileInputStream[] fis = new FileInputStream[ diskChunkStep ];
							for( int i = 0; i < fis.length; i++ ) {
								fis[ i ] = new FileInputStream( file[ diskChunk * diskChunkStep + i ] );
								chunkSize += count[ diskChunk * diskChunkStep + i ];
							}
							fbis = new FastBufferedInputStream( new SequenceInputStream( new IteratorEnumeration( Arrays.asList( fis ).iterator() ) ) );
						}
						final DataInputStream dis = new DataInputStream( fbis );

						final long triple[] = new long[ 3 ];
						int count = 0;
						for( int j = 0; j < chunkSize; j++ ) {
							triple[ 0 ] = dis.readLong();
							triple[ 1 ] = dis.readLong();
							triple[ 2 ] = dis.readLong();

							if ( DEBUG ) System.err.println( "From disk: " + Arrays.toString( triple ) );
							
							if ( filter == null || filter.evaluate( triple ) ) {
								buffer0[ count ] = triple[ 0 ]; 
								buffer1[ count ] = triple[ 1 ]; 
								buffer2[ count ] = triple[ 2 ]; 
								if ( hashMask == 0 ) data[ count ] = dis.readLong();
								count++;
							}
							else if ( hashMask == 0 ) dis.readLong(); // Discard data
						}
						
						chunkSize = count;
						dis.close();
					}
					catch ( IOException e ) {
						throw new RuntimeException( e );
					}

					it.unimi.dsi.fastutil.Arrays.quickSort( 0, chunkSize, new AbstractIntComparator() {
						private static final long serialVersionUID = 0L;

						public int compare( final int x, final int y ) {
							int t = Long.signum( buffer0[ x ] - buffer0[ y ] );
							if ( t != 0 ) return t;
							t = Long.signum( buffer1[ x ] - buffer1[ y ] );
							if ( t != 0 ) return t;
							return Long.signum( buffer2[ x ] - buffer2[ y ] );
						}
					},
					new Swapper() {
						public void swap( final int x, final int y ) {
							final long e0 = buffer0[ x ], e1 = buffer1[ x ], e2 = buffer2[ x ];
							buffer0[ x ] = buffer0[ y ];
							buffer1[ x ] = buffer1[ y ];
							buffer2[ x ] = buffer2[ y ];
							buffer0[ y ] = e0;
							buffer1[ y ] = e1;
							buffer2[ y ] = e2;
							if ( hashMask == 0 ) {
								final long v = data[ x ];
								data[ x ] = data[ y ];
								data[ y ] = v;
							}
						}
					});

					if ( DEBUG ) {
						for( int i = 0; i < chunkSize; i++ ) System.err.println( buffer0[ i ] + ", " + buffer1[ i ] + ", " + buffer2[ i ] );
					}
					
					if ( ! checkedForDuplicates && chunkSize > 1 )
						for( int i = chunkSize - 1; i-- != 0; ) if ( buffer0[ i ] == buffer0[ i + 1 ] && buffer1[ i ] == buffer1[ i + 1 ] && buffer2[ i ] == buffer2[ i + 1 ] ) throw new ChunkedHashStore.DuplicateException();
					if ( chunk == chunks - 1 ) checkedForDuplicates = true;
					last = 0;
				}

				final int start = last;
				while( last < chunkSize && ( chunkShift == Long.SIZE ? 0 : buffer0[ last ] >>> chunkShift ) == chunk ) last++;
				chunk++;

				return new Chunk( buffer0, buffer1, buffer2, data, hashMask, start, last );
			}
		};
	}
}
