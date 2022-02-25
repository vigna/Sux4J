/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2022 Sebastiano Vigna
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
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Iterator;
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
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.io.FileLinesByteArrayIterable;
import it.unimi.dsi.io.FileLinesMutableStringIterable;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.JacobsonBalancedParentheses;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;

/** A hollow trie, that is, a compacted trie recording just the length of the paths associated to the
 * internal nodes.
 *
 * <p>Instances of this class can be used to compute a monotone minimal perfect hashing of the keys. */

public class HollowTrieMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable, Size64 {
	private static final Logger LOGGER = LoggerFactory.getLogger(HollowTrieMonotoneMinimalPerfectHashFunction.class);
	private static final long serialVersionUID = 4L;

	private static final boolean ASSERTS = false;
	private static final boolean DEBUG = false;

	protected EliasFanoLongBigList skips;
	/** The bit vector containing Jacobson's representation of the trie. */
	protected final LongArrayBitVector trie;
	/** A balanced parentheses structure over {@link #trie}. */
	protected JacobsonBalancedParentheses balParen;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** The number of elements in this hollow trie. */
	private long size;

	private final static class Node {
		Node right;
		int skip;
		IntBigArrayBigList skips = new IntBigArrayBigList();
		LongArrayBitVector repr = LongArrayBitVector.getInstance();

		public Node(final Node right, final int skip) {
			this.right = right;
			this.skip = skip;
			if (ASSERTS) assert skip >= 0 : skip + " < " + 0;
		}
	}


	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object object) {
		// System.err.println("Hashing " + object + "...");
		if (size <= 1) return size - 1;
		final BitVector bitVector = transform.toBitVector((T)object).fast();
		long p = 1;
		final long length = bitVector.length();
		long index = 0;
		long s = 0, r = 0;

		for (;;) {
			if ((s += (int)skips.getLong(r)) >= length) return defRetValue;
			// System.err.println("Skipping " + rank9.rank(p) + " bits...");

			// System.err.print("Turning " + (bitVector.getBoolean(s) ? "right" : "left") +
			// " at bit " + s + "... \n");
			if (bitVector.getBoolean(s)) {
				final long q = balParen.findClose(p) + 1;
				r += (q - p) >> 1;
				index += (q - p) >> 1;
				// System.err.println("Increasing index by " + (q - p + 1) / 2 + " to " + index +
				// "...");
				if (!trie.getBoolean(q)) return index;
				p = q;
			}
			else {
				if (!trie.getBoolean(++p)) return index;
				r++;
			}

			s++;
		}
	}

	public HollowTrieMonotoneMinimalPerfectHashFunction(final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform) {
		this(iterable.iterator(), transform);
	}

	public HollowTrieMonotoneMinimalPerfectHashFunction(final Iterator<? extends T> iterator, final TransformationStrategy<? super T> transform) {

		this.transform = transform;
		defRetValue = -1; // For the very few cases in which we can decide

		long size = 0;
		long numNodes = 0;
		long maxLength = 0, totalLength = 0;

		Node root = null, node, parent;
		int prefix;

		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;

		pl.start("Generating hollow trie...");

		if (iterator.hasNext()) {
			LongArrayBitVector prev = LongArrayBitVector.copy(transform.toBitVector(iterator.next())), curr = LongArrayBitVector.getInstance(), temp;
			// The index of the last valid element on the stack
			int last = -1;
			// The stack of nodes forming the right border of the current trie
			final ObjectArrayList<Node> stack = new ObjectArrayList<>();
			// The length of the path compacted in the trie up to the corresponding node in stack,
			// included
			final IntArrayList len = new IntArrayList();
			if (DEBUG) System.err.println(prev);
			pl.lightUpdate();
			totalLength = maxLength = prev.length();

			size++;

			while (iterator.hasNext()) {
				size++;
				curr.replace(transform.toBitVector(iterator.next()));
				if (DEBUG) System.err.println(curr);
				pl.lightUpdate();
				if (maxLength < curr.length()) maxLength = curr.length();
				totalLength += curr.length();
				prefix = (int)curr.longestCommonPrefixLength(prev);
				if (prefix == prev.length() && prefix == curr.length()) throw new IllegalArgumentException("The input bit vectors are not distinct");
				if (prefix == prev.length() || prefix == curr.length()) throw new IllegalArgumentException("The input bit vectors are not prefix-free");
				if (prev.getBoolean(prefix)) throw new IllegalArgumentException("The input bit vectors are not lexicographically sorted");

				// TODO: might use a binary search
				while (last >= 0 && len.getInt(last) > prefix)
					last--;
				if (last >= 0) {
					parent = stack.get(last);
					node = parent.right;
					prefix -= len.getInt(last);
				}
				else {
					parent = null;
					node = root;
				}

				stack.size(last + 1);
				len.size(last + 1);

				Node newNode = null;
				if (node != null) {
					newNode = new Node(null, prefix);
					numNodes++;
					if (parent == null) {
						root.skip -= prefix + 1;
						if (ASSERTS) assert root.skip >= 0;


						root = newNode;
					}
					else {
						parent.right = newNode;
						node.skip -= prefix + 1;

						if (ASSERTS) assert node.skip >= 0;
					}

					Node n = node;
					long reprSize = 0;
					long skipSize = 0;
					do {
						reprSize += n.repr.length() + 2;
						skipSize += n.skips.size64() + 1;
					} while ((n = n.right) != null);

					n = node;
					newNode.repr.ensureCapacity(reprSize);
					newNode.skips.ensureCapacity(skipSize);

					do {
						newNode.repr.add(true);
						newNode.repr.append(n.repr);
						newNode.repr.add(false);
						newNode.skips.add(n.skip);
						newNode.skips.addAll(n.skips);
					} while ((n = n.right) != null);

					len.push((last < 0 ? 0 : len.getInt(last)) + 1 + newNode.skip);
					stack.push(newNode);
					last++;
				}
				else {
					newNode = new Node(null, prefix);
					if (parent == null) {
						root = newNode;
						len.push(root.skip + 1);
						stack.push(root);
					}
					else {
						parent.right = newNode;
						len.push(len.getInt(last) + 1 + newNode.skip);
						stack.push(newNode);
					}

					numNodes++;
					last++;
				}

				temp = prev;
				prev = curr;
				curr = temp;
			}
		}

		this.size = size;

		pl.done();

		if (size <= 1) {
			balParen = new JacobsonBalancedParentheses(BitVectors.EMPTY_VECTOR);
			trie = LongArrayBitVector.getInstance(0);
			return;
		}

		final LongArrayBitVector bitVector = LongArrayBitVector.getInstance(2 * numNodes + 2);

		bitVector.add(true); // Fake open parenthesis

		Node m = root;
		do {
			bitVector.add(true);
			bitVector.append(m.repr);
			bitVector.add(false);
		} while ((m = m.right) != null);

		bitVector.add(false);

		if (ASSERTS) assert bitVector.length() == 2 * numNodes + 2;

		LOGGER.debug("Generating succinct representations...");

		trie = bitVector;
		balParen = new JacobsonBalancedParentheses(bitVector, false, true, false);

		final Node finalRoot = root;

		final LongIterable skipIterable = () -> new LongIterator() {
			Node curr = finalRoot;
			long currElem = -1;

			@Override
			public long nextLong() {
				if (currElem == -1) {
					currElem++;
					return curr.skip;
				}
				else {
					if (currElem < curr.skips.size64()) return curr.skips.getInt(currElem++);
					curr = curr.right;
					currElem = 0;
					return curr.skip;
				}
			}

			@Override
			public boolean hasNext() {
				return curr != null && (currElem < curr.skips.size64() || currElem == curr.skips.size64() && curr.right != null);
			}
		};

		long maxSkip = 0, minSkip = Long.MAX_VALUE, s;
		for (final LongIterator i = skipIterable.iterator(); i.hasNext();) {
			s = i.nextLong();
			maxSkip = Math.max(s, maxSkip);
			minSkip = Math.min(s, minSkip);
		}

		final int skipWidth = Fast.ceilLog2(maxSkip);

		LOGGER.debug("Max skip: " + maxSkip);
		LOGGER.debug("Max skip width: " + skipWidth);

		skips = new EliasFanoLongBigList(skipIterable.iterator(), minSkip, true);

		if (DEBUG) {
			System.err.println(skips);
			System.err.println(this.skips);
		}

		final long numBits = numBits();
		LOGGER.debug("Bits: " + numBits);
		LOGGER.debug("Bits per open parenthesis: " + (double)balParen.numBits() / size);
		final double avgLength = (double)totalLength / size;

		// This is empirical--based on statistics about the average skip length in bits
		// final double avgSkipLength = (double)sumOfSkipLengths / skips.size();
		// LOGGER.info("Empirical bit cost per element: " + (4 + avgSkipLength + Fast.log2(
		// avgSkipLength)));
		// TODO: remove + 1
		LOGGER.info("Forecast bit cost per element: " + (4 + Fast.log2(avgLength) + 1 + Fast.log2(Fast.log2(avgLength + 1) + 1)));
		LOGGER.info("Actual bit cost per element: " + (double)numBits / size);
	}


	@Override
	public long size64() {
		return size;
	}

	public long numBits() {
		return balParen.numBits() + trie.length() + this.skips.numBits() + transform.numBits();
	}

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(HollowTrieMonotoneMinimalPerfectHashFunction.class.getName(), "Builds a monotone minimal perfect hash function based on a hollow trie reading a newline-separated list of strings.",
				new Parameter[] {
						new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
						new Switch("huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length."),
						new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
						new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
						new Switch("byteArray", 'b', "byte-array", "Create a function on byte arrays (no character encoding)."),
						new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
						new FlaggedOption("decompressor", JSAP.CLASS_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'd', "decompressor", "Use this extension of InputStream to decompress the strings (e.g., java.util.zip.GZIPInputStream)."),
						new UnflaggedOption("trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised hollow trie."),
						new UnflaggedOption("stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY,
								"The name of a file containing a newline-separated list of strings, or - for standard input; in the second case, strings must be fewer than 2^31 and will be loaded into core memory."),
				});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String trieName = jsapResult.getString("trie");
		final String stringFile = jsapResult.getString("stringFile");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final boolean zipped = jsapResult.getBoolean("zipped");
		Class<? extends InputStream> decompressor = jsapResult.getClass("decompressor");
		final boolean iso = jsapResult.getBoolean("iso");
		final boolean huTucker = jsapResult.getBoolean("huTucker");
		final boolean utf32 = jsapResult.getBoolean("utf32");
		final boolean byteArray = jsapResult.getBoolean("byteArray");

		if (zipped && decompressor != null) throw new IllegalArgumentException("The zipped and decompressor options are incompatible");
		if (zipped) decompressor = GZIPInputStream.class;

		if (byteArray) {
			if ("-".equals(stringFile)) throw new IllegalArgumentException("Cannot read from standard input when building byte-array functions");
			if (iso || utf32 || huTucker || jsapResult.userSpecified("encoding")) throw new IllegalArgumentException("Encoding options are not available when building byte-array functions");
			final Iterable<byte[]> keys = new FileLinesByteArrayIterable(stringFile, decompressor);
			BinIO.storeObject(new HollowTrieMonotoneMinimalPerfectHashFunction<>(keys, TransformationStrategies.prefixFreeByteArray()), trieName);
		} else {
			final Iterable<? extends CharSequence> keys;
			if ("-".equals(stringFile)) {
				final ObjectArrayList<String> list = new ObjectArrayList<>();
				keys = list;
				FileLinesMutableStringIterable.iterator(System.in, encoding, decompressor).forEachRemaining(s -> list.add(s.toString()));
			} else keys = new FileLinesMutableStringIterable(stringFile, encoding, decompressor);

			final TransformationStrategy<CharSequence> transformationStrategy = huTucker ? new HuTuckerTransformationStrategy(keys, true) : iso ? TransformationStrategies.prefixFreeIso() : utf32 ? TransformationStrategies.prefixFreeUtf32() : TransformationStrategies.prefixFreeUtf16();

			BinIO.storeObject(new HollowTrieMonotoneMinimalPerfectHashFunction<>(keys, transformationStrategy), trieName);
		}
		LOGGER.info("Completed.");
	}
}
