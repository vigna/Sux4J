/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2020 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.mph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

import it.unimi.dsi.Util;
import it.unimi.dsi.big.io.FileLinesByteArrayCollection;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.BucketedHashStore;
import it.unimi.dsi.sux4j.io.BucketedHashStore.Bucket;
import it.unimi.dsi.sux4j.io.BucketedHashStore.DuplicateException;
import it.unimi.dsi.sux4j.mph.solve.Linear3SystemSolver;
import it.unimi.dsi.sux4j.mph.solve.Orient3Hypergraph;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;
import it.unimi.dsi.util.concurrent.ReorderingBlockingQueue;

/**
 * A minimal perfect hash function stored using the {@linkplain Linear3SystemSolver
 * Genuzio-Ottaviano-Vigna 3-regular <b>F</b><sub>3</sub>-linear system technique}. It is the
 * fastest minimal perfect hash function available with space close to 2 bits per key.
 *
 * <P>
 * Given a list of keys without duplicates, the {@linkplain Builder builder} of this class finds a
 * minimal perfect hash function for the list. Subsequent calls to the {@link #getLong(Object)}
 * method will return a distinct number for each key in the list. For keys out of the list, the
 * resulting number is not specified. In some (rare) cases it might be possible to establish that a
 * key was not in the original list, and in that case -1 will be returned; by <em>signing</em> the
 * function (see below), you can guarantee with a prescribed probability that -1 will be returned on
 * keys not in the original list. The class can then be saved by serialisation and reused later.
 *
 * <p>
 * This class uses a {@linkplain BucketedHashStore bucketed hash store} to provide highly scalable
 * construction. Note that at construction time you can {@linkplain Builder#store(BucketedHashStore)
 * pass a BucketedHashStore} containing the keys (associated with any value); however, if the store
 * is rebuilt because of a {@link it.unimi.dsi.sux4j.io.BucketedHashStore.DuplicateException} it
 * will be rebuilt associating with each key its ordinal position.
 *
 * <P>
 * For convenience, this class provides a main method that reads from standard input a (possibly
 * <code>gzip</code>'d) sequence of newline-separated strings, and writes a serialised minimal
 * perfect hash function for the given list.
 *
 * <h2>Signing</h2>
 *
 * <p>
 * Optionally, it is possible to {@linkplain Builder#signed(int) <em>sign</em>} the minimal perfect
 * hash function. A <var>w</var>-bit signature will be associated with each key, so that
 * {@link #getLong(Object)} will return -1 on strings that are not in the original key set. As
 * usual, false positives are possible with probability 2<sup>-<var>w</var></sup>.
 *
 * <h2>Multithreading</h2>
 *
 * <p>
 * This implementation is multithreaded: each bucket returned by the {@link BucketedHashStore} is
 * processed independently. By default, this class uses {@link Runtime#availableProcessors()}
 * parallel threads, but by default no more than 4. If you wish to set a specific number of threads,
 * you can do so through the system property {@value #NUMBER_OF_THREADS_PROPERTY}.
 *
 * <h2>How it Works</h2>
 *
 * <p>
 * The detail of the data structure can be found in &ldquo;Fast Scalable Construction of (Minimal
 * Perfect Hash) Functions&rdquo;, by Marco Genuzio, Giuseppe Ottaviano and Sebastiano Vigna,
 * <i>15th International Symposium on Experimental Algorithms &mdash; SEA 2016</i>, Lecture Notes in
 * Computer Science, Springer, 2016. We generate a random 3-regular hypergraph and give it an
 * {@linkplain Orient3Hypergraph orientation}. From the orientation, we generate a random linear
 * system on <b>F</b><sub>3</sub>, where the variables in the <var>k</var>-th equation are the
 * vertices of the <var>k</var>-th hyperedge, and the known term of the <var>k</var>-th equation is
 * the vertex giving orientation to the <var>k</var>-th hyperedge. Then, we
 * {@linkplain Linear3SystemSolver solve the system} and store the solution, which provides a
 * perfect hash function.
 *
 * <p>
 * To obtain a minimal perfect hash function, we simply notice that we whenever we have to assign a
 * value to a vertex, we can take care of using the number 3 instead of 0 if the vertex is actually
 * the output value for some key. The final value of the minimal perfect hash function is the number
 * of nonzero pairs of bits that precede the perfect hash value for the key. To compute this number,
 * we use use in each bucket {@linkplain #countNonzeroPairs(long) broadword programming}.
 *
 * Since the system must have &#8776;10% more variables than equations to be solvable, a
 * {@link GOVMinimalPerfectHashFunction} on <var>n</var> keys requires 2.2<var>n</var> bits.
 *
 * @author Sebastiano Vigna
 * @since 4.0.0
 */

public class GOVMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
	public static final long serialVersionUID = 6L;
	private static final Logger LOGGER = LoggerFactory.getLogger(GOVMinimalPerfectHashFunction.class);
	private static final LongArrayBitVector END_OF_SOLUTION_QUEUE = LongArrayBitVector.getInstance();
	private static final Bucket END_OF_BUCKET_QUEUE = new Bucket();

	/** The local seed is generated using this step, so to be easily embeddable in {@link #edgeOffsetAndSeed}. */
	private static final long SEED_STEP = 1L << 56;
	/** The lowest 56 bits of {@link #edgeOffsetAndSeed} contain the number of keys stored up to the given bucket. */
	private static final long OFFSET_MASK = -1L >>> 8;

	/** The ratio between vertices and hyperedges. */
	private static double C = 1.09 + 0.01;
	/** Fixed-point representation of {@link #C}. */
	private static int C_TIMES_256 = (int)Math.floor(C * 256);

	/**
	 * Counts the number of nonzero pairs of bits in a long.
	 *
	 * @param x a long.
	 * @return the number of nonzero bit pairs in <code>x</code>.
	 */

	public final static int countNonzeroPairs(final long x) {
		return Long.bitCount((x | x >>> 1) & 0x5555555555555555L);
	}

	/** Counts the number of nonzero pairs between two positions in the given arrays,
	 * which represents a sequence of two-bit values.
	 *
	 * @param start start position (inclusive).
	 * @param end end position (exclusive).
	 * @param array an array of longs containing 2-bit values.
	 * @return the number of nonzero 2-bit values between {@code start} and {@code end}.
	 */
	private final static long countNonzeroPairs(final long start, final long end, final long[] array) {
		int block = (int)(start >>> 5);
		final int endBlock = (int)(end >>> 5);
		final int startOffset = (int)(start & 31);
		final int endOffset = (int)(end & 31);

		if (block == endBlock) return countNonzeroPairs((array[block] & (1L << (endOffset << 1)) - 1) >>> (startOffset << 1));

		long pairs = 0;
		if (startOffset != 0) pairs += countNonzeroPairs(array[block++] >>> (startOffset << 1));
		while(block < endBlock) pairs += countNonzeroPairs(array[block++]);
		if (endOffset != 0) pairs += countNonzeroPairs(array[block] & (1L << (endOffset << 1)) - 1);

		return pairs;
	}

	/** The system property used to set the number of parallel threads. */
	public static final String NUMBER_OF_THREADS_PROPERTY = "it.unimi.dsi.sux4j.mph.threads";

	/** A builder class for {@link GOVMinimalPerfectHashFunction}. */
	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected int signatureWidth;
		protected File tempDir;
		protected BucketedHashStore<T> bucketedHashStore;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;

		/** Specifies the keys to hash; if you have specified a {@link #store(BucketedHashStore) BucketedHashStore}, it can be {@code null}.
		 *
		 * @param keys the keys to hash.
		 * @return this builder.
		 */
		public Builder<T> keys(final Iterable<? extends T> keys) {
			this.keys = keys;
			return this;
		}

		/** Specifies the transformation strategy for the {@linkplain #keys(Iterable) keys to hash}.
		 *
		 * @param transform a transformation strategy for the {@linkplain #keys(Iterable) keys to hash}.
		 * @return this builder.
		 */
		public Builder<T> transform(final TransformationStrategy<? super T> transform) {
			this.transform = transform;
			return this;
		}

		/** Specifies that the resulting {@link GOVMinimalPerfectHashFunction} should be signed using a given number of bits per key.
		 *
		 * @param signatureWidth a signature width, or 0 for no signature.
		 * @return this builder.
		 */
		public Builder<T> signed(final int signatureWidth) {
			this.signatureWidth = signatureWidth;
			return this;
		}

		/** Specifies a temporary directory for the {@link #store(BucketedHashStore) BucketedHashStore}.
		 *
		 * @param tempDir a temporary directory for the {@link #store(BucketedHashStore) BucketedHashStore} files, or {@code null} for the standard temporary directory.
		 * @return this builder.
		 */
		public Builder<T> tempDir(final File tempDir) {
			this.tempDir = tempDir;
			return this;
		}

		/** Specifies a bucketed hash store containing the keys.
		 *
		 * @param bucketedHashStore a bucketed hash store containing the keys, or {@code null}; the store
		 * can be unchecked, but in this case you must specify {@linkplain #keys(Iterable) keys} and a {@linkplain #transform(TransformationStrategy) transform}
		 * (otherwise, in case of a hash collision in the store an {@link IllegalStateException} will be thrown).
		 * @return this builder.
		 */
		public Builder<T> store(final BucketedHashStore<T> bucketedHashStore) {
			this.bucketedHashStore = bucketedHashStore;
			return this;
		}

		/** Builds a minimal perfect hash function.
		 *
		 * @return a {@link GOVMinimalPerfectHashFunction} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public GOVMinimalPerfectHashFunction<T> build() throws IOException {
			if (built) throw new IllegalStateException("This builder has been already used");
			built = true;
			if (transform == null) {
				if (bucketedHashStore != null) transform = bucketedHashStore.transform();
				else throw new IllegalArgumentException("You must specify a TransformationStrategy, either explicitly or via a given BucketedHashStore");
			}
			return new GOVMinimalPerfectHashFunction<>(keys, transform, signatureWidth, tempDir, bucketedHashStore);
		}
	}

	/** The expected bucket size. */
	public final static int BUCKET_SIZE = 1500;

	/** The multiplier for buckets. */
	private final long multiplier;

	/** The number of keys. */
	protected final long n;

	/** The seed used to generate the initial signature. */
	protected final long globalSeed;

	/** A long containing the cumulating function of the bucket edges (i.e., keys) in the lower 56 bits,
	 * and the local seed of each bucket in the upper 8 bits. The method {@link #vertexOffset(long)}
	 * returns the bucket (i.e., vertex) cumulative value starting from the edge cumulative value. */
	protected final long[] edgeOffsetAndSeed;

	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal perfect hash function. */
	protected final LongBigList values;

	/** The bit vector underlying {@link #values}. */
	protected final LongArrayBitVector bitVector;

	/** The bit array supporting {@link #bitVector}. */
	protected transient long[] array;

	/** The transformation strategy. */
	protected final TransformationStrategy<? super T> transform;

	/** The mask to compare signatures, or zero for no signatures. */
	protected final long signatureMask;

	/** The signatures. */
	protected final LongBigList signatures;

	protected static long vertexOffset(final long edgeOffsetSeed) {
		return ((edgeOffsetSeed & OFFSET_MASK) * C_TIMES_256 >> 8);
	}

	/**
	 * Creates a new minimal perfect hash function for the given keys.
	 *
	 * @param keys the keys to hash, or {@code null}.
	 * @param transform a transformation strategy for the keys.
	 * @param signatureWidth a signature width, or 0 for no signature.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 * @param bucketedHashStore a bucketed hash store containing the keys, or {@code null}; the store
	 * can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be non-{@code null}.
	 */
	protected GOVMinimalPerfectHashFunction(final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final int signatureWidth, final File tempDir, BucketedHashStore<T> bucketedHashStore) throws IOException {
		this.transform = transform;

		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		final RandomGenerator r = new XoRoShiRo128PlusRandomGenerator();
		pl.itemsName = "keys";

		final boolean givenBucketedHashStore = bucketedHashStore != null;
		if (bucketedHashStore == null) {
			bucketedHashStore = new BucketedHashStore<>(transform, tempDir, pl);
			bucketedHashStore.reset(r.nextLong());
			bucketedHashStore.addAll(keys.iterator());
		}
		n = bucketedHashStore.size();

		defRetValue = -1; // For the very few cases in which we can decide

		bucketedHashStore.bucketSize(BUCKET_SIZE);
		if (n / BUCKET_SIZE + 1 > Integer.MAX_VALUE) throw new IllegalStateException("This class supports at most " + ((Integer.MAX_VALUE - 1) * BUCKET_SIZE - 1) + " keys");
		final int numBuckets = (int) (n / BUCKET_SIZE + 1);
		multiplier = numBuckets * 2L;

		LOGGER.debug("Number of buckets: " + numBuckets);

		edgeOffsetAndSeed = new long[numBuckets + 1];

		bitVector = LongArrayBitVector.getInstance(2 * (1 + (n * C_TIMES_256 >> 8)));

		int duplicates = 0;

		for (;;) {
			LOGGER.debug("Generating minimal perfect hash function...");

			pl.expectedUpdates = numBuckets;
			pl.itemsName = "buckets";
			pl.start("Analysing buckets... ");
			final AtomicLong unsolvable = new AtomicLong(), unorientable = new AtomicLong();

			try {
				final int numberOfThreads = Integer.parseInt(System.getProperty(NUMBER_OF_THREADS_PROPERTY, Integer.toString(Math.min(4, Runtime.getRuntime().availableProcessors()))));
				final ArrayBlockingQueue<Bucket> bucketQueue = new ArrayBlockingQueue<>(numberOfThreads * 8);
				final ReorderingBlockingQueue<LongArrayBitVector> queue = new ReorderingBlockingQueue<>(numberOfThreads * 128);
				final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads + 2);
				final ExecutorCompletionService<Void> executorCompletionService = new ExecutorCompletionService<>(executorService);

				executorCompletionService.submit(() -> {
					for(;;) {
						final LongArrayBitVector data = queue.take();
						if (data == END_OF_SOLUTION_QUEUE) return null;
						bitVector.append(data);
					}
				});

				final BucketedHashStore<T> chs = bucketedHashStore;
				executorCompletionService.submit(() -> {
					try {
						final Iterator<Bucket> iterator = chs.iterator();
						for(int i1 = 0; iterator.hasNext(); i1++) {
							final Bucket bucket = new Bucket(iterator.next());
							assert i1 == bucket.index();
							synchronized(edgeOffsetAndSeed) {
								edgeOffsetAndSeed[i1 + 1] = edgeOffsetAndSeed[i1] + bucket.size();
								assert edgeOffsetAndSeed[i1 + 1] <= OFFSET_MASK + 1;
							}
							bucketQueue.put(bucket);
						}
					}
					finally {
						for(int i2 = numberOfThreads; i2-- != 0;) bucketQueue.put(END_OF_BUCKET_QUEUE);
					}
					return null;
				});

				final AtomicInteger activeThreads = new AtomicInteger(numberOfThreads);
				for(int i = numberOfThreads; i-- != 0;) executorCompletionService.submit(() -> {
					Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
					long bucketTime = 0;
					final long outputTime = 0;
					for(;;) {
						final long start = System.nanoTime();
						final Bucket bucket = bucketQueue.take();
						bucketTime += System.nanoTime() - start;
						if (bucket == END_OF_BUCKET_QUEUE) {
							if (activeThreads.decrementAndGet() == 0) queue.put(END_OF_SOLUTION_QUEUE, numBuckets);
							LOGGER.debug("Queue waiting time: " + Util.format(bucketTime / 1E9) + "s");
							LOGGER.debug("Output waiting time: " + Util.format(outputTime / 1E9) + "s");
							return null;
						}
						long seed = 0;

						final long off = vertexOffset(edgeOffsetAndSeed[(int)bucket.index()]);
						final Linear3SystemSolver solver =
								new Linear3SystemSolver((int)(vertexOffset(edgeOffsetAndSeed[(int)(bucket.index() + 1)]) - off), bucket.size());

						for(;;) {
							final boolean solved = solver.generateAndSolve(bucket, seed, null);
							unorientable.addAndGet(solver.unorientable);
							unsolvable.addAndGet(solver.unsolvable);
							if (solved) break;
							seed += SEED_STEP;
							if (seed == 0) throw new AssertionError("Exhausted local seeds");
						}

						synchronized (edgeOffsetAndSeed) {
							edgeOffsetAndSeed[(int)bucket.index()] |= seed;
						}

						final long[] solution = solver.solution;
						final LongArrayBitVector dataBitVector = LongArrayBitVector.ofLength(solution.length * 2);
						final LongBigList dataList = dataBitVector.asLongBigList(2);
						for(int j = 0; j < solution.length; j++) dataList.set(j, solution[j]);
						queue.put(dataBitVector, bucket.index());

						synchronized(pl) {
							pl.update();
						}
					}
				});

				try {
					for(int i = numberOfThreads + 2; i-- != 0;)
						executorCompletionService.take().get();
				} catch (final InterruptedException e) {
					throw new RuntimeException(e);
				} catch (final ExecutionException e) {
					final Throwable cause = e.getCause();
					if (cause instanceof DuplicateException) throw (DuplicateException)cause;
					if (cause instanceof IOException) throw (IOException)cause;
					throw new RuntimeException(cause);
				}
				finally {
					executorService.shutdown();
				}
				final long orientable = unsolvable.get() + numBuckets;
				LOGGER.info("Unsolvable systems: " + unsolvable.get() + "/" + orientable + " (" + Util.format(100.0 * unsolvable.get() / orientable) + "%)");
				LOGGER.info("Unorientable systems: " + unorientable.get() + "/" + (orientable + unorientable.get()) + " (" + Util.format(100.0 * unorientable.get() / (orientable + unorientable.get())) + "%)");

				pl.done();
				break;
			}
			catch(final DuplicateException e) {
				if (keys == null) throw new IllegalStateException("You provided no keys, but the bucketed hash store was not checked");
				if (duplicates++ > 3) throw new IllegalArgumentException("The input list contains duplicates");
				LOGGER.warn("Found duplicate. Recomputing signatures...");
				bucketedHashStore.reset(r.nextLong());
				pl.itemsName = "keys";
				bucketedHashStore.addAll(keys.iterator());
				Arrays.fill(edgeOffsetAndSeed, 0);
			}
		}

		globalSeed = bucketedHashStore.seed();
		values = bitVector.asLongBigList(2);
		values.add(0);
		array = bitVector.bits();

		LOGGER.info("Completed.");
		LOGGER.debug("Forecast bit cost per key: " + 2 * C + 64. / BUCKET_SIZE);
		LOGGER.info("Actual bit cost per key: " + (double)numBits() / n);


		if (signatureWidth != 0) {
			signatureMask = -1L >>> -signatureWidth;
			(signatures = LongArrayBitVector.getInstance().asLongBigList(signatureWidth)).size(n);
			pl.expectedUpdates = n;
			pl.itemsName = "signatures";
			pl.start("Signing...");
			for (final BucketedHashStore.Bucket bucket : bucketedHashStore) {
				final Iterator<long[]> iterator = bucket.iterator();
				for(int i = bucket.size(); i-- != 0;) {
					final long[] signature = iterator.next();
					final int[] e = new int[3];
					signatures.set(getLongBySignatureNoCheck(signature, e), signatureMask & signature[0]);
					pl.lightUpdate();
				}
			}
			pl.done();
		}
		else {
			signatureMask = 0;
			signatures = null;
		}

		if (!givenBucketedHashStore) bucketedHashStore.close();
	}

	/**
	 * Returns the number of bits used by this structure.
	 *
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return values.size64() * 2 + edgeOffsetAndSeed.length * (long)Long.SIZE;
	}

	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object key) {
		final long[] signature = new long[2];
		Hashes.spooky4(transform.toBitVector((T)key), globalSeed, signature);
		return getLongBySignature(signature);
	}

	/** Low-level access to the output of this minimal perfect hash function.
	 *
	 * <p>This method makes it possible to build several kind of functions on the same {@link BucketedHashStore} and
	 * then retrieve the resulting values by generating a single signature. The method
	 * {@link TwoStepsGOV3Function#getLong(Object)} is a good example of this technique.
	 *
	 * @param signature a signature generated as documented in {@link BucketedHashStore}.
	 * @return the output of the function.
	 */
	public long getLongBySignature(final long[] signature) {
		final int[] e = new int[3];
		final int bucket = (int)Math.multiplyHigh(signature[0] >>> 1, multiplier);
		final long edgeOffsetSeed = edgeOffsetAndSeed[bucket];
		final long bucketOffset = vertexOffset(edgeOffsetSeed);
		final int numVariables = (int)(vertexOffset(edgeOffsetAndSeed[bucket + 1]) - bucketOffset);
		//if (numVariables == 0) return defRetValue;
		Linear3SystemSolver.signatureToEquation(signature, edgeOffsetSeed & ~OFFSET_MASK, numVariables, e);

		final long result = (edgeOffsetSeed & OFFSET_MASK) + countNonzeroPairs(bucketOffset, bucketOffset + e[(int)(values.getLong(e[0] + bucketOffset) + values.getLong(e[1] + bucketOffset) + values.getLong(e[2] + bucketOffset)) % 3], array);
		if (signatureMask != 0) return result >= n || signatures.getLong(result) != (signature[0] & signatureMask) ? defRetValue : result;
		return result < n ? result : defRetValue;
	}

	/** A dirty function replicating the behaviour of {@link #getLongBySignature(long[])} but skipping the
	 * signature test. Used in the constructor. <strong>Must</strong> be kept in sync with {@link #getLongByTriple(long[])}. */
	private long getLongBySignatureNoCheck(final long[] signature, final int[] e) {
		final int bucket = (int)Math.multiplyHigh(signature[0] >>> 1, multiplier);
		final long edgeOffsetSeed = edgeOffsetAndSeed[bucket];
		final long bucketOffset = vertexOffset(edgeOffsetSeed);
		Linear3SystemSolver.signatureToEquation(signature, edgeOffsetSeed & ~OFFSET_MASK, (int)(vertexOffset(edgeOffsetAndSeed[bucket + 1]) - bucketOffset), e);
		return (edgeOffsetSeed & OFFSET_MASK) + countNonzeroPairs(bucketOffset, bucketOffset + e[(int)(values.getLong(e[0] + bucketOffset) + values.getLong(e[1] + bucketOffset) + values.getLong(e[2] + bucketOffset)) % 3], array);
	}

	@Override
	public long size64() {
		return n;
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		array = bitVector.bits();
	}

	public void dump(final String file) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocateDirect(edgeOffsetAndSeed.length * 8 + 32).order(ByteOrder.nativeOrder());
		final FileOutputStream fos = new FileOutputStream(file);
		final FileChannel channel = fos.getChannel();

		buffer.clear();
		buffer.putLong(size64());
		buffer.putLong(multiplier);
		buffer.putLong(globalSeed);
		buffer.putLong(edgeOffsetAndSeed.length);
		for(final long l : edgeOffsetAndSeed) buffer.putLong(l);
		buffer.flip();
		channel.write(buffer);
		buffer.clear();

		buffer.putLong(array.length);
		for(final long l: array) {
			if (!buffer.hasRemaining()) {
				buffer.flip();
				channel.write(buffer);
				buffer.clear();
			}
			buffer.putLong(l);
		}
		buffer.flip();
		channel.write(buffer);
		fos.close();
	}

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(GOVMinimalPerfectHashFunction.class.getName(), "Builds a minimal perfect hash function reading a newline-separated list of strings.", new Parameter[] {
				new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
				new FlaggedOption("tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files."),
				new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
				new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
				new Switch("byteArray", 'b', "byte-array", "Create a function on byte arrays (no character encoding)."),
				new FlaggedOption("signatureWidth", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "signature-width", "If specified, the signature width in bits."),
				new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
				new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash function."),
				new UnflaggedOption("stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY,
						"The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory."), });

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String stringFile = jsapResult.getString("stringFile");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final File tempDir = jsapResult.getFile("tempDir");
		final boolean byteArray = jsapResult.getBoolean("byteArray");
		final boolean zipped = jsapResult.getBoolean("zipped");
		final boolean iso = jsapResult.getBoolean("iso");
		final boolean utf32 = jsapResult.getBoolean("utf32");
		final int signatureWidth = jsapResult.getInt("signatureWidth", 0);

		if (byteArray) {
			if ("-".equals(stringFile)) throw new IllegalArgumentException("Cannot read from standard input when building byte-array functions");
			if (iso || utf32 || jsapResult.userSpecified("encoding")) throw new IllegalArgumentException("Encoding options are not available when building byte-array functions");
			final Collection<byte[]> collection= new FileLinesByteArrayCollection(stringFile, zipped);
			BinIO.storeObject(new GOVMinimalPerfectHashFunction<>(collection, TransformationStrategies.rawByteArray(), signatureWidth, tempDir, null), functionName);
		}
		else {
			final Collection<MutableString> collection;
			if ("-".equals(stringFile)) {
				final ProgressLogger pl = new ProgressLogger(LOGGER);
				pl.displayLocalSpeed = true;
				pl.displayFreeMemory = true;
				pl.start("Loading strings...");
				collection = new LineIterator(new FastBufferedReader(new InputStreamReader(zipped ? new GZIPInputStream(System.in) : System.in, encoding)), pl).allLines();
				pl.done();
			}
			else collection = new FileLinesCollection(stringFile, encoding.toString(), zipped);
			final TransformationStrategy<CharSequence> transformationStrategy = iso
					? TransformationStrategies.rawIso()
							: utf32
							? TransformationStrategies.rawUtf32()
									: TransformationStrategies.rawUtf16();

							BinIO.storeObject(new GOVMinimalPerfectHashFunction<CharSequence>(collection, transformationStrategy, signatureWidth, tempDir, null), functionName);
		}
		LOGGER.info("Saved.");
	}
}
