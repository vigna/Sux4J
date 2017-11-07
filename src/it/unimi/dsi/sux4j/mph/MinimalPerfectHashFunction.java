package it.unimi.dsi.sux4j.mph;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
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
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

import it.unimi.dsi.big.io.FileLinesByteArrayCollection;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2017 Sebastiano Vigna
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
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.Rank11;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

/**
 * A minimal perfect hash function.
 *
 * <P>Given a list of keys without duplicates, the {@linkplain Builder builder} of this class finds a minimal
 * perfect hash function for the list. Subsequent calls to the {@link #getLong(Object)} method will
 * return a distinct number for each key in the list. For keys out of the list, the
 * resulting number is not specified. In some (rare) cases it might be possible to establish that a
 * key was not in the original list, and in that case -1 will be returned;
 * by <em>signing</em> the function (see below), you can guarantee with a prescribed probability
 * that -1 will be returned on keys not in the original list. The class can then be
 * saved by serialisation and reused later.
 *
 * <p>This class uses a {@linkplain ChunkedHashStore chunked hash store} to provide highly scalable construction. Note that at construction time
 * you can {@linkplain Builder#store(ChunkedHashStore) pass a ChunkedHashStore}
 * containing the keys (associated with any value); however, if the store is rebuilt because of a
 * {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException} it will be rebuilt associating with each key its ordinal position.
 *
 * <P>The theoretical memory requirements for the algorithm we use are 2{@link HypergraphSorter#GAMMA &gamma;}=2.46 +
 * o(<var>n</var>) bits per key, plus the bits for the random hashes (which are usually
 * negligible). The o(<var>n</var>) part is due to an embedded ranking scheme that increases space
 * occupancy by 6.25%, bringing the actual occupied space to around 2.68 bits per key.
 *
 * <P>For convenience, this class provides a main method that reads from standard input a (possibly
 * <code>gzip</code>'d) sequence of newline-separated strings, and writes a serialised minimal
 * perfect hash function for the given list.
 *
 * <h3>Signing</h3>
 *
 * <p>Optionally, it is possible to {@linkplain Builder#signed(int) <em>sign</em>} the minimal perfect hash function. A <var>w</var>-bit signature will
 * be associated with each key, so that {@link #getLong(Object)} will return -1 on strings that are not
 * in the original key set. As usual, false positives are possible with probability 2<sup>-<var>w</var></sup>.
 *
 * <h3>How it Works</h3>
 *
 * <p>The technique used is very similar (but developed independently) to that described by Fabiano
 * C. Botelho, Rasmus Pagh and Nivio Ziviani in &ldquo;Simple and Efficient Minimal Perfect Hashing
 * Functions&rdquo;, <i>Algorithms and data structures: 10th international workshop, WADS 2007</i>,
 * number 4619 of Lecture Notes in Computer Science, pages 139&minus;150, 2007. In turn, the mapping
 * technique described therein was actually first proposed by Bernard Chazelle, Joe Kilian, Ronitt
 * Rubinfeld and Ayellet Tal in &ldquo;The Bloomier Filter: an Efficient Data Structure for Static
 * Support Lookup Tables&rdquo;, <i>Proc. SODA 2004</i>, pages 30&minus;39, 2004, as one of the
 * steps to implement a mutable table.
 *
 * <p>The basic ingredient is the Majewski-Wormald-Havas-Czech
 * {@linkplain HypergraphSorter 3-hypergraph technique}. After generating a random 3-hypergraph, we
 * {@linkplain HypergraphSorter sort} its 3-hyperedges to that a distinguished vertex in each
 * 3-hyperedge, the <em>hinge</em>, never appeared before. We then assign to each vertex a
 * two-bit number in such a way that for each 3-hyperedge the sum of the values associated to its
 * vertices modulo 3 gives the index of the hash function generating the hinge. As as a result we
 * obtain a perfect hash of the original set (one just has to compute the three hash functions,
 * collect the three two-bit values, add them modulo 3 and take the corresponding hash function
 * value).
 *
 * <p>To obtain a minimal perfect hash, we simply notice that we whenever we have to assign a value
 * to a vertex, we can take care of using the number 3 instead of 0 if the vertex is actually the
 * output value for some key. The final value of the minimal perfect hash function is the number
 * of nonzero pairs of bits that precede the perfect hash value for the key. To compute this
 * number, we use a simple table-free ranking scheme, recording the number of nonzero pairs each
 * {@link #BITS_PER_BLOCK} bits and using {@link Long#bitCount(long)} to
 * {@linkplain #countNonzeroPairs(long) count the number of nonzero pairs of bits in a word}.
 *
 * @author Sebastiano Vigna
 * @since 0.1
 * @deprecated Use a {@link GOVMinimalPerfectHashFunction}.
 */

@Deprecated
public class MinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
	public static final long serialVersionUID = 6L;
	private static final Logger LOGGER = LoggerFactory.getLogger(MinimalPerfectHashFunction.class);
	private static final boolean ASSERTS = false;

	/** The length of a superblock. */
	private static final int WORDS_PER_SUPERBLOCK = 32;

	/**
	 * Counts the number of nonzero pairs of bits in a long.
	 *
	 * @param x a long.
	 * @return the number of nonzero bit pairs in <code>x</code>.
	 */

	public static int countNonzeroPairs(final long x) {
		return Long.bitCount((x | x >>> 1) & 0x5555555555555555L);
	}

	/** A builder class for {@link MinimalPerfectHashFunction}. */
	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected int signatureWidth;
		protected File tempDir;
		protected ChunkedHashStore<T> chunkedHashStore;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;

		/** Specifies the keys to hash; if you have specified a {@link #store(ChunkedHashStore) ChunkedHashStore}, it can be {@code null}.
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

		/** Specifies that the resulting {@link MinimalPerfectHashFunction} should be signed using a given number of bits per key.
		 *
		 * @param signatureWidth a signature width, or 0 for no signature.
		 * @return this builder.
		 */
		public Builder<T> signed(final int signatureWidth) {
			this.signatureWidth = signatureWidth;
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

		/** Specifies a chunked hash store containing the keys.
		 *
		 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
		 * can be unchecked, but in this case you must specify {@linkplain #keys(Iterable) keys} and a {@linkplain #transform(TransformationStrategy) transform}
		 * (otherwise, in case of a hash collision in the store an {@link IllegalStateException} will be thrown).
		 * @return this builder.
		 */
		public Builder<T> store(final ChunkedHashStore<T> chunkedHashStore) {
			this.chunkedHashStore = chunkedHashStore;
			return this;
		}

		/** Builds a minimal perfect hash function.
		 *
		 * @return a {@link MinimalPerfectHashFunction} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public MinimalPerfectHashFunction<T> build() throws IOException {
			if (built) throw new IllegalStateException("This builder has been already used");
			built = true;
			if (transform == null) {
				if (chunkedHashStore != null) transform = chunkedHashStore.transform();
				else throw new IllegalArgumentException("You must specify a TransformationStrategy, either explicitly or via a given ChunkedHashStore");
			}
			return new MinimalPerfectHashFunction<>(keys, transform, signatureWidth, tempDir, chunkedHashStore);
		}
	}

	/** The number of bits per block in the rank structure. */
	public static final int BITS_PER_BLOCK = 1024;

	/** The logarithm of the desired chunk size. */
	public final static int LOG2_CHUNK_SIZE = 10;

	/** The number of keys. */
	protected final long n;

	/** The shift for chunks. */
	private final int chunkShift;

	/** The seed used to generate the initial hash triple. */
	protected final long globalSeed;

	/** The seed of the underlying 3-hypergraphs. */
	protected final long[] seed;

	/** The start offset of each chunk. */
	protected final long[] offset;

	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal perfect hash function. */
	protected final LongBigList values;

	/** The bit vector underlying {@link #values}. */
	protected final LongArrayBitVector bitVector;

	/** The bit array supporting {@link #values}. */
	protected transient long[] array;

	/** The transformation strategy. */
	protected final TransformationStrategy<? super T> transform;

	/** The mask to compare signatures, or zero for no signatures. */
	protected final long signatureMask;

	/** The signatures. */
	protected final LongBigList signatures;

	/** The array of counts for blocks and superblocks. */
	protected final long[] count;

	/** This method implements a two-level ranking scheme. It is essentially a
	 * {@link Rank11} in which {@link Long#bitCount(long)} has been replaced
	 * by {@link #countNonzeroPairs(long)}.
	 *
	 * @param pos the position to rank in the 2-bit value array.
	 * @return the number of hinges before the specified position.
	 */
	private long rank(long pos) {
		pos *= 2;
		assert pos >= 0;
		assert pos <= bitVector.length();

		int word = (int)(pos / Long.SIZE);
		final int block = word / (WORDS_PER_SUPERBLOCK / 2) & ~1;
		final int offset = ((word % WORDS_PER_SUPERBLOCK) / 6) - 1;

		long result = count[block] + (count[block + 1] >> 12 * (offset + (offset >>> 32 - 4 & 6)) & 0x7FF) +
				countNonzeroPairs(array[word] & (1L << pos % Long.SIZE) - 1);

		for (int todo = (word & 0x1F) % 6; todo-- != 0;) result += countNonzeroPairs(array[--word]);
		return result;
	}



	/**
	 * Creates a new minimal perfect hash function for the given keys.
	 *
	 * @param keys the keys to hash, or {@code null}.
	 * @param transform a transformation strategy for the keys.
	 * @param signatureWidth a signature width, or 0 for no signature.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
	 * can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be non-{@code null}.
	 */
	@SuppressWarnings("resource")
	protected MinimalPerfectHashFunction(final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final int signatureWidth, final File tempDir, ChunkedHashStore<T> chunkedHashStore) throws IOException {
		this.transform = transform;

		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		final RandomGenerator r = new XoRoShiRo128PlusRandomGenerator();
		pl.itemsName = "keys";

		final boolean givenChunkedHashStore = chunkedHashStore != null;
		if (chunkedHashStore == null) {
			chunkedHashStore = new ChunkedHashStore<>(transform, tempDir, pl);
			chunkedHashStore.reset(r.nextLong());
			chunkedHashStore.addAll(keys.iterator());
		}
		n = chunkedHashStore.size();

		defRetValue = -1; // For the very few cases in which we can decide

		final int log2NumChunks = Math.max(0, Fast.mostSignificantBit(n >> LOG2_CHUNK_SIZE));
		chunkShift = chunkedHashStore.log2Chunks(log2NumChunks);
		final int numChunks = 1 << log2NumChunks;

		LOGGER.debug("Number of chunks: " + numChunks);

		seed = new long[numChunks];
		offset = new long[numChunks + 1];

		bitVector = LongArrayBitVector.getInstance();
		(values = bitVector.asLongBigList(2)).size(((long)Math.ceil(n * HypergraphSorter.GAMMA) + 4 * numChunks));
		array = bitVector.bits();

		int duplicates = 0;

		for (;;) {
			LOGGER.debug("Generating minimal perfect hash function...");

			long seed = 0;
			pl.expectedUpdates = numChunks;
			pl.itemsName = "chunks";
			pl.start("Analysing chunks... ");

			try {
				int q = 0;
				for (final ChunkedHashStore.Chunk chunk : chunkedHashStore) {
					final HypergraphSorter<BitVector> sorter = new HypergraphSorter<>(chunk.size(), false);
					do {
						seed = r.nextLong();
					} while (!sorter.generateAndSort(chunk.iterator(), seed));

					this.seed[q] = seed;
					offset[q + 1] = offset[q] + sorter.numVertices;

					/* We assign values. */
					int top = chunk.size(), k, v = 0;
					final int[] stack = sorter.stack;
					final int[] vertex1 = sorter.vertex1;
					final int[] vertex2 = sorter.vertex2;
					final long off = offset[q];

					while (top > 0) {
						v = stack[--top];
						k = (v > vertex1[v] ? 1 : 0) + (v > vertex2[v] ? 1 : 0);
						assert k >= 0 && k < 3 : Integer.toString(k);
						//System.err.println("<" + v + ", " + vertex1[v] + ", " + vertex2[v]+ "> (" + k + ")");
						final long s = values.getLong(off + vertex1[v]) + values.getLong(off + vertex2[v]);
						final long value = (k - s + 9) % 3;
						assert values.getLong(off + v) == 0;
						values.set(off + v, value == 0 ? 3 : value);
					}

					q++;
					pl.update();

					if (ASSERTS) {
						final IntOpenHashSet pos = new IntOpenHashSet();
						final int[] e = new int[3];
						for (final long[] triple : chunk) {
							HypergraphSorter.tripleToEdge(triple, seed, sorter.numVertices, sorter.partSize, e);
							assert pos.add(e[(int)(values.getLong(off + e[0]) + values.getLong(off + e[1]) + values.getLong(off + e[2])) % 3]);
						}
					}
				}

				pl.done();
				break;
			}
			catch (final ChunkedHashStore.DuplicateException e) {
				if (keys == null) throw new IllegalStateException("You provided no keys, but the chunked hash store was not checked");
				if (duplicates++ > 3) throw new IllegalArgumentException("The input list contains duplicates");
				LOGGER.warn("Found duplicate. Recomputing triples...");
				chunkedHashStore.reset(r.nextLong());
				chunkedHashStore.addAll(keys.iterator());
			}
		}

		globalSeed = chunkedHashStore.seed();

		if (n > 0) {
			final long m = values.size64();

			final long length = bitVector.length();

			final int numWords = (int)((length + Long.SIZE - 1) / Long.SIZE);

			final int numCounts = (int)((length + 32 * Long.SIZE - 1) / (32 * Long.SIZE)) * 2;
			// Init rank/select structure
			count = new long[numCounts + 1];

			long c = 0;
			int pos = 0;
			for(int i = 0; i < numWords; i += WORDS_PER_SUPERBLOCK, pos += 2) {
				count[pos] = c;

				for(int j = 0; j < WORDS_PER_SUPERBLOCK; j++) {
					if (j != 0 && j % 6 == 0) count[pos + 1] |= (i + j <= numWords ? c - count[pos] : 0x7FFL) << 12 * (j / 6 - 1);
					if (i + j < numWords) c += countNonzeroPairs(array[i + j]);
				}
			}

			count[numCounts] = c;

			if (ASSERTS) {
				int k = 0;
				for (long i = 0; i < m; i++) {
					assert rank(i) == k : "(" + i + ") " + k + " != " + rank(i);
					if (values.getLong(i) != 0) k++;
					assert k <= n;
				}

				if (keys != null) {
					final Iterator<? extends T> iterator = keys.iterator();
					for (long i = 0; i < n; i++)
						assert getLong(iterator.next()) < n;
				}
			}
		}
		else count = LongArrays.EMPTY_ARRAY;

		LOGGER.info("Completed.");
		LOGGER.debug("Forecast bit cost per key: " + (2 * HypergraphSorter.GAMMA + 2. * Long.SIZE / BITS_PER_BLOCK));
		LOGGER.info("Actual bit cost per key: " + (double)numBits() / n);


		if (signatureWidth != 0) {
			signatureMask = -1L >>> Long.SIZE - signatureWidth;
			(signatures = LongArrayBitVector.getInstance().asLongBigList(signatureWidth)).size(n);
			pl.expectedUpdates = n;
			pl.itemsName = "signatures";
			pl.start("Signing...");
			for (final ChunkedHashStore.Chunk chunk : chunkedHashStore) {
				final Iterator<long[]> iterator = chunk.iterator();
				for(int i = chunk.size(); i-- != 0;) {
					final long[] triple = iterator.next();
					final int[] e = new int[3];
					signatures.set(getLongByTripleNoCheck(triple, e), signatureMask & triple[0]);
					pl.lightUpdate();
				}
			}
			pl.done();
		}
		else {
			signatureMask = 0;
			signatures = null;
		}

		if (!givenChunkedHashStore) chunkedHashStore.close();
	}

	/**
	 * Returns the number of bits used by this structure.
	 *
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return values.size64() * 2 + count.length * Long.SIZE + offset.length * (long)Long.SIZE + seed.length * (long)Long.SIZE;
	}

	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object key) {
		if (n == 0) return defRetValue;
		final int[] e = new int[3];
		final long[] h = new long[3];
		Hashes.spooky4(transform.toBitVector((T)key), globalSeed, h);
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)(h[0] >>> chunkShift);
		final long chunkOffset = offset[chunk];
		HypergraphSorter.tripleToEdge(h, seed[chunk], (int)(offset[chunk + 1] - chunkOffset), e);
		if (e[0] == -1) return defRetValue;
		final long result = rank(chunkOffset + e[(int)(values.getLong(e[0] + chunkOffset) + values.getLong(e[1] + chunkOffset) + values.getLong(e[2] + chunkOffset)) % 3]);
		if (signatureMask != 0) return result >= n || ((signatures.getLong(result) ^ h[0]) & signatureMask) != 0 ? defRetValue : result;
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < n ? result : defRetValue;
	}

	/** Low-level access to the output of this minimal perfect hash function.
	 *
	 * <p>This method makes it possible to build several kind of functions on the same {@link ChunkedHashStore} and
	 * then retrieve the resulting values by generating a single triple of hashes. The method
	 * {@link TwoStepsMWHCFunction#getLong(Object)} is a good example of this technique.
	 *
	 * @param triple a triple generated as documented in {@link ChunkedHashStore}.
	 * @return the output of the function.
	 */
	public long getLongByTriple(final long[] triple) {
		if (n == 0) return defRetValue;
		final int[] e = new int[3];
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)(triple[0] >>> chunkShift);
		final long chunkOffset = offset[chunk];
		HypergraphSorter.tripleToEdge(triple, seed[chunk], (int)(offset[chunk + 1] - chunkOffset), e);
		if (e[0] == -1) return defRetValue;
		final long result = rank(chunkOffset + e[(int)(values.getLong(e[0] + chunkOffset) + values.getLong(e[1] + chunkOffset) + values.getLong(e[2] + chunkOffset)) % 3]);
		if (signatureMask != 0) return result >= n || signatures.getLong(result) != (triple[0] & signatureMask) ? defRetValue : result;
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < n ? result : defRetValue;
	}

	/** A dirty function replicating the behaviour of {@link #getLongByTriple(long[])} but skipping the
	 * signature test. Used in the constructor. <strong>Must</strong> be kept in sync with {@link #getLongByTriple(long[])}. */
	private long getLongByTripleNoCheck(final long[] triple, final int[] e) {
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)(triple[0] >>> chunkShift);
		final long chunkOffset = offset[chunk];
		HypergraphSorter.tripleToEdge(triple, seed[chunk], (int)(offset[chunk + 1] - chunkOffset), e);
		return rank(chunkOffset + e[(int)(values.getLong(e[0] + chunkOffset) + values.getLong(e[1] + chunkOffset) + values.getLong(e[2] + chunkOffset)) % 3]);
	}

	@Override
	public long size64() {
		return n;
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		array = bitVector.bits();
	}


	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(MinimalPerfectHashFunction.class.getName(), "Builds a minimal perfect hash function reading a newline-separated list of strings.", new Parameter[] {
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

		LOGGER.warn("This class is deprecated: please use GOVMinimalPerfectHashFunction");

		final String functionName = jsapResult.getString("function");
		final String stringFile = jsapResult.getString("stringFile");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final boolean byteArray = jsapResult.getBoolean("byteArray");
		final boolean zipped = jsapResult.getBoolean("zipped");
		final boolean iso = jsapResult.getBoolean("iso");
		final boolean utf32 = jsapResult.getBoolean("utf32");
		final int signatureWidth = jsapResult.getInt("signatureWidth", 0);

		if (byteArray) {
			if ("-".equals(stringFile)) throw new IllegalArgumentException("Cannot read from standard input when building byte-array functions");
			if (iso || utf32 || jsapResult.userSpecified("encoding")) throw new IllegalArgumentException("Encoding options are not available when building byte-array functions");
			final Collection<byte[]> collection= new FileLinesByteArrayCollection(stringFile, zipped);
			BinIO.storeObject(new MinimalPerfectHashFunction<>(collection, TransformationStrategies.rawByteArray(), signatureWidth, jsapResult.getFile("tempDir"), null), functionName);
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

			BinIO.storeObject(new MinimalPerfectHashFunction<CharSequence>(collection, transformationStrategy, signatureWidth, jsapResult.getFile("tempDir"), null), functionName);
		}
		LOGGER.info("Saved.");
	}
}
