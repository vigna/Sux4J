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

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.logging.ProgressLogger;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.iterators.IteratorEnumeration;
import org.apache.log4j.Logger;

import cern.colt.GenericSorting;
import cern.colt.Swapper;
import cern.colt.function.IntComparator;

/** A store of triples
 * @author Sebastiano Vigna
 * @since 1.0.4
 */

public class TripleStore<T> implements Serializable, Closeable, Iterable<TripleStore.Bucket> {
    public static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Util.getLogger( TripleStore.class );
	private static final boolean ASSERTS = false;
	private static final boolean DEBUG = false;
		
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
	/** The seed used to generate the initial hash triple. */
	protected long seed;
	private int[] count;
	private long[][] buffer;
	private int buckets;
	private File file[];
	private int diskBucketStep;
	private int bucketShift;
	private boolean checkedForDuplicates;
	private final TransformationStrategy<? super T> transform;
	private final ProgressLogger pl;
	private DataOutputStream[] dos;
	private int[] offset;
	private int virtualDiskBuckets;
	private Predicate filter;

	public TripleStore( final TransformationStrategy<? super T> transform ) {
		this( transform, null );
	}
		
	/** Creates a new function for the given elements and values.
	 * 
	 * @param transform a transformation strategy for the elements.
	 * @param pl a progress logger, or <code>null</code>.
	 */

	public TripleStore( final TransformationStrategy<? super T> transform, final ProgressLogger pl ) {
		this.transform = transform;
		this.pl = pl;
		
		file = new File[ DISK_BUCKETS ];
		dos = new DataOutputStream[ DISK_BUCKETS ];
		try {
			// Create disk buckets
			for( int i = 0; i < DISK_BUCKETS; i++ ) {
				dos[ i ] = new DataOutputStream( new FastBufferedOutputStream( new FileOutputStream( file[ i ] = File.createTempFile( TripleStore.class.getSimpleName(), String.valueOf( i ) ) ) ) );
				file[ i ].deleteOnExit();
			}

			count = new int[ DISK_BUCKETS ];
		}
		catch ( IOException e ) {
			throw new RuntimeException( e );
		}
	}

	public long seed() {
		return seed;
	}
	
	public void add( final T o ) {
		final long[] triple = new long[ 3 ];
		Hashes.jenkins( transform.toBitVector( o ), seed, triple );
		add( triple );
	}
	
	private void add( final long[] triple ) {
		final int bucket = (int)( triple[ 0 ] >>> DISK_BUCKETS_SHIFT );
		count[ bucket ]++;
		checkedForDuplicates = false;
		try {
			if ( DEBUG ) System.err.println( "Adding " + Arrays.toString( triple ));
			dos[ bucket ].writeLong( triple[ 0 ] );
			dos[ bucket ].writeLong( triple[ 1 ] );
			dos[ bucket ].writeLong( triple[ 2 ] );
			dos[ bucket ].writeInt( n );
		}
		catch ( IOException e ) {
			throw new RuntimeException( e );
		}
		if ( n != -1 && ( filter == null || filter.evaluate( triple ) ) ) n++;
	}
	
	public void addAll( final Iterator<? extends T> iterator ) {
		if ( pl != null ) pl.start( "Adding elements..." );
		final long[] triple = new long[ 3 ];
		while( iterator.hasNext() ) {
			Hashes.jenkins( transform.toBitVector( iterator.next() ), seed, triple );
			add( triple );
			if ( pl != null ) pl.lightUpdate();
		}
		if ( pl != null ) pl.done();
	}

	public int size() {
		if ( n == - 1 ) {
			int c = 0;
			try {
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
			}
			catch ( IOException e ) {
				throw new RuntimeException( e );
			}
			
			n = c;
		}
		return n;
	}
	
	public void close() {
		for( DataOutputStream d: dos )
			try {
				d.close();
			}
			catch ( IOException e ) {
				throw new RuntimeException( e );
			}
		for( File f: file ) f.delete();
	}
	
	public void reset( final long seed ) {
		if ( DEBUG ) System.err.println( "RESET(" + seed + ")" );
		n = 0;
		this.seed = seed;
		checkedForDuplicates = false;
		Arrays.fill( count, 0 );
		try {
			for( int i = 0; i < DISK_BUCKETS; i++ ) dos[ i ] = new DataOutputStream( new FastBufferedOutputStream( new FileOutputStream( file[ i ] ) ) );
		}
		catch ( IOException e ) {
			throw new RuntimeException( e );
		}
	}
	
	public int log2Buckets( final int log2Buckets ) {
		this.buckets = 1 << log2Buckets;
		diskBucketStep = Math.max( DISK_BUCKETS / buckets, 1 );
		virtualDiskBuckets = DISK_BUCKETS / diskBucketStep;
		int maxCount = 0;
		
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

		buffer = new long[ 3 ][ maxCount ];
 		offset = new int[ maxCount ];
		
		return bucketShift;
	}
	
	public final static class Bucket implements Iterable<long[]> {
		private final int start;
		private final long[] buffer0;
		private final long[] buffer1;
		private final long[] buffer2;
		private final int[] offset;
		private final int end;
		
		public Bucket( final long[][] buffer, final int[] offset, final int start, final int end ) {
			this.offset = offset;
			this.start = start;
			this.end = end;
			buffer0 = buffer[ 0 ];
			buffer1 = buffer[ 1 ];
			buffer2 = buffer[ 2 ];
		}
		
		public int size() {
			return end - start;
		}
		
		public int offset( int k ) {
			return offset[ start + k ];
		}
		
		public Iterator<long[]> iterator() {
			return new AbstractObjectIterator<long[]>() {
				private int pos = start;
				private long[] triple = new long[ 3 ];

				public boolean hasNext() {
					return pos < end;
				}

				public long[] next() {
					if ( ! hasNext() ) throw new NoSuchElementException();
					final long[] triple = this.triple;
					triple[ 0 ] = buffer0[ pos ];
					triple[ 1 ] = buffer1[ pos ];
					triple[ 2 ] = buffer2[ pos ];
					pos++;
					return triple;
				}

			};
		}
	}
	
	public void filter( final Predicate filter ) {
		this.filter = filter;
		n = -1;
	}
	
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
			
			public boolean hasNext() {
				return bucket < buckets;
			}
			
			public Bucket next() {
				if ( ! hasNext() ) throw new NoSuchElementException();
				if ( bucket % ( buckets / virtualDiskBuckets ) == 0 ) {
					final int diskBucket = bucket / ( buckets / virtualDiskBuckets );

					final long[] buffer0 = buffer[ 0 ], buffer1 = buffer[ 1 ], buffer2 = buffer[ 2 ];

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
					checkedForDuplicates = true;
					last = 0;
				}

				final int start = last;
				while( last < bucketSize && ( bucketShift == Long.SIZE ? 0 : buffer[ 0 ][ last ] >>> bucketShift ) == bucket ) last++;
				bucket++;

				return new Bucket( buffer, offset, start, last );
			}
		};
	}
}
