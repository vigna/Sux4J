package it.unimi.dsi.sux4j.io;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2019 Sebastiano Vigna
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

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.commons.collections.Predicate;
import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongBigLists;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.io.SafelyCloseable;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.GOV3Function;
import it.unimi.dsi.sux4j.mph.Hashes;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

/** A temporary store of signatures virtually divided into buckets.
 *
 * <p>A bucketed hash store accumulates elements (objects of type {@code T})
 * by turning them into bit vectors (using a provided {@link TransformationStrategy})
 * and then hashing such vectors into a signature (a pair of longs, i.e., overall we get a hash of 128 bits).
 * Elements can be added {@linkplain #add(Object, long) one by one}
 * or {@linkplain #addAll(Iterator, LongIterator) in batches}.
 * Elements must be distinct, or, more precisely, they must be transformed into distinct bit vectors.
 *
 * <p>Besides the hashes, we store some data associated with each element:
 * if {@linkplain #add(Object) no data is specified}, we store the <em>rank</em> of each element added (the first element added has rank 0,
 * the second one has rank 1, and so on), unless you specified at {@linkplain #BucketedHashStore(TransformationStrategy, File, int, ProgressLogger) construction time}
 * a nonzero <em>hash width</em>: in that case, the value stored by {@link #add(Object)} will be given the lowest bits of the first hash of the signature
 * associated with the object (the hash width is the number of bits stored). This feature makes it possible, for example, to implement a static
 * {@linkplain Builder#dictionary(int) dictionary} using a {@link GOV3Function}.
 *
 * <p>The desired expected bucket size can be set by calling {@link #bucketSize(int)}.
 * Once all elements have been added, one calls {@link #iterator()}, which returns buckets one at a time (in their
 * natural order); signatures within each bucket are returned by increasing value, and signatures within different buckets are in bucket order.
 * Actually, the iterator provided by a bucket returns a <em>triple</em> of longs whose last element is the data associated with the element
 * that generated the signature.
 *
 * <p>Note that the main difference between an instance of this class and one of a {@link ChunkedHashStore} is that
 * the latter can only guarantee that the average chunk (here, bucket) size will be within a factor of two from the
 * desired one, as the number of chunks can be specified only as a {@linkplain ChunkedHashStore#log2Chunks(int) power of two}.
 *
 * <p>It is possible (albeit <em>very</em> unlikely) that different elements generate the same hash. This event is detected
 * during bucket iteration (not while accumulating hashes), and it will throw a {@link BucketedHashStore.DuplicateException}.
 * At that point, the caller must handle the exception by {@linkplain #reset(long) resetting the store} and trying again
 * from scratch. Note that after a few (say, three) exceptions you can safely assume that there are duplicate elements. If you
 * need to force a check on the whole store you can call {@link #check()}. If all your elements come from an {@link Iterable},
 * {@link #checkAndRetry(Iterable, LongIterable)} will try three times to build a checked bucketed hash store.
 *
 * <p>Every {@link #reset(long)} changes the seed used by the store to generate signatures. So, if this seed has to be
 * stored this must happen <em>after</em> the last call to {@link #reset(long)}. To help tracking this fact, a call to
 * {@link #seed()} will <em>lock</em> the store; any further call to {@link #reset(long)} will throw an {@link IllegalStateException}.
 * In case the store needs to be reused, you can call {@link #clear()}, that will bring back the store to after-creation state.
 *
 * <p>When you have finished using a bucketed hash store, you should {@link #close()} it. This class implements
 * {@link SafelyCloseable}, and thus provides a safety-net finalizer.
 *
 * <h2>Filtering</h2>
 *
 * <p>You can at any time {@linkplain #filter(Predicate) set a predicate} that will filter the signatures returned by the store.
 *
 * <h2>Computing frequencies</h2>
 *
 * <p>If you specify so {@linkplain #BucketedHashStore(TransformationStrategy, File, int, ProgressLogger) at construction time},
 * a bucketed hash store will compute for you a {@linkplain #value2FrequencyMap() a map from values to their frequency}.
 *
 * <h2>Implementation details</h2>
 *
 * <p>Internally, a bucketed hash store save signatures into different <em>disk segments</em> using
 * the highest bits (performing, in fact, the first phase of a bucket sort).
 * Once the user chooses a bucket size, the store exhibits the data on disk by grouping disk segments or splitting them
 * into buckets. This process is transparent to the user.
 *
 * <p>The assignment to a bucket happens conceptually by using the first 64-bit hash (shifted by one to the right, to avoid sign issues)
 * to define a number &alpha; in the interval [0..1).
 * Then, the bucket assigned is &lfloor;&alpha;<var>m</var>&rfloor;,
 * where <var>m</var> = 1 + {@link #size()} / {@link #bucketSize()} is the number of buckets.
 * Conceptually, we are mapping &alpha;, a uniform random number in the unit interval, into
 * &lfloor;&alpha;<var>m</var>&rfloor;, a uniform integer number in the range [0..<var>m</var>),
 * by <em>inversion</em>.
 * As show below, the whole computation can
 * be carried out using a fixed-point representation,
 * as it has been done for pseudorandom number generators since the early days, using only
 * a multiplication and shift.
 * The assignment is monotone nondecreasing, which makes it possible
 * to emit the buckets one at a time scanning the keys in sorted order.
 *
 * <p>Signatures have to be loaded into memory only segment by segment, so to be sorted and tested for uniqueness. As long as
 * {@link #DISK_CHUNKS} is larger than eight, the store will need less than 0.75 bits per element of main
 * memory. {@link #DISK_CHUNKS} can be increased arbitrarily at compile time, but each store
 * will open {@link #DISK_CHUNKS} files at the same time. (For the same reason, it is
 * <strong>strongly</strong> suggested that you close your stores as soon as you do not need them).
 *
 * <h2>Intended usage</h2>
 *
 * <p>bucketed hash stores should be built by classes that need to manipulate elements in buckets of approximate given
 * size without needing access to the elements themselves, but just to their signatures, a typical
 * example being {@link GOV3Function}, which uses the signatures to compute a 3-hyperedge. Once a bucketed hash
 * store is built, it can be passed on to further substructures, reducing greatly the computation time (as the original
 * collection need not to be scanned again).
 *
 * <p>To compute the bucket corresponding to a given element, use
 * <pre>
 * final long[] signature = new long[2];
 * Hashes.spooky4(transform.toBitVector(key), seed, signature);
 * final int bucket = Math.multiplyHigh(signature[0] &gt;&gt;&gt; 1, (1 + n / bucketSize) &lt;&lt; 1);
 * </pre>
 * where <code>seed</code> is the {@linkplain #seed() store seed},
 * <code>n</code> is the {@linkplain #size() number of keys},
 * and <code>bucketSize</code> is the {@linkplain #bucketSize(int) provided bucket size}.
 *
 * @author Sebastiano Vigna
 * @since 5.0.0
 */

public class BucketedHashStore<T> implements Serializable, SafelyCloseable, Iterable<BucketedHashStore.Bucket> {
    public static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(BucketedHashStore.class);
	private static final boolean DEBUG = false;

	/** Denotes that the bucketed hash store contains a duplicate signature. */
	public static class DuplicateException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	/** The size of the output buffers. */
	public final static int BUFFER_SIZE = 16 * 1024;
	/** The logarithm of the number of disk segments. */
	public final static int LOG2_DISK_CHUNKS = 8;
	/** The number of disk segments. */
	public final static int DISK_CHUNKS = 1 << LOG2_DISK_CHUNKS;
	/** The shift for disk segments. */
	public final static int DISK_CHUNKS_SHIFT = Long.SIZE - LOG2_DISK_CHUNKS;
	/** The expected bucket size. */
	private int bucketSize;
	/** The number of buckets: 1 + {@link #size()} / {@link #bucketSize()}. */
	private long numBuckets;
	/** The multiplier to perform fixed-point computation of the destination bucket (twice {@link #numBuckets}). */
	private long multiplier;
	/** The number of elements ever {@linkplain #add(Object) added}. */
	protected long size;
	/** The number of elements that pass the current filter, or -1 we it must be recomputed. */
	protected long filteredSize;
	/** The seed used to generate the hash signatures. */
	protected long seed;
	/** The number of signatures in each disk segment. */
	private int[] count;
	/** The files containing disk segments. */
	private File file[];
	/** If true, this store has been checked for duplicates. */
	private boolean checkedForDuplicates;
	/** The transformation strategy provided at construction time. */
	private final TransformationStrategy<? super T> transform;
	/** A progress logger. */
	private final ProgressLogger pl;
	/** If nonzero, no associated data is saved in the store: {@link Bucket#data(long)} will return the first of the three hashes associated with the key, masked by this value. */
	private final long hashMask;
	/** The temporary directory for this bucketed hash store, or {@code null}. */
	private final File tempDir;
	/** The file channels for the disk segments. */
	private WritableByteChannel[] writableByteChannel;
	/** The file channels for the disk segments. */
	private ByteBuffer[] byteBuffer;
	/** If not {@code null}, a filter that will be used to select signatures. */
	private Predicate filter;
	/** Whether this store is locked. Any attempt to {@link #reset(long)} the store will cause an {@link IllegalStateException} if this variable is true.*/
	private boolean locked;
	/** Whether this store has already been closed. */
	private boolean closed;
	/** The optional map from values to count. */
	private Long2LongOpenHashMap value2FrequencyMap;

	/** Creates a bucketed hash store with given transformation strategy.
	 *
	 * @param transform a transformation strategy for the elements.
	 * @param bucketSize the expected bucket size.
	 * @throws IOException
	 */
	public BucketedHashStore(final TransformationStrategy<? super T> transform) throws IOException {
		this(transform, null, null);
	}

	/** Creates a bucketed hash store with given transformation strategy and temporary file directory.
	 *
	 * @param transform a transformation strategy for the elements.
	 * @param bucketSize the expected bucket size.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the current directory.
	 */
	public BucketedHashStore(final TransformationStrategy<? super T> transform, final File tempDir) throws IOException {
		this(transform, tempDir, null);
	}

	/** Creates a bucketed hash store with given transformation strategy.
	 *
	 * @param transform a transformation strategy for the elements.
	 * @param bucketSize the expected bucket size.
	 * @param pl a progress logger, or {@code null}.
	 */
	public BucketedHashStore(final TransformationStrategy<? super T> transform, final ProgressLogger pl) throws IOException {
		this(transform, null, pl);
	}

	/** Creates a bucketed hash store with given transformation strategy and progress logger.
	 *
	 * @param transform a transformation strategy for the elements.
	 * @param bucketSize the expected bucket size.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the current directory.
	 * @param pl a progress logger, or {@code null}.
	 */

	public BucketedHashStore(final TransformationStrategy<? super T> transform, final File tempDir, final ProgressLogger pl) throws IOException {
		this(transform, tempDir, 0, pl);
	}

	/** Creates a bucketed hash store with given transformation strategy, hash width and progress logger.
	 *
	 * @param transform a transformation strategy for the elements.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the current directory.
	 * @param hashWidthOrCountValues if positive, no associated data is saved in the store: {@link Bucket#data(long)} will return this many lower bits
	 * of the first of the three hashes associated with the key; zero, values are stored; if negative, values are stored and a map from values
	 * to their frequency is computed.
	 * @param pl a progress logger, or {@code null}.
	 */

	public BucketedHashStore(final TransformationStrategy<? super T> transform, final File tempDir, final int hashWidthOrCountValues, final ProgressLogger pl) throws IOException {
		this.transform = transform;
		this.pl = pl;
		this.tempDir = tempDir;
		this.bucketSize = 1;
		this.hashMask = hashWidthOrCountValues <= 0 ? 0 : -1L >>> Long.SIZE - hashWidthOrCountValues;
		if (hashWidthOrCountValues < 0) value2FrequencyMap = new Long2LongOpenHashMap();

		file = new File[DISK_CHUNKS];
		writableByteChannel = new WritableByteChannel[DISK_CHUNKS];
		byteBuffer = new ByteBuffer[DISK_CHUNKS];
		// Create disk segments
		for(int i = 0; i < DISK_CHUNKS; i++) {
			byteBuffer[i] = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder());
			writableByteChannel[i] = new FileOutputStream(file[i] = File.createTempFile(BucketedHashStore.class.getSimpleName(), String.valueOf(i), tempDir)).getChannel();
			file[i].deleteOnExit();
		}

		count = new int[DISK_CHUNKS];
	}

	/** Returns the expected bucket size.
	 *
	 * @return the expected bucket size.
	 */
	public int bucketSize() {
		return bucketSize;
	}

	/** Sets the expected bucket size. */
	public void bucketSize(final int bucketSize) {
		this.bucketSize = bucketSize;
	}

	/** Return the current seed of this bucketed hash store. After calling this method, no {@link #reset(long)} will be allowed (unless the store
	 * is {@linkplain #clear() cleared}).
	 *
	 * @return the current seed of this bucketed hash store.
	 */

	public long seed() {
		locked = true;
		return seed;
	}

	/** Return the temporary directory of this bucketed hash store, or {@code null}.
	 *
	 * @return the temporary directory of this bucketed hash store, or {@code null}.
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
	public void add(final T o, final long value) throws IOException {
		final long[] signature = new long[2];
		Hashes.spooky4(transform.toBitVector(o), seed, signature);
		add(signature, value);
	}

	/** Adds an element to this store, associating it with its ordinal position.
	 *
	 * @param o the element to be added.
	 */
	public void add(final T o) throws IOException {
		add(o, filteredSize);
	}

	/** Adds a signature to this store.
	 *
	 * @param signature the signature to be added.
	 * @param value the associated value.
	 */
	private void add(final long[] signature, final long value) throws IOException {
		final int segment = (int)(signature[0] >>> DISK_CHUNKS_SHIFT);
		count[segment]++;
		checkedForDuplicates = false;
		if (DEBUG) System.err.println("Adding " + Arrays.toString(signature));
		writeLong(signature[0], byteBuffer[segment], writableByteChannel[segment]);
		writeLong(signature[1], byteBuffer[segment], writableByteChannel[segment]);
		if (hashMask == 0) writeLong(value, byteBuffer[segment], writableByteChannel[segment]);
		if (filteredSize != -1 && (filter == null || filter.evaluate(signature))) filteredSize++;
		if (value2FrequencyMap != null) value2FrequencyMap.addTo(value, 1);
		size++;
	}

	/** Adds the elements returned by an iterator to this store, associating them with specified values,
	 * possibly building the associated value frequency map.
	 *
	 * @param elements an iterator returning elements.
	 * @param values an iterator on values parallel to {@code elements}.
	 * @param requiresValue2CountMap whether to build the value frequency map (associating with each value its frequency).
	 */
	public void addAll(final Iterator<? extends T> elements, final LongIterator values, final boolean requiresValue2CountMap) throws IOException {
		if (pl != null) {
			pl.expectedUpdates = -1;
			pl.start("Adding elements...");
		}
		final long[] signature = new long[2];
		while(elements.hasNext()) {
			Hashes.spooky4(transform.toBitVector(elements.next()), seed, signature);
			add(signature, values != null ? values.nextLong() : filteredSize);
			if (pl != null) pl.lightUpdate();
		}
		if (values != null && values.hasNext()) throw new IllegalStateException("The iterator on values contains more entries than the iterator on keys");
		if (pl != null) pl.done();
	}

	/** Adds the elements returned by an iterator to this store, associating them with specified values.
	 *
	 * @param elements an iterator returning elements.
	 * @param values an iterator on values parallel to {@code elements}.
	 */
	public void addAll(final Iterator<? extends T> elements, final LongIterator values) throws IOException {
		addAll(elements, values, false);
	}

	/** Adds the elements returned by an iterator to this store, associating them with their ordinal position.
	 *
	 * @param elements an iterator returning elements.
	 */
	public void addAll(final Iterator<? extends T> elements) throws IOException {
		addAll(elements, null);
	}

	private void flushAll() throws IOException {
		for(int i = 0; i < DISK_CHUNKS; i++) flush(byteBuffer[i], writableByteChannel[i]);
	}

	/** Returns the size of this store. Note that if you set up
	 * a {@linkplain #filter(Predicate) filter}, the first call to
	 * this method will require a scan to the whole store.
	 *
	 * @return the number of (possibly filtered) pairs of this store.
	 */

	public long size() throws IOException {
		if (filter == null) return size;
		if (filteredSize == - 1) {
			long c = 0;
			final long[] signature = new long[2];
			final ByteBuffer iteratorByteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder());
			for(int i = 0; i < DISK_CHUNKS; i++) {
				if (filter == null) c += count[i];
				else {
					flushAll();
					@SuppressWarnings("resource")
					final ReadableByteChannel channel = new FileInputStream(file[i]).getChannel();
					iteratorByteBuffer.clear().flip();
					for(int j = 0; j < count[i]; j++) {
						signature[0] = readLong(iteratorByteBuffer, channel);
						signature[1] = readLong(iteratorByteBuffer, channel);
						if (hashMask == 0) readLong(iteratorByteBuffer, channel);
						if (filter.evaluate(signature)) c++;
					}
					channel.close();
				}
			}

			filteredSize = c;
		}
		return filteredSize;
	}

	/** Clears this store. After a call to this method, the store can be reused. */
	public void clear() throws IOException {
		locked = false;
		if (value2FrequencyMap != null) value2FrequencyMap = new Long2LongOpenHashMap();
		reset(0);
	}

	/** Return the current value frequency map.
	 *
	 * @return the current value frequency map.
	 * @throws IllegalStateException if this bucketed hash store does not contain a value frequency map.
	 */
	public Long2LongOpenHashMap value2FrequencyMap() {
		if (value2FrequencyMap == null) throw new IllegalStateException("This bucketed hash store does not contain a value frequency map");
		return value2FrequencyMap;
	}

	private static void writeLong(final long value, final ByteBuffer byteBuffer, final WritableByteChannel channel) throws IOException {
		if (!byteBuffer.hasRemaining()) flush(byteBuffer, channel);
		byteBuffer.putLong(value);
	}

	private static void flush(final ByteBuffer buffer, final WritableByteChannel channel) throws IOException {
		buffer.flip();
		channel.write(buffer);
		buffer.clear();
	}

	private static long readLong(final ByteBuffer byteBuffer, final ReadableByteChannel channel) throws IOException {
		if (! byteBuffer.hasRemaining()) {
			byteBuffer.clear();
			final int result = channel.read(byteBuffer);
			assert result != 0;
			if (result == -1) throw new EOFException();
            byteBuffer.flip();
		}
		return byteBuffer.getLong();
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void finalize() throws Throwable {
		try {
			if (! closed) {
				LOGGER.warn("This " + this.getClass().getName() + " [" + toString() + "] should have been closed.");
				close();
			}
		}
		finally {
			super.finalize();
		}
	}

	/** Closes this store, disposing all associated resources. */
	@Override
	public void close() throws IOException {
		if (! closed) {
			closed = true;
			for(final WritableByteChannel channel: writableByteChannel) channel.close();
			for(final File f: file) f.delete();
		}
	}

	/** Resets this store using a new seed. All accumulated data are cleared, and a new seed is reinstated.
	 *
	 * @param seed the new seed.
	 * @throws IllegalStateException if this store was locked by a call to {@link #seed()}, and never {@linkplain #clear() cleared} thereafter.
	 */

	public void reset(final long seed) throws IOException {
		if (locked) throw new IllegalStateException();
		if (DEBUG) System.err.println("RESET(" + seed + ")");
		filteredSize = 0;
		this.seed = seed;
		checkedForDuplicates = false;
		Arrays.fill(count, 0);
		for (int i = 0; i < DISK_CHUNKS; i++) {
			writableByteChannel[i].close();
			byteBuffer[i].clear();
			writableByteChannel[i] = new FileOutputStream(file[i]).getChannel();
		}
	}

	/** Checks that this store has no duplicate signatures, throwing an exception if this fails to happen.
	 *
	 * @throws DuplicateException if this store contains duplicate signatures.
	 */


	public void check() throws DuplicateException {
		for(final BucketedHashStore.Bucket b: this) b.iterator();
	}

	/** Checks that this store has no duplicate signatures, and try to rebuild if this fails to happen.
	 *
	 * @param iterable the elements with which the store will be refilled if there are duplicate signatures.
	 * @param values the values that will be associated with the elements returned by <code>iterable</code>.
	 * @throws IllegalArgumentException if after a few trials the store still contains duplicate signatures.
	 */
	public void checkAndRetry(final Iterable<? extends T> iterable, final LongIterable values) throws IOException {
		final RandomGenerator random = new XoRoShiRo128PlusRandomGenerator();
		int duplicates = 0;

		for(;;)
			try {
				check();
				break;
			}
			catch (final DuplicateException e) {
				if (duplicates++ > 3) throw new IllegalArgumentException("The input list contains duplicates");
				LOGGER.warn("Found duplicate. Recomputing signatures...");
				reset(random.nextLong());
				addAll(iterable.iterator(), values.iterator());
			}

		checkedForDuplicates = true;
	}

	/** Checks that this store has no duplicate signatures, and try to rebuild if this fails to happen.
	 *
	 * <p><strong>Warning</strong>: the actions are executed exactly in the specified order&mdash;<em>first</em>
	 * check, <em>then</em> retry. If you invoke this method on an empty store you'll get a checked empty store.
	 *
	 * @param iterable the elements with which the store will be refilled if there are duplicate signatures.
	 * @throws IllegalArgumentException if after a few trials the store still contains duplicate signatures.
	 */
	public void checkAndRetry(final Iterable<? extends T> iterable) throws IOException {
		checkAndRetry(iterable, null);
	}

	/** Generate a list of signatures using the lowest bits of the first hash in this store.
	 *
	 * <p>For this method to work, this store must contain ranks.
	 *
	 * @param signatureWidth the width in bits of the signatures.
	 * @param pl a progress logger.
	 */

	public LongBigList signatures(final int signatureWidth, final ProgressLogger pl) throws IOException {
		final LongBigList signatures = LongArrayBitVector.getInstance().asLongBigList(signatureWidth);
		final long signatureMask = -1L >>> Long.SIZE - signatureWidth;
		signatures.size(size());
		pl.expectedUpdates = size();
		pl.itemsName = "signatures";
		pl.start("Signing...");
		for (final Bucket bucket: this) {
			final Iterator<long[]> bucketIterator = bucket.iterator();
			for(int i = bucket.size(); i-- != 0;) {
				final long[] triple = bucketIterator.next();
				signatures.set(triple[2], signatureMask & triple[0]);
				pl.lightUpdate();
			}
		}
		pl.done();
		return signatures;
	}


	/** A bucket returned by a {@link BucketedHashStore}. */
	public final static class Bucket implements Iterable<long[]> {
		/** The index of this bucket (the ordinal position in the bucket enumeration). */
		private final int index;
		/** The start position of this bucket in the parallel arrays {@link #buffer0}, {@link #buffer1}, and {@link #data}. */
		private final int start;
		/** The final position (excluded) of this bucket in the parallel arrays {@link #buffer0}, {@link #buffer1}, and {@link #data}. */
		private final int end;
		private final long[] buffer0;
		private final long[] buffer1;
		private final long[] data;
		private final long hashMask;

		private Bucket(final int index, final long[] buffer0, final long[] buffer1, final long[] data, final long hashMask, final int start, final int end) {
			this.index = index;
			this.start = start;
			this.end = end;
			this.data = data;
			this.hashMask = hashMask;
			this.buffer0 = buffer0;
			this.buffer1 = buffer1;
		}

		/** Copy constructor for multi-threaded bucket analysis.
		 *
		 * @param bucket a bucket to be copied.
		 */
		public Bucket(final Bucket bucket) {
			index = bucket.index;
			hashMask = bucket.hashMask;
			start = 0;
			end = bucket.end - bucket.start;
			buffer0 = Arrays.copyOfRange(bucket.buffer0, bucket.start, bucket.end);
			buffer1 = Arrays.copyOfRange(bucket.buffer1, bucket.start, bucket.end);
			data = bucket.data == null ? null : Arrays.copyOfRange(bucket.data, bucket.start, bucket.end);
		}

		/** Creates a bucket with all field set to zero or null. Mainly useful to create marker objects. */
		public Bucket() {
			this.index = 0;
			this.start = 0;
			this.end = 0;
			this.data = null;
			this.hashMask = 0;
			this.buffer0 = null;
			this.buffer1 = null;
		}

		/** The number of signatures in this bucket.
		 *
		 * @return the number of signatures in this bucket.
		 */
		public int size() {
			return end - start;
		}

		/** The index of this bucket.
		 *
		 * @return the index of this bucket.
		 */
		public int index() {
			return index;
		}

		/** Returns the data of the <code>k</code>-th signature returned by this bucket.
		 *
		 * <p>This method provides an alternative random access to data (w.r.t. indexing the fourth element of the
		 * quadruples returned by {@link #iterator()}).
		 *
		 * @param k the index (in iteration order) of a signature.
		 * @return the corresponding data.
		 */

		public long data(final long k) {
			return data != null ? data[(int)(start + k)] : (buffer0[(int)(start + k)] & hashMask);
		}


		/** Returns an iterator over the triples associated with this bucket; the returned array of longs is reused at each call.
		 *
		 * @return an iterator over triples formed by a signature (indices 0, 1) and the associated data (index 2).
		 */

		@Override
		public Iterator<long[]> iterator() {
			return new ObjectIterator<long[]>() {
				private int pos = start;
				private final long[] quadruple = new long[4];

				@Override
				public boolean hasNext() {
					return pos < end;
				}

				@Override
				public long[] next() {
					if (! hasNext()) throw new NoSuchElementException();
					final long[] quadruple = this.quadruple;
					quadruple[0] = buffer0[pos];
					quadruple[1] = buffer1[pos];
					quadruple[2] = data != null ? data[pos] : buffer0[pos] & hashMask;
					pos++;
					return quadruple;
				}

			};
		}

		/** Commodity methods that exposes transparently either the data contained in the bucket,
		 * or the data obtained by using the bucket to index a list.
		 *
		 * @param values a list of values. Must be either an instance of {@link LongList}, or an instance of {@link LongBigList}. If it
		 * is not {@code null}, the data in the bucket is used to index this list and return a value. Otherwise, the data in the bucket
		 * is returned directly.
		 * @return a big list of longs representing the values associated with each element in the bucket.
		 */
		public LongBigList valueList(final LongIterable values) {
			return new AbstractLongBigList() {
				private final LongBigList valueList = values == null ? null : (values instanceof LongList ? LongBigLists.asBigList((LongList)values) : (LongBigList)values);

				@Override
				public long size64() {
					return Bucket.this.size();
				}

				@Override
				public long getLong(final long index) {
					return valueList == null ? Bucket.this.data(index) : valueList.getLong(Bucket.this.data(index));
				}
			};
		}
	}

	/** Sets a filter for this store.
	 *
	 * @param filter a predicate that will be used to filter signatures.
	 */
	public void filter(final Predicate filter) {
		this.filter = filter;
		filteredSize = -1;
	}

	/** Returns an iterator over the buckets of this bucketed hash store.
	 *
	 * <p>Note that at each iteration part of the state of this bucketed hash store
	 * is reused. Thus, after each call to {@code next()} the previously returned
	 * {@link Bucket} will be no longer valid. Please use the provided
	 * {@linkplain Bucket#Bucket(Bucket) copy constructor} if you need to process
	 * in parallel several buckets.
	 *
	 * @return an iterator over the buckets of this bucketed hash store.
	 */
	@Override
	public Iterator<Bucket> iterator() {
		if (closed) throw new IllegalStateException("This " + getClass().getSimpleName() + " has been closed ");
		try {
			flushAll();
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}

		int m = 0;
		for(int i = 0; i < DISK_CHUNKS; i++) if (m < count[i]) m = count[i];

		final int maxCount = m + 16 * bucketSize; // Some headroom for partial buckets

		try {
			numBuckets = 1 + size() / bucketSize;
			multiplier = numBuckets * 2;
		} catch(final IOException e) {
			throw new RuntimeException(e);
		}

		return new ObjectIterator<Bucket>() {
			private int bucket;
			private ReadableByteChannel channel;
			private final ByteBuffer iteratorByteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder());
			private int last;
			private int diskSegmentSize;
			private int nextDiskSegment;
			private final long[] buffer0 = new long[maxCount];
			private final long[] buffer1 = new long[maxCount];
			private final long[] data = hashMask != 0 ? null : new long[maxCount];

			@Override
			public boolean hasNext() {
				return last < diskSegmentSize || nextDiskSegment != DISK_CHUNKS;
			}

			@SuppressWarnings("resource")
			@Override
			public Bucket next() {
				if (! hasNext()) throw new NoSuchElementException();
				final long[] buffer0 = this.buffer0;
				int start;

				int incr = 1;
				for(;;) {
					start = last;
					// Galloping search for the next bucket
					for(incr = 1; last + incr < diskSegmentSize && Math.multiplyHigh(buffer0[last + incr] >>> 1, multiplier) == bucket; incr <<= 1);

					if (last + incr < diskSegmentSize || nextDiskSegment == DISK_CHUNKS) break;
					final long[] buffer1 = this.buffer1;
					// Move partial data to the beginning
					final int residual = diskSegmentSize - start;
					System.arraycopy(buffer0, start, buffer0, 0, residual);
					System.arraycopy(buffer1, start, buffer1, 0, residual);
					if (data != null) System.arraycopy(data, start, data, 0, residual);

					try {
						channel = new FileInputStream(file[nextDiskSegment]).getChannel();
						int pos = residual;

						iteratorByteBuffer.clear().flip();
						final long signature[] = new long[2];
						final int nextSegmentSize = count[nextDiskSegment];
						for(int j = 0; j < nextSegmentSize; j++) {
							signature[0] = readLong(iteratorByteBuffer, channel);
							signature[1] = readLong(iteratorByteBuffer, channel);

							if (DEBUG) System.err.println("From disk: " + Arrays.toString(signature));

							if (filter == null || filter.evaluate(signature)) {
								buffer0[pos] = signature[0];
								buffer1[pos] = signature[1];
								if (hashMask == 0) data[pos] = readLong(iteratorByteBuffer, channel);
								pos++;
							}
							else if (hashMask == 0) readLong(iteratorByteBuffer, channel); // Discard data
						}

						diskSegmentSize = pos;
						channel.close();
					}
					catch (final IOException e) {
						throw new RuntimeException(e);
					}

					it.unimi.dsi.fastutil.Arrays.parallelQuickSort(residual, diskSegmentSize, (x, y) -> {
						final int t = Long.compareUnsigned(buffer0[x], buffer0[y]);
						if (t != 0) return t;
						return Long.compareUnsigned(buffer1[x], buffer1[y]);
					},
					(x, y) -> {
						final long e0 = buffer0[x], e1 = buffer1[x];
						buffer0[x] = buffer0[y];
						buffer1[x] = buffer1[y];
						buffer0[y] = e0;
						buffer1[y] = e1;
						if (hashMask == 0) {
							final long v = data[x];
							data[x] = data[y];
							data[y] = v;
						}
					});

					last = 0;
					nextDiskSegment++;
				}

				int to = Math.min(diskSegmentSize, last + incr);
				last += incr >>> 1;
				while(last < to) {
					final int mid = (last + to) >>> 1;
					if (Math.multiplyHigh(buffer0[mid] >>> 1, multiplier) == bucket) last = mid + 1;
					else to = mid;
				}

				if (!checkedForDuplicates && start < last)
					for (int i = start + 1; i < last; i++)
						if (buffer0[i - 1] == buffer0[i] && buffer1[i - 1] == buffer1[i])
							throw new DuplicateException();
				if (bucket == numBuckets - 1 && last == diskSegmentSize) checkedForDuplicates = true;

				return new Bucket(bucket++, buffer0, buffer1, data, hashMask, start, last);
			}
		};
	}
}
