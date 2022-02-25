/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2017-2022 Sebastiano Vigna
 *
 * This program and the accompanying materials are made available under the
 * terms of the GNU Lesser General Public License v2.1 or later,
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html,
 * or the Apache Software License 2.0, which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later OR Apache-2.0
 */

package it.unimi.dsi.sux4j.mph;

import static it.unimi.dsi.bits.LongArrayBitVector.bits;
import static it.unimi.dsi.bits.LongArrayBitVector.words;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.util.Pair;
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

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.LongBigArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.io.FileLinesByteArrayIterable;
import it.unimi.dsi.io.FileLinesMutableStringIterable;
import it.unimi.dsi.io.OfflineIterable;
import it.unimi.dsi.io.OfflineIterable.OfflineIterator;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.BucketedHashStore;
import it.unimi.dsi.sux4j.io.BucketedHashStore.Bucket;
import it.unimi.dsi.sux4j.io.BucketedHashStore.DuplicateException;
import it.unimi.dsi.sux4j.mph.codec.Codec;
import it.unimi.dsi.sux4j.mph.codec.Codec.Decoder;
import it.unimi.dsi.sux4j.mph.codec.Codec.Huffman;
import it.unimi.dsi.sux4j.mph.codec.Codec.ZeroCodec;
import it.unimi.dsi.sux4j.mph.solve.Linear3SystemSolver;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;
import it.unimi.dsi.util.concurrent.ReorderingBlockingQueue;

/**
 * An immutable function stored in a compressed form.
 *
 * <p>
 * Instances of this class store a function from keys to values. Keys are provided by an
 * {@linkplain Iterable iterable object} (whose iterators must return elements in a consistent
 * order), whereas values are provided by a {@link LongIterable}. If you do not specify values, each
 * key will be assigned its rank (e.g., its position in iteration order starting from zero).
 *
 * <p>
 * The values must have a skewed distribution: some values must be much more frequent than others.
 * In that case, this data structure uses much less space than, say, a {@link GOV3Function} because
 * it is able to use, for each key, a number of bits close to the empirical entropy of the value
 * list (with an additional &#8776;10%). It is faster than a {@link GV4CompressedFunction}, and it
 * can be built more quickly, but it uses more space.
 *
 * <p>
 * In addition to the standard construction, which uses lazy Gaussian elimination to solve a number
 * of random linear systems, this implementation has the option of enlarging slightly the space used
 * by the structure (+12%) and use a <em>peeling</em> procedure (essentially, triangulation) to
 * solve the systems. The construction time can be significantly shorter in this case.
 *
 * <P>
 * For convenience, this class provides a main method that reads from standard input a (possibly
 * <code>gzip</code>'d) sequence of newline-separated strings, and writes a serialised function
 * mapping each element of the list to its position, or to a given list of values.
 *
 * <h2>Building a function</h2>
 *
 * <p>
 * This class provides a great amount of flexibility when creating a new function; such flexibility
 * is exposed through the {@linkplain Builder builder}. To exploit the various possibilities, you
 * must understand some details of the construction.
 *
 * <p>
 * In a first phase, we build a {@link BucketedHashStore} containing hashes of the keys. By default,
 * the store will associate each hash with the rank of the key. If you
 * {@linkplain Builder#values(LongIterable) specify values}, the store will associate with each hash
 * the corresponding value.
 *
 * <p>
 * However, if you further require an {@linkplain Builder#indirect() indirect} construction the
 * store will associate again each hash with the rank of the corresponding key, and access randomly
 * the values (which must be either a {@link LongList} or a {@link LongBigList}). Indirect
 * construction is useful only in complex, multi-layer hashes (such as an
 * {@link LcpMonotoneMinimalPerfectHashFunction}) in which we want to reuse a checked
 * {@link BucketedHashStore}. Storing values in the {@link BucketedHashStore} is extremely scalable
 * because the values must just be a {@link LongIterable} that will be scanned sequentially during
 * the store construction. On the other hand, if you have already a store that associates ordinal
 * positions, and you want to build a new function for which a {@link LongList} or
 * {@link LongBigList} of values needs little space (e.g., because it is described implicitly), you
 * can opt for an {@linkplain Builder#indirect() indirect} construction using the already built
 * store.
 *
 * <p>
 * Note that if you specify a store it will be used before building a new one (possibly because of a
 * {@link it.unimi.dsi.sux4j.io.BucketedHashStore.DuplicateException DuplicateException}), with
 * obvious benefits in terms of performance. If the store is not checked, and a
 * {@link it.unimi.dsi.sux4j.io.BucketedHashStore.DuplicateException DuplicateException} is thrown,
 * the constructor will try to rebuild the store, but this requires, of course, that the keys, and
 * possibly the values, are available. Note that it is your responsibility to pass a correct store.
 *
 * <h2>Multithreading</h2>
 *
 * <p>
 * This implementation is multithreaded: each bucket returned by the {@link BucketedHashStore} is
 * processed independently. By default, this class uses {@link Runtime#availableProcessors()}
 * parallel threads, but by default no more than 4. If you wish to set a specific number of threads,
 * you can do so through the system property {@value #NUMBER_OF_THREADS_PROPERTY}.
 *
 * <h2>Implementation Details</h2>
 *
 * <p>
 * The detail of the data structure can be found in &ldquo;Engineering Compressed Functions&rdquo;,
 * by Marco Genuzio and Sebastiano Vigna, 2017. The theoretical basis for the construction is
 * described by J&oacute;hannes B. Hreinsson, Morten Kr&oslash;yer, and Rasmus Pagh in
 * &ldquo;Storing a compressed function with constant time access&rdquo;, <i> Algorithms - ESA 2009,
 * 17th Annual European Symposium</i>, 2009, pages 730&minus;741.
 *
 * Each output value is represented by a codeword from a prefix-free code (by default, a
 * length-limited {@linkplain Huffman Huffman} code). We generate a random 3-regular linear system
 * on <b>F</b><sub>2</sub>, where the known term of the <var>k</var>-th equation is a bit in a bit
 * array. When we read the bit array at three suitable positions depending on an input key, we can
 * recover the codeword representing the output value.
 *
 * @see GV4CompressedFunction
 * @author Sebastiano Vigna
 * @author Marco Genuzio
 * @since 4.2.0
 */

public class GV3CompressedFunction<T> extends AbstractObject2LongFunction<T> implements Serializable, Size64 {
	private static final long serialVersionUID = 1L;
	private static final LongArrayBitVector END_OF_SOLUTION_QUEUE = LongArrayBitVector.getInstance();
	private static final Pair<Bucket, Integer> END_OF_BUCKET_QUEUE = new Pair<>(new Bucket(), Integer.valueOf(0));
	private static final Logger LOGGER = LoggerFactory.getLogger(GV3CompressedFunction.class);
	private static final boolean DEBUG = false;
	protected static final int SEED_BITS = 10;
	protected static final int OFFSET_BITS = Long.SIZE - SEED_BITS;

	/**
	 * The local seed is generated using this step, so to be easily embeddable in
	 * {@link #offsetAndSeed}.
	 */
	private static final long SEED_STEP = 1L << -SEED_BITS;
	/**
	 * The lowest 54 bits of {@link #offsetAndSeed} contain the number of keys stored up to the given
	 * bucket.
	 */
	private static final long OFFSET_MASK = -1L >>> SEED_BITS;
	private static final long SEED_MASK = -1L << -SEED_BITS;

	/** The system property used to set the number of parallel threads. */
	public static final String NUMBER_OF_THREADS_PROPERTY = "it.unimi.dsi.sux4j.mph.threads";

	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected File tempDir;
		protected BucketedHashStore<T> bucketedHashStore;
		protected LongIterable values;
		protected boolean indirect;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;
		protected Codec codec;
		protected boolean peeled;

		/**
		 * Specifies the keys of the function; if you have specified a {@link #store(BucketedHashStore)
		 * BucketedHashStore}, it can be {@code null}.
		 *
		 * @param keys the keys of the function.
		 * @return this builder.
		 */
		public Builder<T> keys(final Iterable<? extends T> keys) {
			this.keys = keys;
			return this;
		}

		/**
		 * Specifies the transformation strategy for the {@linkplain #keys(Iterable) keys of the function};
		 * the strategy can be {@linkplain TransformationStrategies raw}.
		 *
		 * @param transform a transformation strategy for the {@linkplain #keys(Iterable) keys of the
		 *            function}.
		 * @return this builder.
		 */
		public Builder<T> transform(final TransformationStrategy<? super T> transform) {
			this.transform = transform;
			return this;
		}

		/**
		 * Specifies a temporary directory for the {@link #store(BucketedHashStore) BucketedHashStore}.
		 *
		 * @param tempDir a temporary directory for the {@link #store(BucketedHashStore) BucketedHashStore}
		 *            files, or {@code null} for the standard temporary directory.
		 * @return this builder.
		 */
		public Builder<T> tempDir(final File tempDir) {
			this.tempDir = tempDir;
			return this;
		}

		/**
		 * Specifies a bucketed hash store containing the keys.
		 *
		 * <p>
		 * Note that if you specify a store, it is your responsibility that it conforms to the rest of the
		 * data: it must contain ranks if you do not specify {@linkplain #values(LongIterable) values} or if
		 * you use the {@linkplain #indirect() indirect} feature, values otherwise.
		 *
		 * @param bucketedHashStore a bucketed hash store containing the keys associated with their values
		 *            and counting value frequencies, or {@code null}; the store can be unchecked, but in
		 *            this case you must specify {@linkplain #keys(Iterable) keys} and a
		 *            {@linkplain #transform(TransformationStrategy) transform} (otherwise, in case of a
		 *            hash collision in the store an {@link IllegalStateException} will be thrown).
		 * @return this builder.
		 */
		public Builder<T> store(final BucketedHashStore<T> bucketedHashStore) {
			this.bucketedHashStore = bucketedHashStore;
			return this;
		}

		/**
		 * Specifies the values assigned to the {@linkplain #keys(Iterable) keys}; the output width of the
		 * function will be the minimum width needed to represent all values.
		 *
		 * @param values values to be assigned to each element, in the same order of the
		 *            {@linkplain #keys(Iterable) keys}.
		 * @return this builder.
		 */
		public Builder<T> values(final LongIterable values) {
			this.values = values;
			return this;
		}

		/**
		 * Specifies that the function construction must be indirect: a provided
		 * {@linkplain #store(BucketedHashStore) store} contains indices that must be used to access the
		 * {@linkplain #values(LongIterable) values}.
		 *
		 * <p>
		 * If you specify this option, the provided values <strong>must</strong> be a {@link LongList} or a
		 * {@link LongBigList}.
		 *
		 * @return this builder.
		 */
		public Builder<T> indirect() {
			this.indirect = true;
			return this;
		}

		/**
		 * Specifies a {@linkplain Codec codec} that will be used to encode the function output values. The
		 * default is a {@linkplain Huffman} codec with default parameters.
		 *
		 * @param codec a codec that will be used to encode the function output values
		 * @return this builder.
		 */
		public Builder<T> codec(final Codec codec) {
			this.codec = codec;
			return this;
		}

		/**
		 * Specifies to use peeling rather than lazy Gaussian elimination; the resulting structure uses +12%
		 * space, but it can be constructed much more quickly.
		 *
		 * @return this builder.
		 */
		public Builder<T> peeled() {
			this.peeled = true;
			return this;
		}

		/**
		 * Builds a new function.
		 *
		 * @return a {@link GOV3Function} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public GV3CompressedFunction<T> build() throws IOException {
			if (built) throw new IllegalStateException("This builder has been already used");
			if (codec == null) codec = new Codec.Huffman();
			built = true;
			if (transform == null) if (bucketedHashStore != null) transform = bucketedHashStore.transform();
			else throw new IllegalArgumentException("You must specify a TransformationStrategy, either explicitly or via a given BucketedHashStore");
			return new GV3CompressedFunction<>(keys, transform, values, indirect, tempDir, bucketedHashStore, codec, peeled);
		}
	}

	public static final double DELTA_PEEL = 1.23;
	public static final double DELTA_GAUSSIAN = 1.10;
	/** The expected bucket size. */
	public final static int BUCKET_SIZE = 1000; // This should be larger when peeling and using large pages

	private final int deltaTimes256;
	/** The multiplier for buckets. */
	private final long multiplier;
	/** The number of keys. */
	protected final long n;
	/** Length of longest codeword **/
	protected final int globalMaxCodewordLength;
	/** The seed used to generate the initial signature. */
	protected long globalSeed;
	/**
	 * A long containing three values per bucket:
	 * <ul>
	 * <li>the top {@link #SEED_BITS} bits contain the seed (note that it must not be shifted right);
	 * <li>the remaining lower bits contain the starting position in {@link #data} of the bits
	 * associated with the bucket.
	 * </ul>
	 */
	protected final long[] offsetAndSeed;
	/** A bit vector storing the main data array. */
	protected final BitVector data;
	/**
	 * The transformation strategy to turn objects of type <code>T</code> into bit vectors.
	 */
	protected final TransformationStrategy<? super T> transform;
	/** The decoder that will be used to yield output values. */
	protected final Decoder decoder;
	/** {@link Decoder#escapeLength()} from {@link #decoder}, cached. */
	protected final int escapeLength;
	/** {@link Decoder#escapedSymbolLength()} from {@link #decoder}, cached. */
	protected final int escapedSymbolLength;

	/**
	 * Creates a new function for the given keys and values.
	 *
	 * @param keys the keys in the domain of the function, or {@code null}.
	 * @param transform a transformation strategy for the keys.
	 * @param values values to be assigned to each element, in the same order of the iterator returned
	 *            by <code>keys</code>; if {@code null}, the assigned value will the ordinal number of
	 *            each element.
	 * @param indirect if true, <code>bucketedHashStore</code> contains ordinal positions, and
	 *            <code>values</code> is a {@link LongIterable} that must be accessed to retrieve the
	 *            actual values.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard
	 *            temporary directory.
	 * @param bucketedHashStore a bucketed hash store containing the keys associated with their values
	 *            and counting value frequencies, or {@code null}; the store can be unchecked, but in
	 *            this case <code>keys</code> and <code>transform</code> must be non-{@code null}.
	 * @param codec the {@link Codec} used to encode values.
	 * @param peeled whether to use peeling rather than lazy Gaussian elimination; the resulting
	 *            structure uses +12% space, but it can be constructed much more quickly.
	 */
	@SuppressWarnings("resource")
	protected GV3CompressedFunction(final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final LongIterable values, final boolean indirect, final File tempDir, BucketedHashStore<T> bucketedHashStore, final Codec codec, final boolean peeled) throws IOException {
		Objects.requireNonNull(codec, "Null codec");
		this.transform = transform;
		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom();
		pl.itemsName = "keys";
		final boolean givenBucketedHashStore = bucketedHashStore != null;
		if (!givenBucketedHashStore) {
			if (keys == null) throw new IllegalArgumentException("If you do not provide a bucketed hash store, you must provide the keys");
			bucketedHashStore = new BucketedHashStore<>(transform, tempDir, -1, pl);
			bucketedHashStore.reset(r.nextLong());
			if (values == null || indirect) bucketedHashStore.addAll(keys.iterator());
			else bucketedHashStore.addAll(keys.iterator(), values.iterator());
		}
		n = bucketedHashStore.size();
		defRetValue = -1;
		deltaTimes256 = (int)Math.floor((peeled ? DELTA_PEEL : DELTA_GAUSSIAN) * 256);
		final Long2LongOpenHashMap frequencies;
		if (indirect) {
			frequencies = new Long2LongOpenHashMap();
			for (final long v : values) frequencies.addTo(v, 1);
		} else frequencies = bucketedHashStore.value2FrequencyMap();
		final Codec.Coder coder = frequencies.isEmpty() ? ZeroCodec.getInstance().getCoder(frequencies) : codec.getCoder(frequencies);

		globalMaxCodewordLength = coder.maxCodewordLength();
		decoder = coder.getDecoder();
		escapedSymbolLength = decoder.escapedSymbolLength();
		escapeLength = decoder.escapeLength();

		bucketedHashStore.bucketSize(BUCKET_SIZE);
		if (n / BUCKET_SIZE + 1 > Integer.MAX_VALUE) throw new IllegalStateException("This class supports at most " + ((Integer.MAX_VALUE - 1) * BUCKET_SIZE - 1) + " keys");
		final int numBuckets = (int)(n / BUCKET_SIZE + 1);
		multiplier = numBuckets * 2L;

		LOGGER.debug("Number of buckets: " + numBuckets);
		offsetAndSeed = new long[numBuckets + 1];

		final OfflineIterable<BitVector, LongArrayBitVector> offlineData = new OfflineIterable<>(BitVectors.OFFLINE_SERIALIZER, LongArrayBitVector.getInstance());

		int duplicates = 0;

		for (;;) {
			pl.expectedUpdates = numBuckets;
			pl.itemsName = "buckets";
			pl.start("Analysing buckets... ");
			final AtomicLong unsolvable = new AtomicLong();

			try {
				final int numberOfThreads = Integer.parseInt(System.getProperty(NUMBER_OF_THREADS_PROPERTY, Integer.toString(Math.min(4, Runtime.getRuntime().availableProcessors()))));
				final ArrayBlockingQueue<Pair<Bucket, Integer>> bucketQueue = new ArrayBlockingQueue<>(numberOfThreads);
				final ReorderingBlockingQueue<LongArrayBitVector> queue = new ReorderingBlockingQueue<>(numberOfThreads * 128);
				final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads + 2);
				final ExecutorCompletionService<Void> executorCompletionService = new ExecutorCompletionService<>(executorService);

				executorCompletionService.submit(() -> {
					for (;;) {
						final LongArrayBitVector data = queue.take();
						if (data == END_OF_SOLUTION_QUEUE) return null;
						offlineData.add(data);
					}
				});

				final BucketedHashStore<T> chs = bucketedHashStore;
				executorCompletionService.submit(() -> {
					try {
						final Iterator<Bucket> iterator = chs.iterator();
						for (int i1 = 0; iterator.hasNext(); i1++) {
							final Bucket bucket = new Bucket(iterator.next());
							assert i1 == bucket.index();
							final LongBigList valueList = bucket.valueList(indirect ? values : null);
							long sumOfLengths = 0;
							for (int i = 0; i < bucket.size(); i++) sumOfLengths += coder.codewordLength(valueList.getLong(i));
							final long numVariables = Math.max(3, (sumOfLengths * deltaTimes256 >>> 8) + globalMaxCodewordLength);
							// We add the length of the longest keyword to avoid wrapping up indices
							assert numVariables <= Integer.MAX_VALUE;
							synchronized (offsetAndSeed) {
								offsetAndSeed[i1 + 1] = offsetAndSeed[i1] + numVariables;
								assert offsetAndSeed[i1 + 1] <= OFFSET_MASK + 1;
							}
							bucketQueue.put(new Pair<>(bucket, Integer.valueOf((int)sumOfLengths)));
						}
					} finally {
						for (int i2 = numberOfThreads; i2-- != 0;) bucketQueue.put(END_OF_BUCKET_QUEUE);
					}
					return null;
				});

				final AtomicInteger activeThreads = new AtomicInteger(numberOfThreads);
				for (int i = numberOfThreads; i-- != 0;) executorCompletionService.submit(() -> {
					Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
					long bucketTime = 0;
					long outputTime = 0;
					for (;;) {
						long start = System.nanoTime();
						final Pair<Bucket, Integer> bucketLength = bucketQueue.take();
						bucketTime += System.nanoTime() - start;
						if (bucketLength == END_OF_BUCKET_QUEUE) {
							if (activeThreads.decrementAndGet() == 0) queue.put(END_OF_SOLUTION_QUEUE, numBuckets);
							LOGGER.debug("Queue waiting time: " + Util.format(bucketTime / 1E9) + "s");
							LOGGER.debug("Output waiting time: " + Util.format(outputTime / 1E9) + "s");
							return null;
						}
						final Bucket bucket = bucketLength.getFirst();
						final int numEquations = bucketLength.getSecond().intValue();
						final int numVariables = (int)(offsetAndSeed[(int)(bucket.index() + 1)] - offsetAndSeed[(int)bucket.index()] & OFFSET_MASK);
						long seed = 0;
						final Linear3SystemSolver solver = new Linear3SystemSolver(numVariables, numEquations);

						for (;;) {
							final boolean solved = solver.generateAndSolve(bucket, seed, bucket.valueList(indirect ? values : null), coder, numVariables - globalMaxCodewordLength, globalMaxCodewordLength, peeled);
							unsolvable.addAndGet(solver.unsolvable);
							if (solved) break;
							seed += SEED_STEP;
							if (seed == 0) throw new AssertionError("Exhausted local seeds");
						}

						synchronized (offsetAndSeed) {
							offsetAndSeed[(int)bucket.index()] |= seed;
						}

						final LongArrayBitVector data = LongArrayBitVector.getInstance();
						final long[] solution = solver.solution;
						data.length(solution.length);
						for (int j = 0; j < solution.length; j++) data.set(j, (int)solution[j]);

						start = System.nanoTime();
						queue.put(data, bucket.index());
						outputTime += System.nanoTime() - start;
						synchronized (pl) {
							pl.update();
						}
					}
				});

				try {
					for (int i = numberOfThreads + 2; i-- != 0;) executorCompletionService.take().get();
				} catch (final InterruptedException e) {
					throw new RuntimeException(e);
				} catch (final ExecutionException e) {
					final Throwable cause = e.getCause();
					if (cause instanceof DuplicateException) throw (DuplicateException)cause;
					if (cause instanceof IOException) throw (IOException)cause;
					throw new RuntimeException(cause);
				} finally {
					executorService.shutdown();
				}

				LOGGER.info("Unsolvable systems: " + unsolvable.get() + "/" + (unsolvable.get() + numBuckets) + " (" + Util.format(100.0 * unsolvable.get() / (unsolvable.get() + numBuckets)) + "%)");
				// LOGGER.info("Mean node peeled for solved systems: " + Util.format((double)
				// peeledSumSolved /
				// totalNodesSolvable * 100) + "%");

				// if (unsolvable == 0) {
				// LOGGER.info("Mean node peeled for unsolved systems: " + 0 + "%");
				// } else {
				// LOGGER.info("Mean node peeled for unsolved systems: " + Util.format((double)
				// peeledSumUnsolvable /
				// totalNodesUnsolvable * 100) + "%");
				//
				// }
				pl.done();
				break;
			} catch (final BucketedHashStore.DuplicateException e) {
				if (keys == null) throw new IllegalStateException("You provided no keys, but the bucketed hash store was not checked");
				if (duplicates++ > 3) throw new IllegalArgumentException("The input list contains duplicates");
				LOGGER.warn("Found duplicate. Recomputing signatures...");
				bucketedHashStore.reset(r.nextLong());
				pl.itemsName = "keys";
				if (values == null || indirect) bucketedHashStore.addAll(keys.iterator());
				else bucketedHashStore.addAll(keys.iterator(), values.iterator());
				offlineData.clear();
				Arrays.fill(offsetAndSeed, 0);
			}
		}

		if (DEBUG) {
			System.out.println("MaxCodeword: " + globalMaxCodewordLength);
			System.out.println("Offsets: " + Arrays.toString(offsetAndSeed));
		}
		globalSeed = bucketedHashStore.seed();
		final OfflineIterator<BitVector, LongArrayBitVector> iterator = offlineData.iterator();

		if ((offsetAndSeed[numBuckets] & OFFSET_MASK) + 1 < bits(it.unimi.dsi.fastutil.Arrays.MAX_ARRAY_SIZE)) {
			final LongArrayBitVector dataBitVector = LongArrayBitVector.getInstance((offsetAndSeed[numBuckets] & OFFSET_MASK) + 1);
			this.data = dataBitVector;
			while (iterator.hasNext()) dataBitVector.append(iterator.next());
		} else {
			final LongBigArrayBitVector dataBitVector = LongBigArrayBitVector.getInstance((offsetAndSeed[numBuckets] & OFFSET_MASK) + 1);
			this.data = dataBitVector;
			while (iterator.hasNext()) dataBitVector.append(iterator.next());
		}

		iterator.close();
		offlineData.close();
		data.add(0);

		LOGGER.info("Completed.");

		LOGGER.info("Actual bit cost per element: " + (double)numBits() / n);
		if (!givenBucketedHashStore) bucketedHashStore.close();
	}

	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object o) {
		final int[] e = new int[3];
		final long[] signature = new long[2];
		Hashes.spooky4(transform.toBitVector((T)o), globalSeed, signature);
		final int bucket = (int)Math.multiplyHigh(signature[0] >>> 1, multiplier);
		final long olc = offsetAndSeed[bucket];
		final long bucketOffset = olc & OFFSET_MASK;
		final long nextBucketOffset = offsetAndSeed[bucket + 1] & OFFSET_MASK;
		final long bucketSeed = olc & SEED_MASK;
		final int w = globalMaxCodewordLength;
		final int numVariables = (int)(nextBucketOffset - bucketOffset - w);
		Linear3SystemSolver.signatureToEquation(signature, bucketSeed, numVariables, e);
		final long e0 = e[0] + bucketOffset, e1 = e[1] + bucketOffset, e2 = e[2] + bucketOffset;
		final long t = decoder.decode(data.getLong(e0, e0 + w) ^ data.getLong(e1, e1 + w) ^ data.getLong(e2, e2 + w));
		if (t != -1) return t;
		final int end = w - escapeLength;
		final int start = end - escapedSymbolLength;
		return data.getLong(e0 + start, e0 + end) ^ data.getLong(e1 + start, e1 + end) ^ data.getLong(e2 + start, e2 + end);
	}

	/**
	 * Returns the number of keys in the function domain.
	 *
	 * @return the number of the keys in the function domain.
	 */
	@Override
	public long size64() {
		return n;
	}

	@Override
	@Deprecated
	public int size() {
		return (int)Math.min(n, Integer.MAX_VALUE);
	}

	/**
	 * Returns the number of bits used by this structure.
	 *
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		if (n == 0) return 0;
		return data.size64() + bits(offsetAndSeed.length) + decoder.numBits();
	}

	@Override
	public boolean containsKey(final Object o) {
		return true;
	}

	public void dump(final String file) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024).order(ByteOrder.nativeOrder());
		final FileOutputStream fos = new FileOutputStream(file);
		final FileChannel channel = fos.getChannel();

		buffer.clear();
		buffer.putLong(size64());
		buffer.putLong(multiplier);
		buffer.putLong(globalMaxCodewordLength);
		buffer.putLong(globalSeed);
		buffer.putLong(offsetAndSeed.length);

		for (final long l : offsetAndSeed) {
			if (!buffer.hasRemaining()) {
				buffer.flip();
				channel.write(buffer);
				buffer.clear();
			}
			buffer.putLong(l);
		}

		buffer.flip();
		channel.write(buffer);
		buffer.clear();

		final LongBigList list = data.asLongBigList(Long.SIZE);
		buffer.putLong(words(data.length()));

		for (final long l : list) {
			buffer.putLong(l);
			if (!buffer.hasRemaining()) {
				buffer.flip();
				channel.write(buffer);
				buffer.clear();
			}
		}

		if (!LongArrayBitVector.round(data.length())) buffer.putLong(data.getLong(data.length() & -Long.SIZE, data.length()));

		buffer.flip();
		channel.write(buffer);
		buffer.clear();

		((Codec.Huffman.Coder.Decoder)decoder).dump(buffer);
		buffer.flip();
		channel.write(buffer);

		fos.close();
	}

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(GV3CompressedFunction.class.getName(), "Builds a GOV function mapping a newline-separated list" + " of strings to their ordinal position, or to specific values.", new Parameter[] {
				new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
				new FlaggedOption("tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files."),
				new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
				new Switch("peel", 'p', "peel", "Use peeling instead of lazy Gaussian elimination (+12% space, much faster construction)."),
				new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
				new Switch("byteArray", 'b', "byte-array", "Create a function on byte arrays (no character encoding)."),
				new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
				new FlaggedOption("decompressor", JSAP.CLASS_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'd', "decompressor", "Use this extension of InputStream to decompress the strings (e.g., java.util.zip.GZIPInputStream)."),
				new FlaggedOption("codec", JSAP.STRING_PARSER, "HUFFMAN", JSAP.NOT_REQUIRED, 'C', "codec", "The name of the codec to use (UNARY, BINARY, GAMMA, HUFFMAN, LLHUFFMAN)."),
				new FlaggedOption("limit", JSAP.INTEGER_PARSER, "20", JSAP.NOT_REQUIRED, 'l', "limit", "Decoding-table length limit for the LLHUFFMAN codec."),
				new FlaggedOption("values", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'v', "values", "A binary file in DataInput format containing a long for each string (otherwise, the values will be the ordinal positions of the strings)."),
				new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised GOV function."),
				new UnflaggedOption("stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the second case, strings must be fewer than 2^31 and will be loaded into core memory."), });

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String stringFile = jsapResult.getString("stringFile");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final File tempDir = jsapResult.getFile("tempDir");
		final boolean byteArray = jsapResult.getBoolean("byteArray");
		final boolean zipped = jsapResult.getBoolean("zipped");
		Class<? extends InputStream> decompressor = jsapResult.getClass("decompressor");
		final boolean peeled = jsapResult.getBoolean("peel");
		final boolean iso = jsapResult.getBoolean("iso");
		final boolean utf32 = jsapResult.getBoolean("utf32");
		final int limit = jsapResult.getInt("limit");

		if (zipped && decompressor != null) throw new IllegalArgumentException("The zipped and decompressor options are incompatible");
		if (zipped) decompressor = GZIPInputStream.class;

		Codec codec = null;
		switch (jsapResult.getString("codec")) {
		case "UNARY":
			codec = new Codec.Unary();
			break;
		case "BINARY":
			codec = new Codec.Binary();
			break;
		case "GAMMA":
			codec = new Codec.Gamma();
			break;
		case "HUFFMAN":
			codec = new Codec.Huffman();
			break;
		case "LLHUFFMAN":
			codec = new Codec.Huffman(limit);
			break;
		default:
			throw new IllegalArgumentException("Unknown codec \"" + jsapResult.getString("codec") + "\"");
		}

		final LongIterable values = jsapResult.userSpecified("values") ? BinIO.asLongIterable(jsapResult.getString("values")) : null;

		if (byteArray) {
			if ("-".equals(stringFile)) throw new IllegalArgumentException("Cannot read from standard input when building byte-array functions");
			if (iso || utf32 || jsapResult.userSpecified("encoding")) throw new IllegalArgumentException("Encoding options are not available when building byte-array functions");
			final Iterable<byte[]> keys = new FileLinesByteArrayIterable(stringFile, decompressor);
			BinIO.storeObject(new GV3CompressedFunction<>(keys, TransformationStrategies.rawByteArray(), values, false, tempDir, null, codec, peeled), functionName);
		} else {
			final Iterable<? extends CharSequence> keys;
			if ("-".equals(stringFile)) {
				final ObjectArrayList<String> list = new ObjectArrayList<>();
				keys = list;
				FileLinesMutableStringIterable.iterator(System.in, encoding, decompressor).forEachRemaining(s -> list.add(s.toString()));
			} else keys = new FileLinesMutableStringIterable(stringFile, encoding, decompressor);
			final TransformationStrategy<CharSequence> transformationStrategy = iso ? TransformationStrategies.rawIso() : utf32 ? TransformationStrategies.rawUtf32() : TransformationStrategies.rawUtf16();

			BinIO.storeObject(new GV3CompressedFunction<>(keys, transformationStrategy, values, false, tempDir, null, codec, peeled), functionName);
		}
		LOGGER.info("Completed.");
	}
}
