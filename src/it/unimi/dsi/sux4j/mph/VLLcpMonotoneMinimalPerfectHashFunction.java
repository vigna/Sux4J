package it.unimi.dsi.sux4j.mph;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
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
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

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

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.io.OfflineIterable;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.BucketedHashStore;
import it.unimi.dsi.sux4j.io.BucketedHashStore.Bucket;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

/** A monotone minimal perfect hash implementation based on fixed-size bucketing that uses
 * longest common prefixes as distributors, and store their lengths using a {@link GOVMinimalPerfectHashFunction}
 * indexing an {@link EliasFanoLongBigList}. In theory, this function should use less memory
 * than an {@link LcpMonotoneMinimalPerfectHashFunction} when the lengths of common prefixes vary
 * wildly, but in practice a {@link TwoStepsLcpMonotoneMinimalPerfectHashFunction} is often a better choice.
 */

public class VLLcpMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable, Size64 {
    public static final long serialVersionUID = 4L;
	private static final Logger LOGGER = LoggerFactory.getLogger(VLLcpMonotoneMinimalPerfectHashFunction.class);
	private static final boolean DEBUG = false;

	/** The number of elements. */
	protected final long n;
	/** The size of a bucket. */
	protected final int bucketSize;
	/** {@link Fast#ceilLog2(int)} of {@link #bucketSize}. */
	protected final int log2BucketSize;
	/** The mask for {@link #log2BucketSize} bits. */
	protected final int bucketSizeMask;
	/** A function mapping each element to a distinct index. */
	protected final GOVMinimalPerfectHashFunction<BitVector> mph;
	/** A list, indexed by {@link #mph}, containing the offset of each element inside its bucket. */
	protected final LongBigList offsets;
	/** A list, indexed by {@link #mph}, containing for each element the length of the longest common prefix of its bucket. */
	protected final EliasFanoLongBigList lcpLengths;
	/** A function mapping each longest common prefix to its bucket. */
	protected final GOV3Function<BitVector> lcp2Bucket;
	/** The transformation strategy. */
	protected final TransformationStrategy<? super T> transform;
	/** The seed to be used when converting keys to signatures. */
	private long seed;

	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object o) {
		if (n == 0) return defRetValue;
		final BitVector bitVector = transform.toBitVector((T)o).fast();
		final long[] signature = new long[2];
		Hashes.spooky4(transform.toBitVector((T)o), seed, signature);
		final long index = mph.getLongBySignature(signature);
		if (index == -1) return defRetValue;
		final long prefix = lcpLengths.getLong(index);
		if (prefix == -1 || prefix > bitVector.length()) return defRetValue;
		return (lcp2Bucket.getLong(bitVector.subVector(0, prefix)) << log2BucketSize) + offsets.getLong(index);
	}

	public VLLcpMonotoneMinimalPerfectHashFunction(final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform) throws IOException {
		this(iterable, -1, transform);
	}

	@SuppressWarnings("unused")
	public VLLcpMonotoneMinimalPerfectHashFunction(final Iterable<? extends T> iterable, final int numElements, final TransformationStrategy<? super T> transform) throws IOException {

		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		this.transform = transform;
		final RandomGenerator r = new XoRoShiRo128PlusRandomGenerator();

		if (numElements == -1) {
			if (iterable instanceof Size64) n = ((Size64)iterable).size64();
			else if (iterable instanceof Collection) n = ((Collection<?>)iterable).size();
			else {
				long c = 0;
				for(final T dummy: iterable) c++;
				n = c;
			}
		}
		else n = numElements;

		if (n == 0) {
			bucketSize = bucketSizeMask = log2BucketSize = 0;
			lcp2Bucket = null;
			offsets = null;
			lcpLengths = null;
			mph = null;
			return;
		}

		defRetValue = -1; // For the very few cases in which we can decide

		final int theoreticalBucketSize = (int)Math.ceil(1 + GOV3Function.C * Math.log(2) + Math.log(n) - Math.log(1 + Math.log(n)));
		log2BucketSize = Fast.ceilLog2(theoreticalBucketSize);
		bucketSize = 1 << log2BucketSize;
		bucketSizeMask = bucketSize - 1;

		final long numBuckets = (n + bucketSize - 1) / bucketSize;

		final LongArrayBitVector prev = LongArrayBitVector.getInstance();
		final LongArrayBitVector curr = LongArrayBitVector.getInstance();
		int currLcp = 0;
		int maxLcp = 0, minLcp = Integer.MAX_VALUE;
		long maxLength = 0, totalLength = 0;

		@SuppressWarnings("resource")
		final BucketedHashStore<BitVector> bucketedHashStore = new BucketedHashStore<>(TransformationStrategies.identity(), pl);
		bucketedHashStore.reset(r.nextLong());
		@SuppressWarnings("resource")
		final
		OfflineIterable<BitVector,LongArrayBitVector> lcps = new OfflineIterable<>(BitVectors.OFFLINE_SERIALIZER, LongArrayBitVector.getInstance());
		pl.expectedUpdates = n;
		pl.start("Scanning collection...");

		final Iterator<? extends T> iterator = iterable.iterator();
		for(long b = 0; b < numBuckets; b++) {
			prev.replace(transform.toBitVector(iterator.next()));
			bucketedHashStore.add(prev);
			pl.lightUpdate();
			maxLength = Math.max(maxLength, prev.length());
			totalLength += Fast.length(1 + prev.length());
			currLcp = (int)prev.length();
			final int currBucketSize = (int)Math.min(bucketSize, n - b * bucketSize);

			for(int i = 0; i < currBucketSize - 1; i++) {
				curr.replace(transform.toBitVector(iterator.next()));
				bucketedHashStore.add(curr);
				pl.lightUpdate();
				final int prefix = (int)curr.longestCommonPrefixLength(prev);
				if (prefix == prev.length() && prefix == curr.length()) throw new IllegalArgumentException("The input bit vectors are not distinct");
				if (prefix == prev.length() || prefix == curr.length()) throw new IllegalArgumentException("The input bit vectors are not prefix-free");
				if (prev.getBoolean(prefix)) throw new IllegalArgumentException("The input bit vectors are not lexicographically sorted");

				currLcp = Math.min(prefix, currLcp);
				prev.replace(curr);

				maxLength = Math.max(maxLength, prev.length());
				totalLength += Fast.length (1 + prev.length());
			}

			lcps.add(prev.subVector(0, currLcp));
			maxLcp = Math.max(maxLcp, currLcp);
			minLcp = Math.min(minLcp, currLcp);
		}

		pl.done();

		// Build function assigning each lcp to its bucket.
		lcp2Bucket = new GOV3Function.Builder<BitVector>().keys(lcps).transform(TransformationStrategies.identity()).build();
		final int[][] lcpLength = IntBigArrays.newBigArray(lcps.size64());
		long p = 0;
		for(final LongArrayBitVector bv : lcps) IntBigArrays.set(lcpLength, p++, (int)bv.length());

		if (DEBUG) {
			for(final BitVector v: lcps) System.err.println(v  + " " + v.length());
			for(final BitVector v: lcps) {
				final long value = lcp2Bucket.getLong(v);
				if (p++ != value) {
					System.err.println("p: " + (p-1) + "  value: " + value + " key:" + v);
					throw new AssertionError();
				}
			}
		}

		lcps.close();

		final Iterable<BitVector> bitVectors = TransformationStrategies.wrap(iterable, transform);
		// Build mph on elements.
		mph = new GOVMinimalPerfectHashFunction.Builder<BitVector>().keys(bitVectors).transform(TransformationStrategies.identity()).store(bucketedHashStore).build();
		this.seed = bucketedHashStore.seed();

		// Build function assigning the lcp length and the bucketing data to each element.
		(offsets = LongArrayBitVector.getInstance().asLongBigList(log2BucketSize)).size(n);
		final LongBigList lcpLengthsTemp = LongArrayBitVector.getInstance().asLongBigList(Fast.length(maxLcp));
		lcpLengthsTemp.size(n);

		LOGGER.info("Generating data tables...");

		for(final Bucket bucket: bucketedHashStore) {
			for(final long[] triple: bucket) {
				final long index = mph.getLongBySignature(triple);
				offsets.set(index, triple[2] & bucketSizeMask);
				lcpLengthsTemp.set(index, IntBigArrays.get(lcpLength, (int)(triple[2] >> log2BucketSize)));
			}
		}

		bucketedHashStore.close();

		lcpLengths = new EliasFanoLongBigList(lcpLengthsTemp.iterator(), minLcp, true);

		if (DEBUG) {
			p = 0;
			for(final T key: iterable) {
				final BitVector bv = transform.toBitVector(key);
				final long index = mph.getLong(bv);
				if (p++ != lcp2Bucket.getLong(bv.subVector(0, lcpLengths.getLong(index))) * bucketSize + offsets.getLong(index)) {
					System.err.println("p: " + (p - 1)
							+ "  Key: " + key
							+ " bucket size: " + bucketSize
							+ " lcp " + transform.toBitVector(key).subVector(0, lcpLengths.getLong(index))
							+ " lcp length: " + lcpLengths.getLong(index)
							+ " bucket " + lcp2Bucket.getLong(transform.toBitVector(key).subVector(0, lcpLengths.getLong(index)))
							+ " offset: " + offsets.getLong(index));
					throw new AssertionError();
				}
			}
		}

		LOGGER.debug("Bucket size: " + bucketSize);
		final double avgLength = (double)totalLength / n;
		LOGGER.debug("Forecast bit cost per element: " + (2 * GOV3Function.C + 2 + avgLength + Fast.log2(avgLength) + Fast.log2(Math.E) - Fast.log2(Fast.log2(Math.E)) + Fast.log2(1 + Fast.log2(n))));
		LOGGER.info("Actual bit cost per element: " + (double)numBits() / n);
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
		if (n == 0) return 0;
		return offsets.size64() * log2BucketSize + lcpLengths.numBits() + lcp2Bucket.numBits() + mph.numBits() + transform.numBits();
	}


	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(VLLcpMonotoneMinimalPerfectHashFunction.class.getName(), "Builds a variable-length LCP-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
			new Switch("huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length."),
			new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
			new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
			new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
			new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised monotone minimal perfect hash function."),
			new UnflaggedOption("stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory."),
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String stringFile = jsapResult.getString("stringFile");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final boolean zipped = jsapResult.getBoolean("zipped");
		final boolean iso = jsapResult.getBoolean("iso");
		final boolean utf32 = jsapResult.getBoolean("utf32");
		final boolean huTucker = jsapResult.getBoolean("huTucker");

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
		final TransformationStrategy<CharSequence> transformationStrategy = huTucker
				? new HuTuckerTransformationStrategy(collection, true)
				: iso
					? TransformationStrategies.prefixFreeIso()
					: utf32
						? TransformationStrategies.prefixFreeUtf32()
						: TransformationStrategies.prefixFreeUtf16();

		BinIO.storeObject(new VLLcpMonotoneMinimalPerfectHashFunction<CharSequence>(collection, transformationStrategy), functionName);
		LOGGER.info("Completed.");
	}
}
