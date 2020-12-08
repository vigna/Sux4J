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

import static it.unimi.dsi.bits.LongArrayBitVector.words;

import java.io.IOException;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.NullOutputStream;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;

/** A version of a {@link PaCoTrieDistributor} whose space usage depends on the <em>average</em>
 * string length, rather than on the <em>maximum string length</em>; mainly of theoretical interest.
 *
 * @author Sebastiano Vigna
 */

public class VLPaCoTrieDistributor<T> extends AbstractObject2LongFunction<T> implements Size64 {
	private final static Logger LOGGER = LoggerFactory.getLogger(VLPaCoTrieDistributor.class);
	private static final long serialVersionUID = 2L;
	private static final boolean DEBUG = false;
	private static final boolean DDEBUG = false;
	/** Infinity-like value for initialising node prefixes. It's one less than {@link Integer#MAX_VALUE} because we need to be able to add one
	 * without overflowing. */
	private static final int MAX_PREFIX = Integer.MAX_VALUE - 1;
	/** The bitstream representing the PaCo trie. */
	private final byte[] trie;
	/** The number of leaves in the trie. */
	private final long numberOfLeaves;
	/** The transformation used to map object to bit vectors. */
	private final TransformationStrategy<? super T> transformationStrategy;
	public LongBigArrayBigList offset;

	/** A class representing explicitly a partial trie. The {@link PartialTrie#toStream(OutputBitStream)} method
	 * writes an instance of this class to a bit stream.
	 */
	private final static class PartialTrie<T> {
		private final static boolean ASSERTS = false;

		/** A node in the trie. */
		protected static class Node {
			/** Left child. */
			public Node left;
			/** Right child. */
			public Node right;
			/** The path compacted in this node ({@code null} if there is no compaction at this node). */
			public final LongArrayBitVector path;
			/** The length of the minimum disambiguating prefix on the left. */
			public int prefixLeft;
			/** The length of the minimum disambiguating prefix on the right. */
			public int prefixRight;

			/** Creates a node.
			 *
			 * @param left the left child.
			 * @param right the right child.
			 * @param path the path compacted at this node.
			 */
			public Node(final Node left, final Node right, final LongArrayBitVector path) {
				this.left = left;
				this.right = right;
				this.path = path;
				prefixLeft = prefixRight = MAX_PREFIX;
			}

			/** Returns true if this node is a leaf.
			 *
			 * @return true if this node is a leaf.
			 */
			public boolean isLeaf() {
				return right == null && left == null;
			}

			@Override
			public String toString() {
				return "[" + path + "]";
			}

		}

		/** The root of the trie. */
		protected final Node root;

		/** Leaves in the trie. */
		protected final long size;

		/** The offset of each delimiter. */
		protected final LongBigArrayBigList offset;

		/** Creates a partial compacted trie using given elements, bucket size and transformation strategy.
		 *
		 * @param elements the elements among which the trie must be able to rank.
		 * @param bucketSize the size of a bucket.
		 * @param transformationStrategy a transformation strategy that must turn the elements in <code>elements</code> into a list of
		 * distinct, lexicographically increasing (in iteration order) bit vectors.
		 * @param pl
		 */

		public PartialTrie(final Iterable<? extends T> elements, final long size, final int bucketSize, final TransformationStrategy<? super T> transformationStrategy, final ProgressLogger pl) {
			Iterator<? extends T> iterator = elements.iterator();

			Node node;
			final LongArrayBitVector curr = LongArrayBitVector.getInstance();
			int pos, prefix;

			if (iterator.hasNext()) {
				pl.start("Building trie...");
				final LongArrayBitVector prev = LongArrayBitVector.copy(transformationStrategy.toBitVector(iterator.next()));
				pl.lightUpdate();
				final LongArrayBitVector shortest = prev.copy();
				long shortestIndex = 0;
				// The last delimiter seen, if root is not null.
				final LongArrayBitVector prevDelimiter = LongArrayBitVector.getInstance();

				long count = 1;
				Node root = null;
				long maxLength = prev.length();
				// Last element will be unused
				offset = new LongBigArrayBigList(size / bucketSize + 1);

				while(iterator.hasNext()) {
					// Check order
					curr.replace(transformationStrategy.toBitVector(iterator.next()));
					pl.lightUpdate();
					prefix = (int)curr.longestCommonPrefixLength(prev);
					if (prefix == prev.length() && prefix == curr.length()) throw new IllegalArgumentException("The input bit vectors are not distinct");
					if (prefix == prev.length() || prefix == curr.length()) throw new IllegalArgumentException("The input bit vectors are not prefix-free");
					if (prev.getBoolean(prefix)) throw new IllegalArgumentException("The input bit vectors are not lexicographically sorted");

					if (count % bucketSize == 0) {
						// Found delimiter. Insert into trie.
						if (root == null) {
							root = new Node(null, null, shortest.copy());
							prevDelimiter.replace(shortest);
						}
						else {
							prefix = (int)shortest.longestCommonPrefixLength(prevDelimiter);

							pos = 0;
							node = root;
							Node n = null;
							while(node != null) {
								final long pathLength = node.path.length();
								if (prefix < pathLength) {
									n = new Node(node.left, node.right, node.path.copy(prefix + 1, pathLength));
									node.path.length(prefix);
									node.path.trim();
									node.left = n;
									node.right = new Node(null, null, shortest.copy(pos + prefix + 1, shortest.length()));
									break;
								}

								prefix -= pathLength + 1;
								pos += pathLength + 1;
								node = node.right;
								if (ASSERTS) assert node == null || prefix >= 0 : prefix + " <= " + 0;
							}

							if (ASSERTS) assert node != null;

							prevDelimiter.replace(shortest);
						}

						offset.add(shortestIndex);
						shortest.replace(curr);
						shortestIndex = count;
					}

					if (curr.length() < shortest.length()) {
						shortest.replace(curr);
						shortestIndex = count;
					}

					prev.replace(curr);
					maxLength = Math.max(maxLength, prev.length());
					count++;
				}

				pl.done();

				this.size = count;

				if (DEBUG) System.err.println("Offsets: " + offset);

				// There's no reason to set up an actual distributor if we can just use the offsets.
				if (this.size <= bucketSize * 2) root = null;

				this.root = root;

				if (root != null) {
					pl.start("Reducing paths...");

					iterator = elements.iterator();

					// The stack of nodes visited the last time
					final Node stack[] = new Node[(int)maxLength];
					// The length of the path compacted in the trie up to the corresponding node, excluded
					final int[] len = new int[(int)maxLength];
					stack[0] = root;
					int depth = 0;
					boolean first = true;

					while(iterator.hasNext()) {
						curr.replace(transformationStrategy.toBitVector(iterator.next()));
						pl.lightUpdate();
						if (! first)  {
							// Adjust stack using lcp between present string and previous one
							prefix = (int)prev.longestCommonPrefixLength(curr);
							while(depth > 0 && len[depth] > prefix) depth--;
						}
						else first = false;
						node = stack[depth];
						pos = len[depth];
						for(;;) {
							final LongArrayBitVector path = node.path;
							prefix = (int)curr.subVector(pos).longestCommonPrefixLength(path);
							if (prefix < path.length()) {
								/* If we are at the left of the current node, we simply update prefixLeft. Otherwise,
								 * can update prefixRight only *once*. */
								if (path.getBoolean(prefix)) node.prefixLeft = prefix;
								else if (node.prefixRight == MAX_PREFIX) node.prefixRight = prefix;
								break;
							}

							pos += path.length() + 1;
							if (pos > curr.length()) break;
							node = curr.getBoolean(pos - 1) ? node.right : node.left;
							// Update stack
							len[++depth] = pos;
							stack[depth] = node;
						}

						prev.replace(curr);
					}

					pl.done();
				}
			}
			else {
				this.root = null;
				this.size = 0;
				offset = null;
			}

		}

		/** Accumulates the gain in bits w.r.t. a standard trie (just for statistical purposes). */
		protected long gain;

		private final OutputBitStream bitCount = new OutputBitStream(NullOutputStream.getInstance(), 0);

		/** Writes this trie in bit stream format to the given stream.
		 *
		 * @param obs an output bit stream.
		 * @return the number of leaves in the trie.
		 */
		public long toStream(final OutputBitStream obs, final ProgressLogger pl) throws IOException {
			final long result = toStream(root, obs, pl);
			LOGGER.debug("Gain: " + gain);
			return result;
		}

		private long toStream(final Node n, final OutputBitStream obs, final ProgressLogger pl) throws IOException {
			if (n == null) return 0;

			if (ASSERTS) assert (n.left != null) == (n.right != null);

			// We recursively create the stream of the left and right trees
			final FastByteArrayOutputStream leftStream = new FastByteArrayOutputStream();
			final OutputBitStream left = new OutputBitStream(leftStream, 0);
			final long leavesLeft = toStream(n.left, left, pl);
			final long leftBits = left.writtenBits();
			left.flush();

			final FastByteArrayOutputStream rightStream = new FastByteArrayOutputStream();
			final OutputBitStream right = new OutputBitStream(rightStream, 0);
			final long leavesRight = toStream(n.right, right, pl);
			final long rightBits = right.writtenBits();
			right.flush();

			pl.lightUpdate();

			obs.writeLongDelta(n.isLeaf() ? 0 : leftBits); // Skip pointer (nonzero if non leaf)

			final int pathLength = (int)Math.min(n.path.length(), Math.max(n.prefixLeft, n.prefixRight) + 1);

			final int missing =  (int)(n.path.length() - pathLength);
			// We gain one bit for each missing bit
			gain += missing;

			/* For efficiency, the path is written in 64-bit blocks exactly as
			 * it is represented in a LongArrayBitVector. */

			gain += bitCount.writeLongDelta(n.path.length()) - obs.writeDelta(pathLength); // We gain if the path length is written in less bits than it should be.
			if (pathLength > 0) for(int i = 0; i < pathLength; i += Long.SIZE) obs.writeLong(n.path.getLong(i, Math.min(i + Long.SIZE, pathLength)), Math.min(Long.SIZE, pathLength - i));

			// Nothing after the path in leaves.
			if (n.isLeaf()) return 1;

			n.left = n.right = null;

			// We count the missing bit as a gain, but of course in an internal node we must subtract the space needed to represent their cardinality.
			gain -= obs.writeDelta(missing);

			obs.writeLongDelta(leavesLeft); // The number of leaves in the left subtree

			// Write left and right trees
			obs.write(leftStream.array, leftBits);
			obs.write(rightStream.array, rightBits);

			return leavesLeft + leavesRight;
		}

		private void recToString(final Node n, final MutableString printPrefix, final MutableString result, final MutableString path, final int level) {
			if (n == null) return;

			result.append(printPrefix).append('(').append(level).append(')');

			if (n.path != null) {
				path.append(n.path);
				result.append(" path:").append(n.path);
			}

			result.append('\n');

			path.append('0');
			recToString(n.left, printPrefix.append('\t').append("0 => "), result, path, level + 1);
			path.charAt(path.length() - 1, '1');
			recToString(n.right, printPrefix.replace(printPrefix.length() - 5, printPrefix.length(), "1 => "), result, path, level + 1);
			path.delete(path.length() - 1, path.length());
			printPrefix.delete(printPrefix.length() - 6, printPrefix.length());

			//System.err.println("Path now: " + path + " Going to delete from " + (path.length() - n.pathLength));

			path.delete((int)(path.length() - n.path.length()), path.length());
		}

		@Override
		public String toString() {
			final MutableString s = new MutableString();
			recToString(root, new MutableString(), s, new MutableString(), 0);
			return s.toString();
		}

	}

	/** Creates a partial compacted trie using given elements, bucket size and transformation strategy.
	 *
	 * @param elements the elements among which the trie must be able to rank.
	 * @param bucketSize the size of a bucket.
	 * @param transformationStrategy a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, lexicographically increasing (in iteration order) bit vectors.
	 */
	@SuppressWarnings("resource")
	public VLPaCoTrieDistributor(final Iterable<? extends T> elements, final long size, final int bucketSize, final TransformationStrategy<? super T> transformationStrategy) throws IOException {
		this.transformationStrategy = transformationStrategy;
		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		pl.itemsName = "keys";
		final PartialTrie<T> immutableBinaryTrie = new PartialTrie<>(elements, size, bucketSize, transformationStrategy, pl);
		final FastByteArrayOutputStream fbStream = new FastByteArrayOutputStream();
		final OutputBitStream trie = new OutputBitStream(fbStream, 0);
		pl.expectedUpdates = immutableBinaryTrie.size;
		pl.start("Converting to bitstream...");
		numberOfLeaves = immutableBinaryTrie.toStream(trie, pl);
		pl.done();
		offset = immutableBinaryTrie.offset;

		LOGGER.debug("Trie bit size: " + trie.writtenBits());

		trie.flush();
		fbStream.trim();
		this.trie = fbStream.array;

		if (DDEBUG) {
			final MutableString s = new MutableString();
			recToString(new InputBitStream(this.trie), new MutableString(), s, new MutableString(), 0);
			System.err.println(s);
		}
	}


	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object o) {
		if (numberOfLeaves == 0) return 0;
		try {
			if (DEBUG) System.err.println("Getting " + o + "...");
			final BitVector v = transformationStrategy.toBitVector((T)o).fast();
			final long length = v.length();
			@SuppressWarnings("resource")
			final InputBitStream trie = new InputBitStream(this.trie);

			long pos = 0, readBits, skip, xor, t;
			long leavesOnTheLeft = 0, leftSubtrieLeaves;
			int pathLength, size, missing;
			long leaves = numberOfLeaves;
			for(;;) {
				skip = trie.readLongDelta();
				pathLength = trie.readDelta();
				if (DEBUG) System.err.println("Path length: " + pathLength);

				xor = t = 0;

				readBits = trie.readBits();

				for (int i = 0, numWords = words(pathLength); i < numWords; i++) {
					size = Math.min(Long.SIZE, pathLength - i * Long.SIZE);
					xor = v.getLong(pos, Math.min(length, pos += size)) ^ (t = trie.readLong(size));
					if (xor != 0 || pos >= length) break;
				}

				if (xor != 0 || pos > length) {
					if (DEBUG) System.err.println("Path mismatch: " +  ((((xor & -xor) & t) != 0) ? "smaller" : "greater") + " than trie path at (leaf = " + leavesOnTheLeft + ")");
					// If we are lexicographically smaller than the trie, we just return the leaves to our left.
					if (((xor & -xor) & t) != 0) return leavesOnTheLeft;
					else {
						if (skip == 0) {
							if (DEBUG) System.err.println("Leaf node");
							return leavesOnTheLeft + 1;
						}
						if (DEBUG) System.err.println("Non-leaf node");
						// Skip remaining path, if any and missing bits count
						trie.skip(pathLength - (trie.readBits() - readBits));
						trie.readDelta();
						return leavesOnTheLeft + leaves;
					}
				}

				if (skip == 0) {
					if (DEBUG) System.err.println("Exact match (leaf = " + leavesOnTheLeft + ")" + pos + " " + length);
					return leavesOnTheLeft;
				}

				missing = trie.readDelta();
				if (DEBUG) System.err.println("Missing bits: " + missing);

				// Increment pos by missing bits
				pos += missing;
				if (pos >= v.length()) return leavesOnTheLeft;

				leftSubtrieLeaves = trie.readLongDelta();

				if (v.getBoolean(pos++)) {
					// Right
					trie.skip(skip);
					leavesOnTheLeft += leftSubtrieLeaves;
					leaves -= leftSubtrieLeaves;
					if (DEBUG) System.err.println("Turining right (" + leavesOnTheLeft + " leaves on the left)...");
				}
				else {
					// Left
					leaves = leftSubtrieLeaves;
					if (DEBUG) System.err.println("Turining left (" + leavesOnTheLeft + " leaves on the left)...");
				}

			}
		} catch(final IOException cantHappen) {
			throw new RuntimeException(cantHappen);
		}
	}


	private void recToString(final InputBitStream trie, final MutableString printPrefix, final MutableString result, final MutableString path, final int level) throws IOException {
		final long skip = trie.readLongDelta();

		//System.err.println("Called with prefix " + printPrefix);

		result.append(printPrefix).append('(').append(level).append(')');

		final int pathLength = trie.readDelta();
		final LongArrayBitVector p = LongArrayBitVector.getInstance(pathLength);

		for (int i = 0, numWords = words(pathLength); i < numWords; i++) {
			final int size = Math.min(Long.SIZE, pathLength - i * Long.SIZE);
			p.append(trie.readLong(size), size);
		}

		if (skip == 0) return; // Leaf

		int missing = trie.readDelta();

		path.append(p);
		result.append(" path:").append(p);
		while(missing-- != 0) result.append('*');

		result.append('\n');

		trie.readDelta(); // Skip number of leaves in the left subtree

		path.append('0');
		recToString(trie, printPrefix.append('\t').append("0 => "), result, path, level + 1);
		path.charAt(path.length() - 1, '1');
		recToString(trie, printPrefix.replace(printPrefix.length() - 5, printPrefix.length(), "1 => "), result, path, level + 1);
		path.delete(path.length() - 1, path.length());
		printPrefix.delete(printPrefix.length() - 6, printPrefix.length());

		//System.err.println("Path now: " + path + " Going to delete from " + (path.length() - n.pathLength));

		path.delete(path.length() - pathLength, path.length());
	}

	public long numBits() {
		return trie.length * (long)Byte.SIZE + transformationStrategy.numBits();
	}

	@Override
	public boolean containsKey(final Object o) {
		return true;
	}

	@Override
	public long size64() {
		return numberOfLeaves;
	}

	@Override
	@Deprecated
	public int size() {
		return (int)Math.min(numberOfLeaves, Integer.MAX_VALUE);
	}
}
