/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2020 Sebastiano Vigna
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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
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

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.SparseRank;
import it.unimi.dsi.sux4j.bits.SparseSelect;
import it.unimi.dsi.sux4j.io.BucketedHashStore;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

/** A version of a {@link PaCoTrieDistributorMonotoneMinimalPerfectHashFunction} whose space usage depends on the <em>average</em>
 * string length, rather than on the <em>maximum string length</em>; mainly of theoretical interest.
 *
 * @author Sebastiano Vigna
 */

public class VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable, Size64 {
    public static final long serialVersionUID = 4L;
	private static final Logger LOGGER = LoggerFactory.getLogger(VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction.class);
	private static final boolean ASSERTS = false;

	/** The number of elements. */
	private final long size;
	/** The size of a bucket. */
	private final int bucketSize;
	/** {@link Fast#ceilLog2(int)} of {@link #bucketSize}. */
	private final int log2BucketSize;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A PaCo trie assigning keys to buckets. */
	private final VLPaCoTrieDistributor<BitVector> distributor;
	/** The offset of each element into his bucket. */
	private final GOV3Function<BitVector> offset;
	private SparseSelect select;

	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object o) {
		if (size == 0) return defRetValue;
		final BitVector bv = transform.toBitVector((T)o).fast();
		final long bucket = distributor.getLong(bv);
		return (bucket == 0 ? 0 : select.select(bucket - 1)) + offset.getLong(bv);
	}

	/** Creates a new PaCo-trie-based monotone minimal perfect hash function using the given
	 * elements and transformation strategy.
	 *
	 * @param elements the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 */
	public VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction(final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform) throws IOException {

		this.transform = transform;
		defRetValue = -1; // For the very few cases in which we can decide

		long maxLength = 0;
		long totalLength = 0;
		BitVector bv;
		final RandomGenerator random = new XoRoShiRo128PlusRandomGenerator();
		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		pl.itemsName = "keys";

		pl.start("Creating chunked hash store...");
		final BucketedHashStore<BitVector> bucketedHashStore = new BucketedHashStore<>(TransformationStrategies.identity());
		bucketedHashStore.reset(random.nextLong());
		for(final T s: elements) {
			bv = transform.toBitVector(s);
			bucketedHashStore.add(bv);
			maxLength = Math.max(maxLength, bv.length());
			totalLength += bv.length();
			pl.lightUpdate();
		}

		pl.done();

		size = bucketedHashStore.size();

		if (size == 0)	{
			bucketSize = log2BucketSize = 0;
			distributor = null;
			offset = null;
			bucketedHashStore.close();
			return;
		}

		final long averageLength = (totalLength + size - 1) / size;

		final int t = Fast.mostSignificantBit((int)Math.floor(averageLength - Math.log(size) - Math.log(averageLength - Math.log(size)) - 1));
		final int firstbucketSize = 1 << t;
		LOGGER.debug("First bucket size estimate: " +  firstbucketSize);

		final Iterable<BitVector> bitVectors = TransformationStrategies.wrap(elements, transform);

		VLPaCoTrieDistributor<BitVector> firstDistributor = new VLPaCoTrieDistributor<>(bitVectors, size, firstbucketSize, TransformationStrategies.identity());

		if (firstDistributor.numBits() == 0 || firstbucketSize >= size) log2BucketSize = t;
		else {
			// Reassign bucket size based on empirical estimation
			log2BucketSize = t - Fast.mostSignificantBit((int)Math.ceil(size / (firstDistributor.numBits() * Math.log(2))));
		}

		bucketSize = 1 << log2BucketSize;
		LOGGER.debug("Second bucket size estimate: " + bucketSize);

		if (firstbucketSize == bucketSize) distributor = firstDistributor;
		else {
			firstDistributor = null;
			distributor = new VLPaCoTrieDistributor<>(bitVectors, size, bucketSize, TransformationStrategies.identity());
		}

		LOGGER.info("Bucket size: " + bucketSize);

		final SparseRank sparseRank;
		if (size > 2 * bucketSize) {
			sparseRank = new SparseRank(distributor.offset.getLong(distributor.offset.size64() - 1) + 1, distributor.offset.size64(), distributor.offset.iterator());
			if (ASSERTS) {
				long i = 0;
				for(final BitVector b: bitVectors) {
					final long d = distributor.getLong(b);
					assert sparseRank.rank(i) == d : "At " + i + ": " + sparseRank.rank(i) + " != " + d;
					i++;
				}
			}

			select = sparseRank.getSelect();
		}
		else {
			sparseRank = null;
			select = null;
		}

		if (size > 0) {
			offset = new GOV3Function.Builder<BitVector>().keys(bitVectors).transform(TransformationStrategies.identity()).store(bucketedHashStore).values(new AbstractLongBigList() {
				@Override
				public long getLong(final long index) {
					final long rank = sparseRank == null ? 0 : index < distributor.offset.getLong(distributor.offset.size64() - 1) + 1 ? sparseRank.rank(index) : distributor.offset.size64();
					if (ASSERTS) {
						assert rank == 0 || distributor.offset.getLong(rank - 1) <= index : distributor.offset.getLong(rank - 1)  + " >= " + index + "(rank=" + rank + ")";
						assert rank == 0 && index < bucketSize * 2 || rank > 0 && index - distributor.offset.getLong(rank - 1) < bucketSize * 2;
					}
					return rank == 0 ? index : index - distributor.offset.getLong(rank - 1);
				}
				@Override
				public long size64() {
					return size;
				}
			}, log2BucketSize + 1).indirect().build();

		}
		else offset = null;

		bucketedHashStore.close();

		LOGGER.debug("Forecast distributor bit cost: " + (size / bucketSize) * (maxLength + log2BucketSize - Math.log(size)));
		LOGGER.debug("Actual distributor bit cost: " + distributor.numBits());
		LOGGER.debug("Forecast bit cost per element: " + (GOV3Function.C + Fast.log2(Math.E) - Fast.log2(Fast.log2(Math.E)) + Fast.log2(maxLength - Fast.log2(size))));
		LOGGER.info("Actual bit cost per element: " + (double)numBits() / size);
	}

	@Override
	public long size64() {
		return size;
	}

	public long numBits() {
		return distributor.numBits() + (offset == null ? 0 : offset.numBits()) + transform.numBits() + (select == null ? 0 : select.numBits());
	}

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction.class.getName(), "Builds a variable-length PaCo trie-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
			new Switch("huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length."),
			new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
			new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
			new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
			new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised monotone minimal perfect hash function."),
			new UnflaggedOption("stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the second case, strings must be fewer than 2^31 and will be loaded into core memory."),
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

		BinIO.storeObject(new VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<CharSequence>(collection, transformationStrategy), functionName);
		LOGGER.info("Completed.");
	}
}
