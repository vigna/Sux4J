/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2021 Sebastiano Vigna
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

import static it.unimi.dsi.bits.Fast.log2;
import static java.lang.Math.E;
import static java.lang.Math.log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
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

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.io.FileLinesByteArrayIterable;
import it.unimi.dsi.io.FileLinesMutableStringIterable;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.BucketedHashStore;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

/** A monotone minimal perfect hash implementation based on fixed-size bucketing that uses
 * a {@linkplain ZFastTrieDistributor z-fast trie} as a distributor.
 *
 * <p>See the {@linkplain it.unimi.dsi.sux4j.mph package overview} for a comparison with other implementations.
 * Similarly to a {@link GOV3Function}, an instance of this class may be <em>{@linkplain Builder#signed(int) signed}</em>.
 */

public class ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
    public static final long serialVersionUID = 5L;
	private static final Logger LOGGER = LoggerFactory.getLogger(ZFastTrieDistributorMonotoneMinimalPerfectHashFunction.class);

	/** The number of elements. */
	private final long size;
	/** The logarithm of the bucket size. */
	private final int log2BucketSize;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A hollow trie distributor assigning keys to buckets. */
	private final ZFastTrieDistributor<BitVector> distributor;
	/** The offset of each element into his bucket. */
	private final GOV3Function<BitVector> offset;
	/** The seed returned by the {@link BucketedHashStore}. */
	private long seed;
	/** The mask to compare signatures, or zero for no signatures. */
	protected final long signatureMask;
	/** The signatures. */
	protected final LongBigList signatures;

	/** A builder class for {@link ZFastTrieDistributorMonotoneMinimalPerfectHashFunction}. */
	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected long numKeys = -1;
		protected int signatureWidth;
		protected File tempDir;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;

		/** Specifies the keys to hash.
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

		/** Specifies that the resulting {@link LcpMonotoneMinimalPerfectHashFunction} should be signed using a given number of bits per key.
		 *
		 * @param signatureWidth a signature width, or 0 for no signature.
		 * @return this builder.
		 */
		public Builder<T> signed(final int signatureWidth) {
			this.signatureWidth = signatureWidth;
			return this;
		}

		/** Specifies a temporary directory for the {@link BucketedHashStore}.
		 *
		 * @param tempDir a temporary directory for the {@link BucketedHashStore}. files, or {@code null} for the standard temporary directory.
		 * @return this builder.
		 */
		public Builder<T> tempDir(final File tempDir) {
			this.tempDir = tempDir;
			return this;
		}

		/** Builds a monotone minimal perfect hash function based on a z-fast trie distributor.
		 *
		 * @return a {@link ZFastTrieDistributorMonotoneMinimalPerfectHashFunction} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<T> build() throws IOException {
			if (built) throw new IllegalStateException("This builder has been already used");
			built = true;
			return new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(keys, transform, -1, signatureWidth, tempDir);
		}
	}


	/** Creates a new monotone minimal perfect hash function based on a z-fast trie distributor using the given
	 * keys, transformation strategy and bucket size.
	 *
	 * @param keys the keys among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the keys into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 * @param log2BucketSize the logarithm of the bucket size, or -1 for the default value.
	 * @param signatureWidth a signature width, or 0 for no signature.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 */
	protected ZFastTrieDistributorMonotoneMinimalPerfectHashFunction(final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final int log2BucketSize, final int signatureWidth, final File tempDir) throws IOException {

		this.transform = transform;
		defRetValue = -1; // For the very few cases in which we can decide

		long maxLength = 0;
		long totalLength = 0;
		final RandomGenerator r = new XoRoShiRo128PlusRandomGenerator();
		final BucketedHashStore<BitVector> bucketedHashStore = new BucketedHashStore<>(TransformationStrategies.identity(), tempDir);
		bucketedHashStore.reset(r.nextLong());
		final Iterable<BitVector> bitVectors = TransformationStrategies.wrap(keys, transform);
		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		pl.itemsName = "keys";
		pl.start("Scanning collection...");
		for(final BitVector bv: bitVectors) {
			maxLength = Math.max(maxLength, bv.length());
			totalLength += bv.length();
			bucketedHashStore.add(bv);
			pl.lightUpdate();
		}

		pl.done();

		bucketedHashStore.checkAndRetry(bitVectors);
		size = bucketedHashStore.size();

		if (size == 0) {
			this.log2BucketSize = -1;
			distributor = null;
			offset = null;
			signatureMask = 0;
			signatures = null;
			bucketedHashStore.close();
			return;
		}

		final long averageLength = (totalLength + size - 1) / size;

		final long forecastBucketSize = (long)Math.ceil(10.5 + 4.05 * log(averageLength) + 2.43 * log(log (size) + 1) + 2.43 * log(log(averageLength) + 1));
		this.log2BucketSize = log2BucketSize == -1 ? Fast.mostSignificantBit(forecastBucketSize) : log2BucketSize;

		LOGGER.debug("Average length: " + averageLength);
		LOGGER.debug("Max length: " + maxLength);
		LOGGER.debug("Bucket size: " + (1L << this.log2BucketSize));
		LOGGER.info("Computing z-fast trie distributor...");
		distributor = new ZFastTrieDistributor<>(bitVectors, this.log2BucketSize, TransformationStrategies.identity(), bucketedHashStore);

		LOGGER.info("Computing offsets...");
		offset = new GOV3Function.Builder<BitVector>().store(bucketedHashStore).values(new AbstractLongBigList() {
			final long bucketSizeMask = (1L << ZFastTrieDistributorMonotoneMinimalPerfectHashFunction.this.log2BucketSize) - 1;
			@Override
			public long getLong(final long index) {
				return index & bucketSizeMask;
			}
			@Override
			public long size64() {
				return size;
			}
		}, this.log2BucketSize).indirect().build();

		seed = bucketedHashStore.seed();
		final double logU = averageLength * log(2);
		LOGGER.info("Forecast bit cost per element: "
				+ 1.0 / forecastBucketSize
				* (-6 * log2(log(2)) + 5 * log2(logU) + 2 * log2(forecastBucketSize) +
						log2(log(logU) - log(log(2))) + 6 * GOV3Function.C + 3 * log2 (E) + 3 * log2(log(3.0 * size))
						+ 3 + GOV3Function.C * forecastBucketSize + GOV3Function.C * forecastBucketSize * log2(forecastBucketSize)));

		LOGGER.info("Actual bit cost per element: " + (double)numBits() / size);

		if (signatureWidth != 0) {
			signatureMask = -1L >>> -signatureWidth;
			signatures = bucketedHashStore.signatures(signatureWidth, pl);
		}
		else {
			signatureMask = 0;
			signatures = null;
		}

		bucketedHashStore.close();

	}

	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object o) {
		if (size == 0) return defRetValue;
		final BitVector bv = transform.toBitVector((T)o).fast();
		final long state[] = Hashes.preprocessSpooky4(bv, seed);
		final long[] signature = new long[2];
		Hashes.spooky4(bv, bv.length(), seed, state, signature);

		final long bucket = distributor.getLongByBitVectorSignatureAndState(bv, signature, state);
		final long result = (bucket << log2BucketSize) + offset.getLongBySignature(signature);
		if (signatureMask != 0) return result < 0 || result >= size || signatures.getLong(result) != (signature[0] & signatureMask) ? defRetValue : result;
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < 0 || result >= size ? defRetValue : result;
	}

	@Override
	public long size64() {
		return size;
	}

	public long numBits() {
		if (size == 0) return 0;
		return distributor.numBits() + offset.numBits() + transform.numBits();
	}

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(ZFastTrieDistributorMonotoneMinimalPerfectHashFunction.class.getName(), "Builds a monotone minimal perfect hash using a probabilistic z-fast trie as a distributor reading a newline-separated list of strings.", new Parameter[] {
				new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
				new FlaggedOption("tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files."),
				new Switch("huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length."),
				new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
				new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
				new Switch("byteArray", 'b', "byte-array", "Create a function on byte arrays (no character encoding)."),
				new FlaggedOption("signatureWidth", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "signature-width", "If specified, the signature width in bits; if negative, the generated function will be a dictionary."),
				new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
				new FlaggedOption("decompressor", JSAP.CLASS_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'd', "decompressor", "Use this extension of InputStream to decompress the strings (e.g., java.util.zip.GZIPInputStream)."),
				new FlaggedOption("log2bucket", JSAP.INTEGER_PARSER, "-1", JSAP.NOT_REQUIRED, 'B', "log2bucket", "The base 2 logarithm of the bucket size (mainly for testing)."),
				new UnflaggedOption("function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised monotone minimal perfect hash function."),
				new UnflaggedOption("stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the second case, strings must be fewer than 2^31 and will be loaded into core memory."), });

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("function");
		final String stringFile = jsapResult.getString("stringFile");
		final int log2BucketSize = jsapResult.getInt("log2bucket");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final File tempDir = jsapResult.getFile("tempDir");
		final boolean zipped = jsapResult.getBoolean("zipped");
		Class<? extends InputStream> decompressor = jsapResult.getClass("decompressor");
		final boolean iso = jsapResult.getBoolean("iso");
		final boolean utf32 = jsapResult.getBoolean("utf32");
		final boolean huTucker = jsapResult.getBoolean("huTucker");
		final boolean byteArray = jsapResult.getBoolean("byteArray");
		final int signatureWidth = jsapResult.getInt("signatureWidth", 0);

		if (zipped && decompressor != null) throw new IllegalArgumentException("The zipped and decompressor options are incompatible");
		if (zipped) decompressor = GZIPInputStream.class;

		if (byteArray) {
			if ("-".equals(stringFile)) throw new IllegalArgumentException("Cannot read from standard input when building byte-array functions");
			if (iso || utf32 || huTucker || jsapResult.userSpecified("encoding")) throw new IllegalArgumentException("Encoding options are not available when building byte-array functions");
			final Iterable<byte[]> keys = new FileLinesByteArrayIterable(stringFile, decompressor);
			BinIO.storeObject(new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(keys, TransformationStrategies.prefixFreeByteArray(), log2BucketSize, signatureWidth, tempDir), functionName);
		} else {
			final Iterable<? extends CharSequence> keys;
			if ("-".equals(stringFile)) {
				final ObjectArrayList<String> list = new ObjectArrayList<>();
				keys = list;
				FileLinesMutableStringIterable.iterator(System.in, encoding, decompressor).forEachRemaining(s -> list.add(s.toString()));
			} else keys = new FileLinesMutableStringIterable(stringFile, encoding, decompressor);

			final TransformationStrategy<CharSequence> transformationStrategy = huTucker ? new HuTuckerTransformationStrategy(keys, true) : iso ? TransformationStrategies.prefixFreeIso() : utf32 ? TransformationStrategies.prefixFreeUtf32() : TransformationStrategies.prefixFreeUtf16();

			BinIO.storeObject(new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(keys, transformationStrategy, log2BucketSize, signatureWidth, tempDir), functionName);
		}
		LOGGER.info("Completed.");
	}
}
