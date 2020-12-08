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

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.zip.GZIPInputStream;

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

/** A monotone minimal perfect hash implementation based on fixed-size bucketing that uses
 * a {@linkplain HollowTrieDistributor hollow trie} as a distributor.
 *
 */

public class HollowTrieDistributorMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Size64, Serializable {
    public static final long serialVersionUID = 5L;
	private static final Logger LOGGER = LoggerFactory.getLogger(HollowTrieDistributorMonotoneMinimalPerfectHashFunction.class);

	/** The number of elements. */
	private final long size;
	/** The size of a bucket. */
	private final int bucketSize;
	/** {@link Fast#ceilLog2(int)} of {@link #bucketSize}. */
	private final int log2BucketSize;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A hollow trie distributor assigning keys to buckets. */
	private final HollowTrieDistributor<BitVector> distributor;
	/** The offset of each element into his bucket. */
	private final GOV3Function<BitVector> offset;

	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object o) {
		if (size <= 1) return defRetValue;
		final BitVector bv = transform.toBitVector((T)o).fast();
		final long bucket = distributor.getLong(bv);
		// TODO: could use offset's return value to return defRetValue.
		return (bucket << log2BucketSize) + offset.getLong(bv);
	}

	/** Creates a new hollow-trie-based monotone minimal perfect hash function using the given
	 * elements and transformation strategy, using the default temporary directory.
	 *
	 * @param elements the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 */
	public HollowTrieDistributorMonotoneMinimalPerfectHashFunction(final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform) throws IOException {
		this(elements, transform, null);
	}

	/** Creates a new hollow-trie-based monotone minimal perfect hash function using the given
	 * elements, transformation strategy, and temporary directory.
	 *
	 * @param elements the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 * @param tempDir a directory for the temporary files created during construction
	 * by the {@link HollowTrieDistributor}, or {@code null} for the default temporary directory.
	 */
	public HollowTrieDistributorMonotoneMinimalPerfectHashFunction(final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final File tempDir) throws IOException {

		this.transform = transform;

		long maxLength = 0;
		long totalLength = 0;
		long c = 0;
		BitVector bv;
		for(final T s: elements) {
			bv = transform.toBitVector(s);
			maxLength = Math.max(maxLength, bv.length());
			totalLength += bv.length();
			c++;
		}

		size = c;
		defRetValue = size == 1 ? 0 : -1; // For the very few cases in which we can decide

		if (size <= 1) {
			bucketSize = log2BucketSize = 0;
			distributor = null;
			offset = null;
			return;
		}

		final long averageLength = (totalLength + size - 1) / size;

		// The distributor cannot contain just one string
		final int l = Fast.ceilLog2(Math.round((long)((Math.log(averageLength) + 2) * Math.log(2) / GOV3Function.C)));
		log2BucketSize = size / (1 << l) <= 1 ? 0 : l;
		bucketSize = 1 << log2BucketSize;
		final int bucketMask = bucketSize - 1;
		LOGGER.debug("Bucket size: " + bucketSize);

		final Iterable<BitVector> bitVectors = TransformationStrategies.wrap(elements, transform);
		distributor = new HollowTrieDistributor<>(bitVectors, log2BucketSize, TransformationStrategies.identity(), tempDir);
		offset = new GOV3Function.Builder<BitVector>().keys(bitVectors).transform(TransformationStrategies.identity()).values(new AbstractLongBigList() {
			@Override
			public long getLong(final long index) {
				return index & bucketMask;
			}
			@Override
			public long size64() {
				return size;
			}
		}, log2BucketSize).build();


		LOGGER.debug("Forecast bit cost per element: " + (GOV3Function.C * (1 / Math.log(2) + 2 + Fast.log2(Math.log(2) / GOV3Function.C)) + Fast.log2(2 + Fast.log2(averageLength + 1))));
		LOGGER.info("Actual bit cost per element: " + (double)numBits() / size);

	}

	@Override
	public long size64() {
		return size;
	}

	public long numBits() {
		return distributor.numBits() + offset.numBits() + transform.numBits();
	}

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(HollowTrieDistributorMonotoneMinimalPerfectHashFunction.class.getName(), "Builds a monotone minimal perfect hash using a hollow trie as a distributor reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
			new Switch("huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length."),
			new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
			new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
			new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
			new FlaggedOption("tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 't', "temp-dir", "A temporary directory for the files created during the construction."),
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
		final File tempDir = jsapResult.getFile("tempDir");

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

		BinIO.storeObject(new HollowTrieDistributorMonotoneMinimalPerfectHashFunction<CharSequence>(collection, transformationStrategy, tempDir), functionName);
		LOGGER.info("Completed.");
	}
}
