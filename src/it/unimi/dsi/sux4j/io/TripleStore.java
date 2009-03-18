package it.unimi.dsi.sux4j.io;

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

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.io.SafelyCloseable;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.Hashes;
import it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction;

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
import java.util.Random;

import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.iterators.IteratorEnumeration;
import org.apache.log4j.Logger;

import cern.colt.GenericSorting;
import cern.colt.Swapper;
import cern.colt.function.IntComparator;

/** A temporary store of hash triples.
 * 
 * <p>A triple store accumulates objects of type <code>T</code> by turning them into bit vectors (using a provided {@link TransformationStrategy})
 * and then hashing such vectors into a triple of longs (i.e., overall we get a hash of 192 bits). 
 * Elements can be added {@linkplain #add(Object) one by one}
 * or {@linkplain #addAll(Iterator) in batches}. Besides the hashes, we store the <em>offset</em> of each element added (the first element added has offset 0,
 * the second one has offset 1, and so on). Elements must be distinct, or more precisely, they must be transformed into distinct bit vectors.
 * 
 * <p>Once all elements have been added, they can be gathered into <em>buckets</em> whose 
 * tentative size can be set by calling {@link #log2Buckets(int)}. More precisely,
 * if the latter method is called with argument <var>k</var>, then each bucket
 * will be formed by grouping triples by the <var>k</var> most significant bits of their first hash.
 * 
 * <p>To obtain triples, one calls {@link #iterator()}, which returns buckets one at a time (in their
 * natural order); triples within each bucket are returned by increasing hash. Actually, the iterator
 * provided by a bucket returns a <em>quadruple</em> whose last element is the offset of the element
 * that generated the triple.
 * 
 * <p>It is possible (albeit unlikely) that different elements generate the same triple. This event is detected
 * during bucket iteration (not while accumulating triples), and it will throw a {@link TripleStore.DuplicateException}.
 * At that point, the caller must handle the exception by {@linkplain #reset(long) resetting the store} ant trying again
 * from scratch. Note that after a few (say, three) exceptions you can assume that there are duplicate elements. If you 
 * need to force a check on the whole store you can call {@link #check()}. If all your elements come from an {@link Iterable}, 
 * {@link #checkAndRetry(Iterable)} will try three times to build a checked triple store.
 * 
 * <p>Note that every {@link #reset(long)} changes the seed used by the store to generate triples. So, if this seed has to be
 * stored this must happen <em>after</em> the last call to {@link #reset(long)}. To help tracking this fact, a call to 
 * {@link #seed()} will <em>lock</em> the store; any further call to {@link #reset(long)} will throw an {@link IllegalStateException}.
 * In case the store needs to be reused, you can call {@link #clear()}, that will bring back the store to after-creation state.
 * 
 * <p>When you have finished using a triple store, you should {@link #close()} it. This class implements
 * {@link SafelyCloseable}, and thus provides a safety-net finalizer.
 * 
 * <h2>Filtering</h2>
 * 
 * <p>You can at any time {@linkplain #filter(Predicate) set a predicate} that will filter the triples returned by the store. 
 * 
 * <h2>Implementation details</h2>
 * 
 * <p>Internally, a triple store has a notion of disk bucket: triples are stored on disk using a fixed number of bits.
 * Once the user chooses a bucket size, the store exhibits the data on disk by grouping disk buckets or splitting them
 * in a suitable way. This process is transparent to the user.
 * 
 * <h2>Intended usage</h2>
 * 
 * <p>Triple stores should be built by classes that need to manipulate elements in buckets of approximate given 
 * size without needing access to the elements themselves, but just to their triples, a typical
 * example being {@link MinimalPerfectHashFunction}, which uses the triples to compute a 3-hyperedge. Once a triple
 * store is built, it can be passed on to further substructures, reducing greatly the computation time (as the original
 * collection need not to be scanned again).
 * 
 * <p>To compute the bucket corresponding to given element, compute
 * <pre>
 * final long[] h = new long[ 3 ];
 * Hashes.jenkins( transform.toBitVector( (T)key ), seed, h );
 * final int bucket = bucketShift == Long.SIZE ? 0 : (int)( h[ 0 ] >>> bucketShift );
 * </pre>
 * where <code>seed</code> is the store seed, and <code>bucketShift</code> 
 * is the return value of {@link #log2Buckets(int)} and should be stored by the caller. Note
 * that you do not need the triple store to compute these data, but just its seed and the bucket shift. 
 * 
 * @author Sebastiano Vigna
 * @since 1.0.4
 */

public class TripleStore<T> implements Serializable, SafelyCloseable, Iterable<TripleStore.Bucket> {
    public static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Util.getLogger( TripleStore.class );
	private static final boolean DEBUG = false;

	/** Denotes that the triple store contains a duplicate hash triple. */
	public static class DuplicateException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	/** The logarithm of the number of physical disk buckets. */
	public final static int LOG2_DISK_BUCKETS = 8;
	/** The number of physical disk buckets. */
	public final static int DISK_BUCKETS = 1 << LOG2_DISK_BUCKETS;
	/** The shift for physical disk buckets. */
	public final static int DISK_BUCKETS_SHIFT = Long.SIZE - LOG2_DISK_BUCKETS;
	/** The logarithm of the desired bucket size. */
	public final static int LOG2_BUCKET_SIZE = 9;
	/** The number of elements that pass the current filter. */
	protected int n;
	/** The seed used to generate the hash triples. */
	protected long seed;
	/** The number of triples in each disk bucket. */
	private int[] count;
	/** The number of buckets. */
	private int buckets;
	/** The files containing disk buckets. */
	private File file[];
	/** The number of disk buckets making up a bucket, or 1 if a bucket is smaller than or equal to a disk bucket. */
	private int diskBucketStep;
	/** The shift to be applied to the first hash to obtain the bucket index, set by {@link #log2Buckets(int)} (watch out: it can be {@link Long#SIZE}). */
	private int bucketShift;
	/** If true, this store has been checked for duplicates. */
	private boolean checkedForDuplicates;
	/** The transformation strategy provided at construction time. */
	private final TransformationStrategy<? super T> transform;
	/** A progress logger. */
	private final ProgressLogger pl;
	/** The data output streams for the disk buckets. */
	private DataOutputStream[] dos;
	/** The number of disk buckets divided by {@link #diskBucketStep}. */
	private int virtualDiskBuckets;
	/** If not <code>null</code>, a filter that will be used to select triples. */
	private Predicate filter;
	/** The maximum number of triples in a disk bucket. */
	private int maxCount;
	/** Whether this store is locked. Any attempt to {@link #reset(long)} the store will cause an {@link IllegalStateException} if this variable is true.*/
	private boolean locked;
	/** Whether this store has already been closed. */
	private boolean closed;

	/** Creates a triple store with given transformation strategy.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @throws IOException 
	 */	
	public TripleStore( final TransformationStrategy<? super T> transform ) throws IOException {
		this( transform, null );
	}
		
	/** Creates a triple store with given transformation strategy and progress logger.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @param pl a progress logger, or <code>null</code>.
	 */

	public TripleStore( final TransformationStrategy<? super T> transform, final ProgressLogger pl ) throws IOException {
		this.transform = transform;
		this.pl = pl;
		
		file = new File[ DISK_BUCKETS ];
		dos = new DataOutputStream[ DISK_BUCKETS ];
		// Create disk buckets
		for( int i = 0; i < DISK_BUCKETS; i++ ) {
			dos[ i ] = new DataOutputStream( new FastBufferedOutputStream( new FileOutputStream( file[ i ] = File.createTempFile( TripleStore.class.getSimpleName(), String.valueOf( i ) ) ) ) );
			file[ i ].deleteOnExit();
		}

		count = new int[ DISK_BUCKETS ];
	}

	/** Return the current seed of this triple store. After calling this method, no {@link #reset(long)} will be allowed (unless the store
	 * is {@linkplain #clear() cleared}).
	 * 
	 * @return the current seed of this triple store.
	 */
	
	public long seed() {
		locked = true;
		return seed;
	}
	
	
	/** Adds an element to this store.
	 * 
	 * @param o the element to be added.
	 */
	public void add( final T o ) throws IOException {
		final long[] triple = new long[ 3 ];
		Hashes.jenkins( transform.toBitVector( o ), seed, triple );
		add( triple );
	}
	
	private void add( final long[] triple ) throws IOException {
		final int bucket = (int)( triple[ 0 ] >>> DISK_BUCKETS_SHIFT );
		count[ bucket ]++;
		checkedForDuplicates = false;
		if ( DEBUG ) System.err.println( "Adding " + Arrays.toString( triple ));
		dos[ bucket ].writeLong( triple[ 0 ] );
		dos[ bucket ].writeLong( triple[ 1 ] );
		dos[ bucket ].writeLong( triple[ 2 ] );
		dos[ bucket ].writeInt( n );
		if ( n != -1 && ( filter == null || filter.evaluate( triple ) ) ) n++;
	}
	
	/** Adds the elements returned by an iterator to this store.
	 * 
	 * @param iterator an iterator returning elements.
	 */
	public void addAll( final Iterator<? extends T> iterator ) throws IOException {
		if ( pl != null ) pl.start( "Adding elements..." );
		final long[] triple = new long[ 3 ];
		while( iterator.hasNext() ) {
			Hashes.jenkins( transform.toBitVector( iterator.next() ), seed, triple );
			add( triple );
			if ( pl != null ) pl.lightUpdate();
		}
		if ( pl != null ) pl.done();
	}

	/** Returns the size of this store. Note that if you set up 
	 * a {@linkplain #filter(Predicate) filter}, the first call to
	 * this method will require a scan to the whole store. 
	 * 
	 * @return the number of (possibly filtered) triples of this store.
	 */

	public int size() throws IOException {
		if ( n == - 1 ) {
			int c = 0;
			final long[] triple = new long[ 3 ];
			for( int i = 0; i < DISK_BUCKETS; i++ ) {
				if ( filter == null ) c += count[ i ];
				else {
					for( DataOutputStream d: dos ) d.flush();
					final DataInputStream dis = new DataInputStream( new FastBufferedInputStream( new FileInputStream( file[ i ] ) ) );
					for( int j = 0; j < count[ i ]; j++ ) {
						triple[ 0 ] = dis.readLong();
						triple[ 1 ] = dis.readLong();
						triple[ 2 ] = dis.readLong();
						dis.readInt();
						if ( filter.evaluate( triple ) ) c++;
					}
					dis.close();
				}
			}

			n = c;
		}
		return n;
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
		n = 0;
		this.seed = seed;
		checkedForDuplicates = false;
		Arrays.fill( count, 0 );
		try {
			for( DataOutputStream d: dos ) d.close();
			for( int i = 0; i < DISK_BUCKETS; i++ ) dos[ i ] = new DataOutputStream( new FastBufferedOutputStream( new FileOutputStream( file[ i ] ) ) );
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
		for( TripleStore.Bucket b: this ) b.iterator();
	}
	
	/** Checks that this store has no duplicate triples, and try to rebuild if this fails to happen.
	 * 
	 * @param iterable the elements with which the store will be refilled if there are duplicate triples. 
	 * @throws IllegalArgumentException if after a few trials the store still contains duplicate triples.
	 */
	public void checkAndRetry( final Iterable<? extends T> iterable ) throws IOException {
		final Random random = new Random();
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
				addAll( iterable.iterator() );
			}
			
		checkedForDuplicates = true;
	}
	
	/** Sets the number of buckets.
	 * 
	 * <p>Once the store is filled, you must call this method to set the number of buckets. The store will take
	 * care of merging or fragmenting disk buckets to get exactly the desired buckets.
	 * 
	 * @param log2Buckets the base-2 logarithm of the number of buckets.
	 * @return the shift to be applied to the first hash of a triple to get the bucket number (see the {@linkplain TripleStore introduction}).
	 */
	
	public int log2Buckets( final int log2Buckets ) {
		this.buckets = 1 << log2Buckets;
		diskBucketStep = Math.max( DISK_BUCKETS / buckets, 1 );
		virtualDiskBuckets = DISK_BUCKETS / diskBucketStep;
		maxCount = 0;
		for( int i = 0; i < virtualDiskBuckets; i++ ) {
			int s = 0;
			for( int j = 0; j < diskBucketStep; j++ ) s += count[ i * diskBucketStep + j ];
			if ( s > maxCount ) maxCount = s;
		}

		if ( DEBUG ) {
			System.err.print( "Bucket sizes: " );
			double avg = n / (double)DISK_BUCKETS;
			double var = 0;
			for( int i = 0; i < DISK_BUCKETS; i++ ) {
				System.err.print( i + ":" + count[ i ] + " " );
				var += ( count[ i ] - avg  ) * ( count[ i ] - avg );
			}
			System.err.println();
			System.err.println( "Average: " + avg );
			System.err.println( "Variance: " + var / n );

		}

		bucketShift = Long.SIZE - log2Buckets;

		LOGGER.debug( "Number of buckets: " + buckets );
		LOGGER.debug( "Number of disk buckets: " + DISK_BUCKETS );
		LOGGER.debug( "Number of virtual disk buckets: " + virtualDiskBuckets );

		return bucketShift;
	}

	/** A bucket returned by a {@link TripleStore}. */
	public final static class Bucket implements Iterable<long[]> {
		/** The start position of this bucket in the parallel arrays {@link #buffer0}, {@link #buffer1}, {@link #buffer2}, and {@link #offset}. */
		private final int start;
		/** The final position (excluded) of this bucket in the parallel arrays {@link #buffer0}, {@link #buffer1}, {@link #buffer2}, and {@link #offset}. */
		private final int end;
		private final long[] buffer0;
		private final long[] buffer1;
		private final long[] buffer2;
		private final int[] offset;
		
		private Bucket( final long[] buffer0, final long[] buffer1, final long[] buffer2, final int[] offset, final int start, final int end ) {
			this.start = start;
			this.end = end;
			this.offset = offset;
			this.buffer0 = buffer0;
			this.buffer1 = buffer1;
			this.buffer2 = buffer2;
		}
		
		/** The number of triples in this bucket.
		 * 
		 * @return the number of triples in this bucket.
		 */
		public int size() {
			return end - start;
		}
		
		/** Returns the offset of the <code>k</code>-th triple returned by this bucket.
		 * 
		 * <p>This method provides an alternative random access to offset data (w.r.t. indexing the fourth element of the
		 * quadruples returned by {@link #iterator()}). 
		 * 
		 * @param k the index (in iteration order) of a triple.
		 * @return the corresponding offset.
		 */
		
		public int offset( final int k ) {
			return offset[ start + k ];
		}
		
		
		/** Returns an iterator over the triples associated to this bucket; the returned array of longs is reused at each call.
		 * 
		 * @return an iterator over quadruples formed by a triple (indices 0, 1, 2) and the associated offset (index 3).
		 */
		
		public Iterator<long[]> iterator() {
			return new AbstractObjectIterator<long[]>() {
				private int pos = start;
				private long[] triple = new long[ 4 ];

				public boolean hasNext() {
					return pos < end;
				}

				public long[] next() {
					if ( ! hasNext() ) throw new NoSuchElementException();
					final long[] triple = this.triple;
					triple[ 0 ] = buffer0[ pos ];
					triple[ 1 ] = buffer1[ pos ];
					triple[ 2 ] = buffer2[ pos ];
					triple[ 3 ] = offset[ pos ];
					pos++;
					return triple;
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
		n = -1;
	}

	/** Returns an iterator over the buckets of this triple store.
	 *
	 * @return an iterator over the buckets of this triple store.
	 */
	
	public Iterator<Bucket> iterator() {
		for( DataOutputStream d: dos )
			try {
				d.flush();
			}
			catch ( IOException e ) {
				throw new RuntimeException( e );
			}
		
		return new AbstractObjectIterator<Bucket>() {
			private int bucket = 0;
			private FastBufferedInputStream fbis;
			private int last;
			private int bucketSize;
			private final long[] buffer0 = new long[ maxCount ];
			private final long[] buffer1 = new long[ maxCount ];
			private final long[] buffer2 = new long[ maxCount ];
			private final int[] offset = new int[ maxCount ];
			
			public boolean hasNext() {
				return bucket < buckets;
			}
			
			@SuppressWarnings("unchecked")
			public Bucket next() {
				if ( ! hasNext() ) throw new NoSuchElementException();
				final long[] buffer0 = this.buffer0;

				if ( bucket % ( buckets / virtualDiskBuckets ) == 0 ) {
					final int diskBucket = bucket / ( buckets / virtualDiskBuckets );
					final long[] buffer1 = this.buffer1, buffer2 = this.buffer2;

					bucketSize = 0;
					try {
						if ( diskBucketStep == 1 ) {
							fbis = new FastBufferedInputStream( new FileInputStream( file[ diskBucket ] ) );
							bucketSize = count[ diskBucket ];
						}
						else {
							final FileInputStream[] fis = new FileInputStream[ diskBucketStep ];
							for( int i = 0; i < fis.length; i++ ) {
								fis[ i ] = new FileInputStream( file[ diskBucket * diskBucketStep + i ] );
								bucketSize += count[ diskBucket * diskBucketStep + i ];
							}
							fbis = new FastBufferedInputStream( new SequenceInputStream( new IteratorEnumeration( Arrays.asList( fis ).iterator() ) ) );
						}
						final DataInputStream dis = new DataInputStream( fbis );

						final long triple[] = new long[ 3 ];
						int count = 0;
						for( int j = 0; j < bucketSize; j++ ) {
							triple[ 0 ] = dis.readLong();
							triple[ 1 ] = dis.readLong();
							triple[ 2 ] = dis.readLong();

							if ( DEBUG ) System.err.println( "From disk: " + Arrays.toString( triple ) );
							
							if ( filter == null || filter.evaluate( triple ) ) {
								buffer0[ count ] = triple[ 0 ]; 
								buffer1[ count ] = triple[ 1 ]; 
								buffer2[ count ] = triple[ 2 ]; 
								offset[ count ] = dis.readInt();
								count++;
							}
							else dis.readInt(); // Discard offset
						}
						
						bucketSize = count;
						dis.close();
					}
					catch ( IOException e ) {
						throw new RuntimeException( e );
					}

					GenericSorting.quickSort( 0, bucketSize, new IntComparator() {
						public int compare( final int x, final int y ) {
							return Long.signum( buffer0[ x ] - buffer0[ y ] );
						}
					},
					new Swapper() {
						public void swap( final int x, final int y ) {
							final long e0 = buffer0[ x ], e1 = buffer1[ x ], e2 = buffer2[ x ];
							final int v = offset[ x ];
							buffer0[ x ] = buffer0[ y ];
							buffer1[ x ] = buffer1[ y ];
							buffer2[ x ] = buffer2[ y ];
							offset[ x ] = offset[ y ];
							buffer0[ y ] = e0;
							buffer1[ y ] = e1;
							buffer2[ y ] = e2;
							offset[ y ] = v;
						}
					});

					if ( DEBUG ) {
						for( int i = 0; i < bucketSize; i++ ) System.err.println( buffer0[ i ] + ", " + buffer1[ i ] + ", " + buffer2[ i ] );
					}
					
					if ( ! checkedForDuplicates && bucketSize > 1 )
						for( int i = bucketSize - 1; i-- != 0; ) if ( buffer0[ i ] == buffer0[ i + 1 ] && buffer1[ i ] == buffer1[ i + 1 ] && buffer2[ i ] == buffer2[ i + 1 ] ) throw new TripleStore.DuplicateException();
					if ( bucket == buckets - 1 ) checkedForDuplicates = true;
					last = 0;
				}

				final int start = last;
				while( last < bucketSize && ( bucketShift == Long.SIZE ? 0 : buffer0[ last ] >>> bucketShift ) == bucket ) last++;
				bucket++;

				return new Bucket( buffer0, buffer1, buffer2, offset, start, last );
			}
		};
	}
}
