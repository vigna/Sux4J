package it.unimi.dsi.sux4j.mph;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2017-2018 Sebastiano Vigna
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


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
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
import it.unimi.dsi.big.io.FileLinesByteArrayCollection;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongBigList;
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
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.sux4j.io.ChunkedHashStore.Chunk;
import it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException;
import it.unimi.dsi.sux4j.mph.codec.Codec;
import it.unimi.dsi.sux4j.mph.codec.Codec.Huffman;
import it.unimi.dsi.sux4j.mph.codec.Codec.ZeroCodec;
import it.unimi.dsi.sux4j.mph.solve.Linear4SystemSolver;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;
import it.unimi.dsi.util.concurrent.ReorderingBlockingQueue;


/** An immutable function stored in a compressed form.
 *
 * <p>Instances of this class store a function from keys to values. Keys are provided by an {@linkplain Iterable iterable object} (whose iterators
 * must return elements in a consistent order), whereas values are provided by a {@link LongIterable}. If you do not specify
 * values, each key will be assigned its rank (e.g., its position in iteration order starting from zero).
 *
 * <p>The values must have a skewed distribution: some values must be much more frequent than others. In that case, this
 * data structure uses much less space than, say, a {@link GOV4Function} because it is able to use, for each key, a number
 * of bits close to the empirical entropy of the value list (with an additional &#8776;3%). It is slower than a {@link GV3CompressedFunction},
 * and it takes more time to build, but it uses less space.
 *
 * <P>For convenience, this class provides a main method that reads from
 * standard input a (possibly <code>gzip</code>'d) sequence of newline-separated strings, and
 * writes a serialised function mapping each element of the list to its position, or to a given list of values.
 *
 * <h2>Building a function</h2>
 *
 * <p>This class provides a great amount of flexibility when creating a new function; such flexibility is exposed through the {@linkplain Builder builder}.
 * To exploit the various possibilities, you must understand some details of the construction.
 *
 * <p>In a first phase, we build a {@link ChunkedHashStore} containing hashes of the keys. By default,
 * the store will associate each hash with the rank of the key. If you {@linkplain Builder#values(LongIterable) specify values},
 * the store will associate with each hash the corresponding value.
 *
 * <p>However, if you further require an {@linkplain Builder#indirect() indirect}
 * construction the store will associate again each hash with the rank of the corresponding key, and access randomly the values
 * (which must be either a {@link LongList} or a {@link LongBigList}). Indirect construction is useful only in complex, multi-layer
 * hashes (such as an {@link LcpMonotoneMinimalPerfectHashFunction}) in which we want to reuse a checked {@link ChunkedHashStore}.
 * Storing values in the {@link ChunkedHashStore}
 * is extremely scalable because the values must just be a {@link LongIterable} that
 * will be scanned sequentially during the store construction. On the other hand, if you have already a store that
 * associates ordinal positions, and you want to build a new function for which a {@link LongList} or {@link LongBigList} of values needs little space (e.g.,
 * because it is described implicitly), you can opt for an {@linkplain Builder#indirect() indirect} construction using the already built store.
 *
 * <p>Note that if you specify a store it will be used before building a new one (possibly because of a {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException DuplicateException}),
 * with obvious benefits in terms of performance. If the store is not checked, and a {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException DuplicateException} is
 * thrown, the constructor will try to rebuild the store, but this requires, of course, that the keys, and possibly the values, are available.
 * Note that it is your responsibility to pass a correct store.
 *
 * <h2>Multithreading</h2>
 *
 * <p>This implementation is multithreaded: each chunk returned by the {@link ChunkedHashStore} is processed independently. By
 * default, this class uses {@link Runtime#availableProcessors()} parallel threads, but never more than 16. If you wish to
 * set a specific number of threads, you can do so through the system property {@value #NUMBER_OF_THREADS_PROPERTY}.
 *
 * <h2>Implementation Details</h2>
 *
 * <p>The detail of the data structure
 * can be found in &ldquo;Engineering Compressed Functions&rdquo;, by
 * Marco Genuzio and Sebastiano Vigna, 2017. The theoretical basis for the construction is described by
 * J&oacute;hannes B. Hreinsson, Morten Kr&oslash;yer, and Rasmus Pagh
 * in &ldquo;Storing a compressed function with constant time access&rdquo;, <i>
 * Algorithms - ESA 2009, 17th Annual European Symposium</i>, 2009, pages 730&minus;741.
 *
 * Each output value is represented by a codeword from a prefix-free code
 * (by default, a length-limited {@linkplain Huffman Huffman} code).
 * We generate a random 4-regular linear system on <b>F</b><sub>2</sub>, where
 * the known term of the <var>k</var>-th equation is a bit in a bit array. When we read the
 * bit array at four suitable positions depending on an input key, we can recover the codeword representing the output value.
 *
 * @see GV3CompressedFunction
 * @author Sebastiano Vigna
 * @author Marco Genuzio
 * @since 4.2.0
 */

public class GV4CompressedFunction<T> extends AbstractObject2LongFunction<T> implements Serializable, Size64 {
	private static final long serialVersionUID = 1L;
	private static final LongArrayBitVector END_OF_SOLUTION_QUEUE = LongArrayBitVector.getInstance();
	private static final Pair<Chunk, Integer> END_OF_CHUNK_QUEUE = new Pair<>(new Chunk(), Integer.valueOf(0));
	private static final Logger LOGGER = LoggerFactory.getLogger(GV4CompressedFunction.class);
	private static final boolean DEBUG = false;
	protected static final int SEED_BITS = 10;
	protected static final int OFFSET_BITS = Long.SIZE - SEED_BITS;
	/**
	 * The local seed is generated using this step, so to be easily embeddable
	 * in {@link #offsetAndSeed}.
	 */
	private static final long SEED_STEP = 1L << Long.SIZE - SEED_BITS;
	/**
	 * The lowest 54 bits of {@link #offsetAndSeed} contain the number of
	 * keys stored up to the given chunk.
	 */
	private static final long OFFSET_MASK = -1L >>> SEED_BITS;
	private static final long SEED_MASK = -1L << Long.SIZE - SEED_BITS;

	/** The system property used to set the number of parallel threads. */
	public static final String NUMBER_OF_THREADS_PROPERTY = "it.unimi.dsi.sux4j.mph.threads";

	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected File tempDir;
		protected ChunkedHashStore<T> chunkedHashStore;
		protected LongIterable values;
		protected boolean indirect;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;
		protected Codec codec;

		/**
		 * Specifies the keys of the function; if you have specified a
		 * {@link #store(ChunkedHashStore) ChunkedHashStore}, it can be
		 * {@code null}.
		 *
		 * @param keys
		 *            the keys of the function.
		 * @return this builder.
		 */
		public Builder<T> keys(final Iterable<? extends T> keys) {
			this.keys = keys;
			return this;
		}

		/**
		 * Specifies the transformation strategy for the
		 * {@linkplain #keys(Iterable) keys of the function}; the strategy can
		 * be {@linkplain TransformationStrategies raw}.
		 *
		 * @param transform
		 *            a transformation strategy for the
		 *            {@linkplain #keys(Iterable) keys of the function}.
		 * @return this builder.
		 */
		public Builder<T> transform(final TransformationStrategy<? super T> transform) {
			this.transform = transform;
			return this;
		}

		/**
		 * Specifies a temporary directory for the
		 * {@link #store(ChunkedHashStore) ChunkedHashStore}.
		 *
		 * @param tempDir
		 *            a temporary directory for the
		 *            {@link #store(ChunkedHashStore) ChunkedHashStore} files,
		 *            or {@code null} for the standard temporary directory.
		 * @return this builder.
		 */
		public Builder<T> tempDir(final File tempDir) {
			this.tempDir = tempDir;
			return this;
		}

		/**
		 * Specifies a chunked hash store containing the keys.
		 *
		 * <p>
		 * Note that if you specify a store, it is your responsibility that it
		 * conforms to the rest of the data: it must contain ranks if you do not
		 * specify {@linkplain #values(LongIterable) values} or if you use the
		 * {@linkplain #indirect() indirect} feature, values otherwise.
		 *
		 * @param chunkedHashStore
		 *            a chunked hash store containing the keys associated with their
		 *            values and counting value frequencies, or {@code null}; the
		 *            store can be unchecked, but in this case you must
		 *            specify {@linkplain #keys(Iterable) keys} and a
		 *            {@linkplain #transform(TransformationStrategy) transform}
		 *            (otherwise, in case of a hash collision in the store an
		 *            {@link IllegalStateException} will be thrown).
		 * @return this builder.
		 */
		public Builder<T> store(final ChunkedHashStore<T> chunkedHashStore) {
			this.chunkedHashStore = chunkedHashStore;
			return this;
		}

		/**
		 * Specifies the values assigned to the {@linkplain #keys(Iterable)
		 * keys}.
		 *
		 * <p>
		 * Contrarily to {@link #values(LongIterable)}, this method does not
		 * require a complete scan of the value to determine the output width.
		 *
		 * @param values
		 *            values to be assigned to each element, in the same order
		 *            of the {@linkplain #keys(Iterable) keys}.
		 * @return this builder.
		 * @see #values(LongIterable)
		 */
		public Builder<T> values(final LongIterable values) {
			this.values = values;
			return this;
		}

		/**
		 * Specifies that the function construction must be indirect: a provided
		 * {@linkplain #store(ChunkedHashStore) store} contains indices that
		 * must be used to access the {@linkplain #values(LongIterable) values}.
		 *
		 * <p>
		 * If you specify this option, the provided values <strong>must</strong>
		 * be a {@link LongList} or a {@link LongBigList}.
		 *
		 * @return this builder.
		 */
		public Builder<T> indirect() {
			this.indirect = true;
			return this;
		}

		/**
		 * Specifies a {@linkplain Codec codec} that will be used to encode the function
		 * output values. The default is a {@linkplain Huffman} codec with default parameters.
		 *
		 * @param codec a codec that will be used to encode the function
		 * output values
		 * @return this builder.
		 */
		public Builder<T> codec(final Codec codec) {
			this.codec = codec;
			return this;
		}

		/**
		 * Builds a new function.
		 *
		 * @return a {@link GOV3Function} instance with the specified
		 *         parameters.
		 * @throws IllegalStateException
		 *             if called more than once.
		 */
		public GV4CompressedFunction<T> build() throws IOException {
			if (built) throw new IllegalStateException("This builder has been already used");
			if (codec == null) codec = new Codec.Huffman();
			built = true;
			if (transform == null) {
				if (chunkedHashStore != null) transform = chunkedHashStore.transform();
				else throw new IllegalArgumentException("You must specify a TransformationStrategy, either explicitly or via a given ChunkedHashStore");
			}
			return new GV4CompressedFunction<>(keys, transform, values, indirect, tempDir, chunkedHashStore, codec);
		}
	}

	public final static double DELTA = 1.03;
	public final static int DELTA_TIMES_256 = (int) Math.floor(DELTA * 256);
	/** The logarithm of the desired chunk size. */
	public final static int LOG2_CHUNK_SIZE = 10;
	/** The shift for chunks. */
	private final int chunkShift;
	/** The number of keys. */
	protected final long n;
	/** Length of longest codeword **/
	protected final int globalMaxCodewordLength;
	/** The seed used to generate the initial hash triple. */
	protected long globalSeed;
	/**
	 * A long containing three values per chunk:
	 * <ul>
	 * <li>the top {@link #SEED_BITS} bits contain the seed (note that it must
	 * not be shifted right);
	 * <li>the remaining lower bits contain the starting position in
	 * {@link #data} of the bits associated with the chunk.
	 * </ul>
	 */
	protected final long[] offsetAndSeed;

	protected final LongArrayBitVector data;
	/**
	 * The transformation strategy to turn objects of type <code>T</code> into
	 * bit vectors.
	 */
	protected final TransformationStrategy<? super T> transform;
	/** The decoder that will be used to yield output values. */
	protected final Codec.Decoder decoder;

	/**
	 * Creates a new function for the given keys and values.
	 *
	 * @param keys
	 *            the keys in the domain of the function, or {@code null}.
	 * @param transform
	 *            a transformation strategy for the keys.
	 * @param values
	 *            values to be assigned to each element, in the same order of
	 *            the iterator returned by <code>keys</code>; if {@code null},
	 *            the assigned value will the ordinal number of each
	 *            element.
	 * @param indirect
	 *            if true, <code>chunkedHashStore</code> contains ordinal
	 *            positions, and <code>values</code> is a {@link LongIterable}
	 *            that must be accessed to retrieve the actual values.
	 * @param tempDir
	 *            a temporary directory for the store files, or {@code null} for
	 *            the standard temporary directory.
	 * @param chunkedHashStore
	 *            a chunked hash store containing the keys associated with their
	 *            values and counting value frequencies, or {@code null}; the
	 *            store can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be
	 *            non-{@code null}.
	 */
	@SuppressWarnings("resource")
	protected GV4CompressedFunction(final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final LongIterable values, final boolean indirect, final File tempDir, ChunkedHashStore<T> chunkedHashStore, final Codec codec) throws IOException {
		Objects.requireNonNull(codec, "Null codec");
		this.transform = transform;
		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		final XoRoShiRo128PlusRandomGenerator r = new XoRoShiRo128PlusRandomGenerator();
		pl.itemsName = "keys";
		final boolean givenChunkedHashStore = chunkedHashStore != null;
		if (!givenChunkedHashStore) {
			if (keys == null) throw new IllegalArgumentException("If you do not provide a chunked hash store, you must provide the keys");
			chunkedHashStore = new ChunkedHashStore<>(transform, tempDir, -1, pl);
			chunkedHashStore.reset(r.nextLong());
			if (values == null || indirect) chunkedHashStore.addAll(keys.iterator());
			else chunkedHashStore.addAll(keys.iterator(), values.iterator());

		}
		n = chunkedHashStore.size();
		defRetValue = -1;

		final Long2LongOpenHashMap frequencies;
		if (indirect) {
			frequencies = new Long2LongOpenHashMap();
			for(final long v : values) frequencies.addTo(v, 1);
		}
		else frequencies = chunkedHashStore.value2FrequencyMap();

		final Codec.Coder coder = frequencies.isEmpty() ? ZeroCodec.getInstance().getCoder(frequencies) : codec.getCoder(frequencies);
		globalMaxCodewordLength = coder.maxCodewordLength();
		decoder = coder.getDecoder();

		int log2NumChunks = Math.max(0, Fast.mostSignificantBit(n >> LOG2_CHUNK_SIZE));
		// Adjustment for the empty map
		if (log2NumChunks < 1) log2NumChunks = 1;
		chunkShift = chunkedHashStore.log2Chunks(log2NumChunks);
		final int numChunks = 1 << log2NumChunks;
		LOGGER.debug("Number of chunks: " + numChunks);
		offsetAndSeed = new long[numChunks + 1];

		final OfflineIterable<BitVector, LongArrayBitVector> offlineData = new OfflineIterable<>(BitVectors.OFFLINE_SERIALIZER, LongArrayBitVector.getInstance());

		int duplicates = 0;

		for (;;) {
			pl.expectedUpdates = numChunks;
			pl.itemsName = "chunks";
			pl.start("Analysing chunks... ");
			final AtomicLong unsolvable = new AtomicLong();

			try {
				final int numberOfThreads = Integer.parseInt(System.getProperty(NUMBER_OF_THREADS_PROPERTY, Integer.toString(Math.min(16, Runtime.getRuntime().availableProcessors()))));
				final ArrayBlockingQueue<Pair<Chunk, Integer>> chunkQueue = new ArrayBlockingQueue<>(numberOfThreads);
				final ReorderingBlockingQueue<LongArrayBitVector> queue = new ReorderingBlockingQueue<>(numberOfThreads * 128);
				final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads + 2);
				final ExecutorCompletionService<Void> executorCompletionService = new ExecutorCompletionService<>(executorService);

				executorCompletionService.submit(() -> {
					for(;;) {
						final LongArrayBitVector data = queue.take();
						if (data == END_OF_SOLUTION_QUEUE) return null;
						offlineData.add(data);
					}
				});

				final ChunkedHashStore<T> chs = chunkedHashStore;
				executorCompletionService.submit(() -> {
					try {
						final Iterator<Chunk> iterator = chs.iterator();
						for(int i1 = 0; iterator.hasNext(); i1++) {
							final Chunk chunk = new Chunk(iterator.next());
							assert i1 == chunk.index();
							final LongBigList valueList = chunk.valueList(indirect ? values : null);
							long sumOfLengths = 0;
							for(int i = 0; i < chunk.size(); i++)
								sumOfLengths += coder.codewordLength(valueList.getLong(i));

							// We add the length of the longest keyword to avoid wrapping up indices
							assert (sumOfLengths * DELTA_TIMES_256 >>> 8) + globalMaxCodewordLength <= Integer.MAX_VALUE;
							synchronized(offsetAndSeed) {
								offsetAndSeed[i1 + 1] = offsetAndSeed[i1] + (sumOfLengths * DELTA_TIMES_256 >>> 8) + globalMaxCodewordLength;
								assert offsetAndSeed[i1 + 1] <= OFFSET_MASK + 1;
							}
							chunkQueue.put(new Pair<>(chunk, Integer.valueOf((int)sumOfLengths)));
						}
					}
					finally {
						for(int i2 = numberOfThreads; i2-- != 0;) chunkQueue.put(END_OF_CHUNK_QUEUE);
					}
					return null;
				});

				final AtomicInteger activeThreads = new AtomicInteger(numberOfThreads);
				for(int i = numberOfThreads; i-- != 0;) executorCompletionService.submit(() -> {
					Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
					long chunkTime = 0;
					long outputTime = 0;
					for(;;) {
						long start = System.nanoTime();
						final Pair<Chunk, Integer> chunkLength = chunkQueue.take();
						chunkTime += System.nanoTime() - start;
						if (chunkLength == END_OF_CHUNK_QUEUE) {
							if (activeThreads.decrementAndGet() == 0) queue.put(END_OF_SOLUTION_QUEUE, numChunks);
							LOGGER.debug("Queue waiting time: " + Util.format(chunkTime / 1E9) + "s");
							LOGGER.debug("Output waiting time: " + Util.format(outputTime / 1E9) + "s");
							return null;
						}
						final Chunk chunk = chunkLength.getFirst();
						final int numEquations = chunkLength.getSecond().intValue();
						final int numVariables = (int) (offsetAndSeed[chunk.index() + 1] - offsetAndSeed[chunk.index()] & OFFSET_MASK);
						long seed = 0;
						final Linear4SystemSolver solver = new Linear4SystemSolver(numVariables, numEquations);

						for(;;) {
							final boolean solved = solver.generateAndSolve(chunk, seed, chunk.valueList(indirect ? values : null), coder, numVariables - globalMaxCodewordLength, globalMaxCodewordLength);
							unsolvable.addAndGet(solver.unsolvable);
							if (solved) break;
							seed += SEED_STEP;
							if (seed == 0) throw new AssertionError("Exhausted local seeds");
						}

						synchronized (offsetAndSeed) {
							offsetAndSeed[chunk.index()] |= seed;
						}

						final LongArrayBitVector data = LongArrayBitVector.getInstance();
						final long[] solution = solver.solution;
						data.length(solution.length);
						for (int j = 0; j < solution.length; j++) data.set(j, (int)solution[j]);

						start = System.nanoTime();
						queue.put(data, chunk.index());
						outputTime += System.nanoTime() - start;
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

				LOGGER.info("Unsolvable systems: " + unsolvable.get() + "/" + (unsolvable.get() + numChunks) + " (" + Util.format(100.0 * unsolvable.get() / (unsolvable.get() + numChunks)) + "%)");
//				LOGGER.info("Mean node peeled for solved systems: " + Util.format((double) peeledSumSolved / totalNodesSolvable * 100) + "%");

//				if (unsolvable == 0) {
//					LOGGER.info("Mean node peeled for unsolved systems: " + 0 + "%");
//				} else {
//					LOGGER.info("Mean node peeled for unsolved systems: " + Util.format((double) peeledSumUnsolvable / totalNodesUnsolvable * 100) + "%");
//
//				}
				pl.done();
				break;
			}
			catch (final ChunkedHashStore.DuplicateException e) {
				if (keys == null) throw new IllegalStateException("You provided no keys, but the chunked hash store was not checked");
				if (duplicates++ > 3) throw new IllegalArgumentException("The input list contains duplicates");
				LOGGER.warn("Found duplicate. Recomputing triples...");
				chunkedHashStore.reset(r.nextLong());
				pl.itemsName = "keys";
				if (values == null || indirect) chunkedHashStore.addAll(keys.iterator());
				else chunkedHashStore.addAll(keys.iterator(), values.iterator());
				offlineData.clear();
			}
		}

		if (DEBUG) {
			System.out.println("MaxCodeword: " + globalMaxCodewordLength);
			System.out.println("Offsets: " + Arrays.toString(offsetAndSeed));
		}
		globalSeed = chunkedHashStore.seed();
		final LongArrayBitVector dataBitVector = LongArrayBitVector.getInstance((offsetAndSeed[numChunks] & OFFSET_MASK) + 1);
		this.data = dataBitVector;
		final OfflineIterator<BitVector, LongArrayBitVector> iterator = offlineData.iterator();
		while (iterator.hasNext())
			dataBitVector.append(iterator.next());
		iterator.close();
		offlineData.close();
		data.add(0);
		LOGGER.info("Completed.");

		LOGGER.info("Actual bit cost per element: " + (double) numBits() / n);
		if (!givenChunkedHashStore) chunkedHashStore.close();
	}

	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object o) {
		final int[] e = new int[4];
		final long[] h = new long[3];
		Hashes.spooky4(transform.toBitVector((T) o), globalSeed, h);
		final int chunk = (int)(h[0] >>> chunkShift);
		final long olc = offsetAndSeed[chunk];
		final long chunkOffset = olc & OFFSET_MASK;
		final long nextChunkOffset = offsetAndSeed[chunk + 1] & OFFSET_MASK;
		final long chunkSeed = olc & SEED_MASK;
		final int w = globalMaxCodewordLength;
		final int numVariables = (int)(nextChunkOffset - chunkOffset - w);
		Linear4SystemSolver.tripleToEquation(h, chunkSeed, numVariables, e);
		final long e0 = e[0] + chunkOffset, e1 = e[1] + chunkOffset,
				e2 = e[2] + chunkOffset, e3 = e[3] + chunkOffset;
		final long code = data.getLong(e0, e0 + w) ^ data.getLong(e1, e1 + w) ^
				data.getLong(e2, e2 + w) ^ data.getLong(e3, e3 + w);
		return decoder.decode(code);
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
		return (int) Math.min(n, Integer.MAX_VALUE);
	}

	/**
	 * Returns the number of bits used by this structure.
	 *
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		if (n == 0) return 0;
		return data.size64() + offsetAndSeed.length * (long) Long.SIZE + decoder.numBits();
	}

	@Override
	public boolean containsKey(final Object o) {
		return true;
	}

	public void dump(final String file) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocateDirect(128 * 1024 * 1024).order(ByteOrder.nativeOrder());
		final FileOutputStream fos = new FileOutputStream(file);
		final FileChannel channel = fos.getChannel();

		buffer.clear();
		buffer.putLong(size64());
		buffer.putLong(chunkShift);
		buffer.putLong(globalMaxCodewordLength);
		buffer.putLong(globalSeed);
		buffer.putLong(offsetAndSeed.length);
		for(final long l : offsetAndSeed) buffer.putLong(l);
		buffer.flip();
		channel.write(buffer);
		buffer.clear();

		final long[] array = data.bits();
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
		buffer.clear();

		((Huffman.Coder.Decoder)decoder).dump(buffer);
		buffer.flip();
		channel.write(buffer);

		fos.close();
	}


	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(GV4CompressedFunction.class.getName(), "Builds a GOV function mapping a newline-separated list of strings to their ordinal position, or to specific values.",
				new Parameter[] {
						new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
						new FlaggedOption("tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files."),
						new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
						new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
						new Switch("byteArray", 'b', "byte-array", "Create a function on byte arrays (no character encoding)."),
						new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
						new FlaggedOption("codec", JSAP.STRING_PARSER, "HUFFMAN", JSAP.NOT_REQUIRED, 'C', "codec", "The name of the codec to use (UNARY, BINARY, GAMMA, HUFFMAN, LLHUFFMAN)."),
						new FlaggedOption("limit", JSAP.INTEGER_PARSER, "20", JSAP.NOT_REQUIRED, 'l', "limit", "Decoding-table length limit for the LLHUFFMAN codec."),
						new FlaggedOption("values", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'v', "values", "A binary file in DataInput format containing a long for each string (otherwise, the values will be the ordinal positions of the strings)."),
						new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised GOV function."),
						new UnflaggedOption("stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory."), });

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String stringFile = jsapResult.getString("stringFile");
		final Charset encoding = (Charset) jsapResult.getObject("encoding");
		final File tempDir = jsapResult.getFile("tempDir");
		final boolean byteArray = jsapResult.getBoolean("byteArray");
		final boolean zipped = jsapResult.getBoolean("zipped");
		final boolean iso = jsapResult.getBoolean("iso");
		final boolean utf32 = jsapResult.getBoolean("utf32");
		final int limit = jsapResult.getInt("limit");

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
			final Collection<byte[]> collection = new FileLinesByteArrayCollection(stringFile, zipped);
			BinIO.storeObject(new GV4CompressedFunction<>(collection, TransformationStrategies.rawByteArray(), values, false, tempDir, null, codec), functionName);
		} else {
			final Collection<MutableString> collection;
			if ("-".equals(stringFile)) {
				final ProgressLogger pl = new ProgressLogger(LOGGER);
				pl.displayLocalSpeed = true;
				pl.displayFreeMemory = true;
				pl.start("Loading strings...");
				collection = new LineIterator(new FastBufferedReader(new InputStreamReader(zipped ? new GZIPInputStream(System.in) : System.in, encoding)), pl).allLines();
				pl.done();
			} else collection = new FileLinesCollection(stringFile, encoding.toString(), zipped);
			final TransformationStrategy<CharSequence> transformationStrategy = iso ? TransformationStrategies.rawIso() : utf32 ? TransformationStrategies.rawUtf32() : TransformationStrategies.rawUtf16();

			BinIO.storeObject(new GV4CompressedFunction<>(collection, transformationStrategy, values, false, tempDir, null, codec), functionName);
		}
		LOGGER.info("Completed.");
	}

}
