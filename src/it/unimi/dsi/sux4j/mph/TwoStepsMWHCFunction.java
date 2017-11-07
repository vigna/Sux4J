package it.unimi.dsi.sux4j.mph;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2017 Sebastiano Vigna
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

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.zip.GZIPInputStream;

import org.apache.commons.collections.Predicate;
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


/** A function stored using two {@linkplain MWHCFunction Majewski-Wormald-Havas-Czech functions}&mdash;one for
 * frequent values, and one for infrequent values. This naive idea turns out to be very effective in reducing the function
 * size when the distribution of values is skewed (e.g., as it happens in a {@link TwoStepsLcpMonotoneMinimalPerfectHashFunction}).
 *
 * <p>To create an instance, we perform a pre-scan of the values to be assigned. If possible, we finds the best possible
 * <var>r</var> such that the 2<sup><var>r</var></sup> &minus; 1 most frequent values can be stored in a {@link MWHCFunction}
 * and suitably remapped when read. The function uses 2<sup><var>r</var></sup> &minus; 1 as an escape symbol for all other
 * values, which are stored in a separate function.
 *
 * <p><strong>Warning</strong>: during the construction phase, a {@linkplain ChunkedHashStore#filter(Predicate) filter}
 * will be set on the {@link ChunkedHashStore} used to store the keys. If you are {@linkplain Builder#store(ChunkedHashStore) passing a store},
 * you will have to reset it to its previous state.
 *
 * @author Sebastiano Vigna
 * @since 1.0.2
 * @deprecated Please use {@link TwoStepsGOV3Function}.
 */

@Deprecated
public class TwoStepsMWHCFunction<T> extends AbstractHashFunction<T> implements Serializable, Size64 {
    public static final long serialVersionUID = 5L;
    private static final Logger LOGGER = LoggerFactory.getLogger(TwoStepsMWHCFunction.class);

    private final static boolean ASSERTS = false;

	/** A builder class for {@link TwoStepsMWHCFunction}. */
	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected File tempDir;
		protected ChunkedHashStore<T> chunkedHashStore;
		protected LongBigList values;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;

		/** Specifies the keys of the function; if you have specified a {@link #store(ChunkedHashStore) ChunkedHashStore}, it can be {@code null}.
		 *
		 * @param keys the keys of the function.
		 * @return this builder.
		 */
		public Builder<T> keys(final Iterable<? extends T> keys) {
			this.keys = keys;
			return this;
		}

		/** Specifies the transformation strategy for the {@linkplain #keys(Iterable) keys of the function}.
		 *
		 * @param transform a transformation strategy for the {@linkplain #keys(Iterable) keys of the function}.
		 * @return this builder.
		 */
		public Builder<T> transform(final TransformationStrategy<? super T> transform) {
			this.transform = transform;
			return this;
		}

		/** Specifies a temporary directory for the {@link #store(ChunkedHashStore) ChunkedHashStore}.
		 *
		 * @param tempDir a temporary directory for the {@link #store(ChunkedHashStore) ChunkedHashStore} files, or {@code null} for the standard temporary directory.
		 * @return this builder.
		 */
		public Builder<T> tempDir(final File tempDir) {
			this.tempDir = tempDir;
			return this;
		}

		/** Specifies a chunked hash store containing the keys associated with their rank.
		 *
		 * <p><strong>Warning</strong>: during the construction phase, a {@linkplain ChunkedHashStore#filter(Predicate) filter}
		 * will be set on the specified {@link ChunkedHashStore}. You will have to reset it to its previous state.
		 *
		 * @param chunkedHashStore a chunked hash store containing the keys associated with their rank, or {@code null}; the store
		 * can be unchecked, but in this case you must specify {@linkplain #keys(Iterable) keys} and a {@linkplain #transform(TransformationStrategy) transform}
		 * (otherwise, in case of a hash collision in the store an {@link IllegalStateException} will be thrown).
		 * @return this builder.
		 */
		public Builder<T> store(final ChunkedHashStore<T> chunkedHashStore) {
			this.chunkedHashStore = chunkedHashStore;
			return this;
		}

		/** Specifies the values assigned to the {@linkplain #keys(Iterable) keys}; the output width of the function will
		 * be the minimum width needed to represent all values.
		 *
		 * @param values values to be assigned to each element, in the same order of the {@linkplain #keys(Iterable) keys}.
		 * @return this builder.
		 */
		public Builder<T> values(final LongBigList values) {
			this.values = values;
			return this;
		}

		/** Builds a new function.
		 *
		 * @return an {@link MWHCFunction} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public TwoStepsMWHCFunction<T> build() throws IOException {
			if (built) throw new IllegalStateException("This builder has been already used");
			built = true;
			if (transform == null) {
				if (chunkedHashStore != null) transform = chunkedHashStore.transform();
				else throw new IllegalArgumentException("You must specify a TransformationStrategy, either explicitly or via a given ChunkedHashStore");
			}
			return new TwoStepsMWHCFunction<>(keys, transform, values, tempDir, chunkedHashStore);
		}
	}


	/** The number of keys. */
	protected final long n;
	/** The transformation strategy to turn objects of type <code>T</code> into bit vectors. */
	protected final TransformationStrategy<? super T> transform;
	/** The first function, or {@code null}. The special output value {@link #escape} denotes that {@link #secondFunction}
	 * should be queried instead. */
	protected final MWHCFunction<T> firstFunction;
	/** The second function. All queries for which {@link #firstFunction} returns
	 * {@link #escape} (or simply all queries, if {@link #firstFunction} is {@code null}) will be rerouted here. */
	protected final MWHCFunction<T> secondFunction;
	/** A mapping from values of the first function to actual values, provided that there is a {@linkplain #firstFunction first function}. */
	protected final long[] remap;
	/** The escape value returned by {@link #firstFunction} to suggest that {@link #secondFunction} should be queried instead, provided that there is a {@linkplain #firstFunction first function}. */
	protected final int escape;
	/** The seed to be used when converting keys to triples. */
	protected long seed;
	/** The width of the output of this function, in bits. */
	protected final int width;
	/** The mean of the rank distribution. */
	protected final double rankMean;

	/** Creates a new two-step function for the given keys and values.
	 *
	 * @param keys the keys in the domain of the function.
	 * @param transform a transformation strategy for the keys.
	 * @param values values to be assigned to each key, in the same order of the iterator returned by <code>keys</code>; if {@code null}, the
	 * assigned value will the the ordinal number of each key.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the keys associated with their rank, or {@code null}; the store
	 * can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be non-{@code null}.
	 */
	protected TwoStepsMWHCFunction(final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final LongBigList values, final File tempDir, ChunkedHashStore<T> chunkedHashStore) throws IOException {
		this.transform = transform;
		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		final RandomGenerator random = new XoRoShiRo128PlusRandomGenerator();
		pl.itemsName = "keys";

		final boolean givenChunkedHashStore = chunkedHashStore != null;
		if (chunkedHashStore == null) {
			if (keys == null) throw new IllegalArgumentException("If you do not provide a chunked hash store, you must provide the keys");
			chunkedHashStore = new ChunkedHashStore<>(transform, pl);
			chunkedHashStore.reset(random.nextLong());
			chunkedHashStore.addAll(keys.iterator());
		}
		n = chunkedHashStore.size();
		defRetValue = -1; // For the very few cases in which we can decide

		if (n == 0) {
			rankMean = escape = width = 0;
			firstFunction = secondFunction = null;
			remap = null;
			if (! givenChunkedHashStore) chunkedHashStore.close();
			return;
		}

		// Compute distribution of values and maximum number of bits.
		int w = 0, size;
		long v;
		final Long2LongOpenHashMap counts = new Long2LongOpenHashMap();
		counts.defaultReturnValue(-1);
		for(LongIterator i = values.iterator(); i.hasNext();) {
			v = i.nextLong();
			counts.put(v, counts.get(v) + 1);
			size = Fast.length(v);
			if (size > w) w = size;
		}

		this.width = w;
		final int m = counts.size();

		LOGGER.debug("Generating two-steps MWHC function with " + w + " output bits...");

		// Sort keys by reverse frequency
		final long[] keysArray = counts.keySet().toLongArray(new long[m]);
		LongArrays.quickSort(keysArray, 0, keysArray.length, (a, b) -> Long.compare(counts.get(b), counts.get(a)));

		long mean = 0;
		for(int i = 0; i < keysArray.length; i++) mean += i * counts.get(keysArray[i]);
		rankMean = (double)mean / n;

		// Analyze data and choose a threshold
		long post = n, bestCost = Long.MAX_VALUE;
		int pos = 0, best = -1;

		// Examine every possible choice for r. Note that r = 0 implies one function, so we do not need to test the case r == w.
		for(int r = 0; r < w && pos < m; r++) {

			/* This cost function is dependent on the implementation of MWHCFunction.
			 * Note that for r = 0 we are actually computing the cost of a single function (the first one). */
			final long cost = (long)Math.min(HypergraphSorter.GAMMA * n * 1.126 + n * r, HypergraphSorter.GAMMA * n * r) +
					(long)Math.min(HypergraphSorter.GAMMA * post * 1.126 + post * w, HypergraphSorter.GAMMA * post * w) +
					pos * Long.SIZE;

			if (cost < bestCost) {
				best = r;
				bestCost = cost;
			}

			/* We add to pre and subtract from post the counts of keys from position (1<<r)-1 to position (1<<r+1)-1. */
			for(int j = 0; j < (1 << r) && pos < m; j++) {
				final long c = counts.get(keysArray[pos++]);
				post -= c;
			}
		}

		if (ASSERTS) assert pos == m;

		counts.clear();
		counts.trim();

		// We must keep the remap array small.
		if (best >= Integer.SIZE) best = Integer.SIZE - 1;

		LOGGER.debug("Best threshold: " + best);
		escape = (1 << best) - 1;
		System.arraycopy(keysArray, 0, remap = new long[escape], 0, remap.length);
		final Long2LongOpenHashMap map = new Long2LongOpenHashMap();
		map.defaultReturnValue(-1);
		for(int i = 0; i < escape; i++) map.put(remap[i], i);

		if (best != 0) {
			firstFunction = new MWHCFunction.Builder<T>().keys(keys).transform(transform).store(chunkedHashStore).values(new AbstractLongBigList() {
				@Override
				public long getLong(long index) {
					long value = map.get(values.getLong(index));
					return value == -1 ? escape : value;
				}

				@Override
				public long size64() {
					return n;
				}
			}, best).indirect().build();

			LOGGER.debug("Actual bit cost per key of first function: " + (double)firstFunction.numBits() / n);
		}
		else firstFunction = null;

		chunkedHashStore.filter(triple -> firstFunction == null || firstFunction.getLongByTriple((long[])triple) == escape);

		secondFunction = new MWHCFunction.Builder<T>().store(chunkedHashStore).values(values, w).indirect().build();

		this.seed = chunkedHashStore.seed();
		if (! givenChunkedHashStore) chunkedHashStore.close();

		LOGGER.debug("Actual bit cost per key of second function: " + (double)secondFunction.numBits() / n);

		LOGGER.info("Actual bit cost per key: " + (double)numBits() / n);
		LOGGER.info("Completed.");

	}


	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object o) {
		if (n == 0) return defRetValue;
		final long[] triple = new long[3];
		Hashes.spooky4(transform.toBitVector((T)o), seed, triple);
		if (firstFunction != null) {
			final int firstValue = (int)firstFunction.getLongByTriple(triple);
			if (firstValue == -1) return defRetValue;
			if (firstValue != escape) return remap[firstValue];
		}
		return secondFunction.getLongByTriple(triple);
	}

	public long getLongByTriple(final long[] triple) {
		if (firstFunction != null) {
			final int firstValue = (int)firstFunction.getLongByTriple(triple);
			if (firstValue == -1) return defRetValue;
			if (firstValue != escape) return remap[firstValue];
		}
		return secondFunction.getLongByTriple(triple);
	}

	@Override
	public long size64() {
		return n;
	}

	/** Returns the number of bits used by this structure.
	 *
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return (firstFunction != null ? firstFunction.numBits() : 0) + secondFunction.numBits() + transform.numBits() + remap.length * (long)Long.SIZE;
	}

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(TwoStepsMWHCFunction.class.getName(), "Builds a two-steps MWHC function mapping a newline-separated list of strings to their ordinal position, or to specific values.",
				new Parameter[] {
			new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
			new FlaggedOption("tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files."),
			new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
			new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
			new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
			new FlaggedOption("values", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'v', "values", "A binary file in DataInput format containing a long for each string (otherwise, the values will be the ordinal positions of the strings)."),
			new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised MWHC function."),
			new UnflaggedOption("stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory."),
		});

		JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		LOGGER.warn("This class is deprecated: please use TwoStepsGOV3Function");

		final String functionName = jsapResult.getString("function");
		final String stringFile = jsapResult.getString("stringFile");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final File tempDir = jsapResult.getFile("tempDir");
		final boolean zipped = jsapResult.getBoolean("zipped");
		final boolean iso = jsapResult.getBoolean("iso");
		final boolean utf32 = jsapResult.getBoolean("utf32");

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

		BinIO.storeObject(new TwoStepsMWHCFunction<CharSequence>(collection, transformationStrategy, LongBigArrayBigList.wrap(BinIO.loadLongsBig(jsapResult.getString("values"))), tempDir, null), functionName);
		LOGGER.info("Completed.");
	}
}
